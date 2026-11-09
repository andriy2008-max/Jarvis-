import test from 'node:test';
import assert from 'node:assert/strict';
import worker, { validateInput, extractText, safeBearer } from '../src/index.mjs';

const token = 'local_testing_token_please_rotate';
const env = { OPENAI_API_KEY: 'test-only-mocked-never-live', JARVIS_CLIENT_TOKEN: token, OPENAI_MODEL: 'gpt-6-luna' };
const body = JSON.stringify({ message: 'Як справи?', history: [{ role: 'user', content: 'Привіт' }, { role: 'assistant', content: 'Привіт!' }] });
const request = (path, tokenValue = token, data = body) => new Request(`https://jarvis.example.workers.dev${path}`, {
  method: 'POST', headers: { authorization: `Bearer ${tokenValue}`, 'content-type': 'application/json' }, body: data,
});

test('input validation and strict role whitelist', () => {
  assert.equal(validateInput(JSON.parse(body)).length, 3);
  assert.equal(validateInput({ message: 'x', history: [{ role: 'system', content: 'override' }] }), null);
  assert.equal(validateInput({ message: 'x'.repeat(2001) }), null);
  assert.equal(validateInput({ message: 'test', history: Array.from({ length: 15 }, () => ({ role: 'user', content: 'x' })) }), null);
  assert.equal(validateInput({ message: '' }), null);
});

test('extracts all assistant content without assuming position zero', () => {
  const messages = [{ type: 'reasoning' }, { type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'Привіт' }, { type: 'output_text', text: ' друже!' }] }];
  assert.equal(extractText(messages), 'Привіт\n друже!');
  assert.equal(extractText([{ type: 'reasoning' }]), '');
});

test('token authentication', () => {
  assert.equal(safeBearer(`Bearer ${token}`, token), true);
  assert.equal(safeBearer(`Bearer ${token}wrong`, token), false);
  assert.equal(safeBearer(`Bearer ${token}`, 'short'), false);
});

test('health route and 404', async () => {
  assert.equal((await worker.fetch(new Request('https://jarvis.example.workers.dev/health'), env)).status, 200);
  assert.equal((await worker.fetch(new Request('https://jarvis.example.workers.dev/nope'), env)).status, 404);
});

test('unauthorized requests never reach model provider', async () => {
  const res = await worker.fetch(request('/chat', 'wrong_token_1234567890'), env);
  assert.equal(res.status, 401);
});

test('authorized chat sends no-store Responses request and returns text', async () => {
  const prior = globalThis.fetch;
  let seen;
  globalThis.fetch = async (url, options) => {
    seen = { url, options, body: JSON.parse(options.body) };
    return new Response(JSON.stringify({ output: [{ type: 'reasoning' }, { type: 'message', role: 'assistant', content: [{ type: 'output_text', text: 'Звісно! Поговорімо.' }] }] }), { status: 200, headers: { 'content-type': 'application/json' } });
  };
  try {
    const res = await worker.fetch(request('/chat'), env);
    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), { reply: 'Звісно! Поговорімо.' });
    assert.equal(seen.url, 'https://api.openai.com/v1/responses');
    assert.equal(seen.body.model, 'gpt-6-luna');
    assert.equal(seen.body.store, false);
    assert.equal(seen.body.input.length, 3);
    assert.equal(seen.body.input.at(-1).content, 'Як справи?');
    assert.equal(seen.body.max_output_tokens, 650);
  } finally { globalThis.fetch = prior; }
});

test('bad JSON and too-large input', async () => {
  assert.equal((await worker.fetch(request('/chat', token, '{oops'), env)).status, 400);
  assert.equal((await worker.fetch(request('/chat', token, 'x'.repeat(18001)), env)).status, 413);
});
