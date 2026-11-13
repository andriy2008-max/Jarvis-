import test from 'node:test';
import assert from 'node:assert/strict';
import worker, {createPayload, validateInput} from '../src/index.mjs';
import {functionTools, readDecision, validDecision} from '../src/tools.mjs';

const decision = (tool='volume_set', args={percent:30}, confidence=0.95) => ({intent:'change volume', tool, args, confidence});
const response = d => ({status:'completed',output:[{type:'function_call',status:'completed',name:d.tool,
  arguments:JSON.stringify({intent:d.intent,args:d.args,confidence:d.confidence})}]});

test('allowlist rejects arbitrary execution, forged approval, types and limits', () => {
  assert.equal(validDecision(decision()), true);
  for (const d of [decision('shell',{command:'id'}), decision('volume_set',{percent:'30'}),
    decision('volume_set',{percent:101}), decision('volume_set',{percent:1.5}),
    decision('volume_set',{percent:30,approved:true}), {...decision(),approved:true},
    decision('torch_set',{enabled:'true'}), decision('timer_start',{seconds:10801}),
    decision('music_control',{action:'delete'}), decision('timer_cancel',{seconds:1}),
    decision('volume_set',{percent:30},NaN), {...decision(),intent:''}]) {
    assert.equal(validDecision(d), false, JSON.stringify(d));
  }
  assert.equal(validDecision(decision('timer_cancel',{})),true);
  assert.equal(validDecision(decision('torch_set',{enabled:false})),true);
});

test('function schemas require exact parameters and provide no shell or send tools', () => {
  for (const tool of functionTools()) {
    assert.equal(tool.strict, true);
    assert.equal(tool.parameters.additionalProperties, false);
    assert.equal(tool.parameters.properties.args.additionalProperties, false);
    assert.deepEqual(tool.parameters.properties.args.required,Object.keys(tool.parameters.properties.args.properties));
  }
  assert.equal(functionTools().some(t=>/shell|root|send|delete/.test(t.name)),false);
});

test('partial/multiple/unknown tool calls fail closed; low confidence clarifies', () => {
  assert.deepEqual(readDecision(response(decision())), decision());
  assert.equal(readDecision(response(decision('volume_set',{percent:20},0.84))).tool,'clarify');
  assert.throws(()=>readDecision({...response(decision()),status:'incomplete'}));
  assert.throws(()=>readDecision({output:[...response(decision()).output,...response(decision()).output]}));
  assert.throws(()=>readDecision(response(decision('root',{command:'id'}))));
  assert.throws(()=>readDecision({output:[{type:'function_call',name:'volume_set',arguments:'{'}]}));
  assert.throws(()=>readDecision({output:[{type:'function_call',status:'in_progress',name:'volume_set',arguments:'{}'}]}));
});

test('assist is explicit, versioned, text-only and uses one function at a time', () => {
  assert.equal(validateInput({message:'x',mode:'assist'}),null);
  assert.equal(validateInput({message:'x',mode:'assist',tool_version:2}),null);
  assert.equal(validateInput({message:'x',mode:'assist',tool_version:1,device_context:{approved:true}}),null);
  assert.equal(validateInput({message:'x',mode:'assist',tool_version:1,attachment:{}}),null);
  const body={message:'тихіше',mode:'assist',tool_version:1,device_context:decision()};
  const payload=createPayload(body,validateInput(body),{});
  assert.equal(payload.parallel_tool_calls,false);
  assert.equal(payload.tool_choice,'auto');
  assert.equal(payload.store,false);
  assert.equal(payload.tools.length,8);
  assert.equal(payload.input.at(-1).content,'тихіше');
  assert.equal(createPayload({mode:'chat'},[],{}).tools,undefined);
});

test('HTTP assist validates provider response and never calls a phone executor', async () => {
  const saved=globalThis.fetch;
  const token='test_tool_client_token_long';
  const env={OPENAI_API_KEY:'mock',JARVIS_CLIENT_TOKEN:token};
  const request=()=>new Request('https://worker.test/chat',{method:'POST',headers:{authorization:'Bearer '+token},
    body:JSON.stringify({message:'трохи тихіше',mode:'assist',tool_version:1})});
  let output=response(decision('volume_adjust',{delta:-10})), count=0;
  globalThis.fetch=async (url, opts)=>{
    count++; assert.equal(url,'https://api.openai.com/v1/responses');
    assert.ok(opts.signal); return new Response(JSON.stringify(output));
  };
  try {
    let result=await worker.fetch(request(),env);
    assert.equal(result.status,200);
    assert.deepEqual((await result.json()).decision,decision('volume_adjust',{delta:-10}));
    assert.equal(count,1);
    output=response(decision('volume_set',{percent:'50'}));
    result=await worker.fetch(request(),env);
    assert.equal(result.status,502);
    assert.equal((await result.json()).reason,'invalid_tool_proposal');
    output={status:'completed',output:[{type:'message',role:'assistant',content:[{type:'output_text',text:'Привіт!'}]}]};
    assert.deepEqual(await (await worker.fetch(request(),env)).json(),{reply:'Привіт!',tool_version:1,decision:null});
  } finally { globalThis.fetch=saved; }
});
