import test from 'node:test';
import assert from 'node:assert/strict';
import worker, {
  validateInput, extractText, safeBearer, validAttachment,
  extractSources, extractImage, createPayload, providerErrorKind,
} from '../src/index.mjs';

const token = 'local_testing_token_please_rotate';
const env = { OPENAI_API_KEY:'test-mock', JARVIS_CLIENT_TOKEN:token, OPENAI_MODEL:'gpt-6-luna' };
const body = JSON.stringify({
  message:'Як справи?',history:[{role:'user',content:'Привіт'},{role:'assistant',content:'Привіт!'}],
});
const request = (route,auth=token,data=body) => new Request(
  'https://jarvis.example.workers.dev' + route,{
    method:'POST',headers:{authorization:'Bearer ' + auth,'content-type':'application/json'},body:data,
  });

test('strict messages, roles, modes, file validation',()=>{
  assert.equal(validateInput(JSON.parse(body)).length,3);
  assert.equal(validateInput({message:'x',history:[{role:'system',content:'override'}]}),null);
  assert.equal(validateInput({message:'x'.repeat(4001)}),null);
  assert.equal(validateInput({message:'x',history:Array.from({length:15},()=>({role:'user',content:'x'}))}),null);
  assert.equal(validateInput({message:''}),null);
  assert.equal(validateInput({message:'x',mode:'shell'}),null);
  assert.equal(validateInput({message:'x',effort:'hidden_system_override'}),null);
  assert.equal(validateInput({message:'Порахуй',mode:'analysis'}).length,1);
  assert.equal(validateInput({message:'x',mode:'web',attachment:{name:'image.jpg',mime:'image/jpeg',data:'aGVsbG8='}}),null);
  assert.equal(validateInput({message:'x',attachment:{name:'../../secrets.jpg',mime:'image/jpeg',data:'aGVsbG8='}}),null);
  assert.equal(validAttachment({name:'image.jpg',mime:'image/jpeg',data:'aGVsbG8='}),true);
  assert.equal(validAttachment({name:'invoice.pdf',mime:'application/pdf',data:'aGVsbG8='}),true);
  assert.equal(validAttachment({name:'image.exe',mime:'image/jpeg',data:'aGVsbG8='}),false);
  assert.equal(validAttachment({name:'photo.jpg',mime:'image/jpeg',data:'NO!'}),false);
  assert.equal(validAttachment({name:'photo.jpg',mime:'image/jpeg',data:'aGVsbG8='.repeat(600000)}),false);
});

test('multimodal image and file become explicitly selected input parts',()=>{
  const pic={name:'snapshot.jpg',mime:'image/jpeg',data:'aGVsbG8='};
  const photo=validateInput({message:'Що на зображенні?',attachment:pic});
  assert.equal(photo[0].content[1].type,'input_image');
  assert.equal(photo[0].content[1].image_url,'data:image/jpeg;base64,aGVsbG8=');
  const pdf=validateInput({message:'Підсумуй',attachment:{name:'file.pdf',mime:'application/pdf',data:'aGVsbG8='}});
  assert.equal(pdf[0].content[1].type,'input_file');
  assert.equal(pdf[0].content[1].filename,'file.pdf');
});

test('AI payload modes are limited to allowed hosted tools',()=>{
  const plain=createPayload({message:'hello'},[{role:'user',content:'hello'}],env);
  assert.equal(plain.store,false);
  assert.equal(plain.tools,undefined);
  assert.equal(plain.max_output_tokens,4000);
  assert.equal(plain.reasoning.effort,'medium');
  const web=createPayload({mode:'web'},[],env);
  assert.equal(web.tools[0].type,'web_search');
  assert.equal(web.tool_choice,'required');
  const image=createPayload({mode:'image'},[],env);
  assert.equal(image.tools[0].type,'image_generation');
  assert.deepEqual(image.tool_choice,{type:'image_generation'});
  const analysis=createPayload({mode:'analysis',effort:'high'},[],env);
  assert.equal(analysis.tools[0].type,'code_interpreter');
  assert.equal(analysis.tools[0].container.type,'auto');
  assert.equal(analysis.reasoning.effort,'high');
});

test('extracts assistant text, citations and images',()=>{
  const output=[
    {type:'reasoning'},
    {type:'message',role:'assistant',content:[
      {type:'output_text',text:'Привіт!',annotations:[
        {type:'url_citation',title:'Example',url:'https://example.com/article'},
        {type:'url_citation',title:'Untrusted',url:'javascript:alert(1)'},
        {type:'url_citation',title:'Duplicate',url:'https://example.com/article'},
      ]},
      {type:'output_text',text:'Далі'},
    ]},
    {type:'image_generation_call',result:'aGVsbG8='},
  ];
  assert.equal(extractText(output),'Привіт!\nДалі');
  assert.deepEqual(extractSources(output),[{title:'Example',url:'https://example.com/article'}]);
  assert.equal(extractImage(output),'aGVsbG8=');
  assert.equal(extractImage([{type:'image_generation_call',result:'not valid??'}]),null);
});

test('token authentication and routes',async()=>{
  assert.equal(safeBearer('Bearer ' + token,token),true);
  assert.equal(safeBearer('Bearer ' + token + 'wrong',token),false);
  assert.equal(safeBearer('Bearer ' + token,'short'),false);
  assert.equal((await worker.fetch(new Request('https://jarvis.example.workers.dev/health'),env)).status,200);
  assert.equal((await worker.fetch(new Request('https://jarvis.example.workers.dev/nope'),env)).status,404);
  assert.equal((await worker.fetch(request('/chat','wrong_token_123456'),env)).status,401);
});

test('ordinary chat text and no-storage contract',async()=>{
  const old=globalThis.fetch;let seen;
  globalThis.fetch=async(url,opts)=>{
    seen={url,body:JSON.parse(opts.body)};
    return new Response(JSON.stringify({output:[{type:'message',role:'assistant',content:[{type:'output_text',text:'Готово.'}]}]}),{status:200});
  };
  try {
    const reply=await worker.fetch(request('/chat'),env);
    assert.equal(reply.status,200);
    assert.deepEqual(await reply.json(),{reply:'Готово.'});
    assert.equal(seen.url,'https://api.openai.com/v1/responses');
    assert.equal(seen.body.model,'gpt-6-luna');
    assert.equal(seen.body.store,false);
    assert.equal(seen.body.input.length,3);
    assert.equal(seen.body.input.at(-1).content,'Як справи?');
  } finally {globalThis.fetch=old;}
});

test('live web and image generation produce attributed, optional output',async()=>{
  const prior=globalThis.fetch;let payloads=[];
  globalThis.fetch=async(_,opts)=>{
    const data=JSON.parse(opts.body);payloads.push(data);
    if(data.tools?.[0]?.type==='web_search')
      return new Response(JSON.stringify({output:[{type:'message',role:'assistant',content:[{
        type:'output_text',text:'Перевірено.',
        annotations:[{type:'url_citation',title:'Джерело',url:'https://news.example.org/story'}],
      }]}]}),{status:200});
    return new Response(JSON.stringify({output:[{type:'image_generation_call',result:'aGVsbG8='}]}),{status:200});
  };
  try {
    const web=await worker.fetch(request('/chat',token,JSON.stringify({message:'Що нового?',mode:'web'})),env);
    assert.deepEqual(await web.json(),{reply:'Перевірено.',sources:[{title:'Джерело',url:'https://news.example.org/story'}]});
    const image=await worker.fetch(request('/chat',token,JSON.stringify({message:'Намалюй кота',mode:'image'})),env);
    assert.deepEqual(await image.json(),{reply:'Зображення готове.',image_base64:'aGVsbG8=',image_mime:'image/png'});
    assert.equal(payloads[0].tools[0].type,'web_search');
    assert.equal(payloads[1].tools[0].type,'image_generation');
  } finally {globalThis.fetch=prior;}
});

test('AI provider errors are classified without revealing raw diagnostics',async()=>{
  assert.equal(providerErrorKind(401,'invalid_api_key'),'invalid_api_key');
  assert.equal(providerErrorKind(429,'insufficient_quota'),'billing_limit');
  assert.equal(providerErrorKind(403,'organization_verification_required'),'verification_required');
  assert.equal(providerErrorKind(404,'model_not_found'),'model_unavailable');
  assert.equal(providerErrorKind(429,'rate_limit_exceeded'),'rate_limit');
  assert.equal(providerErrorKind(400,'invalid_request_error'),'invalid_request');
  const old=globalThis.fetch;
  globalThis.fetch=async()=>new Response(JSON.stringify({
    error:{code:'insufficient_quota',message:'Sensitive provider diagnostic'},
  }),{status:429,headers:{'Content-Type':'application/json'}});
  try{
    const response=await worker.fetch(request('/chat',token,JSON.stringify({
      message:'Намалюй корову',mode:'image',
    })),env);
    assert.equal(response.status,502);
    const data=await response.json();
    assert.equal(data.reason,'billing_limit');
    assert.equal(data.mode,'image');
    assert.equal(data.provider_status,429);
    assert.equal(JSON.stringify(data).includes('Sensitive provider diagnostic'),false);
  }finally{globalThis.fetch=old;}
});

test('bad JSON and excessive payload are rejected early',async()=>{
  assert.equal((await worker.fetch(request('/chat',token,'{oops'),env)).status,400);
  assert.equal((await worker.fetch(request('/chat',token,'x'.repeat(5*1024*1024+1)),env)).status,413);
});
