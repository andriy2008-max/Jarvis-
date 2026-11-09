/**
 * JARVIS personal AI proxy, Cloudflare Workers.
 * OPENAI_API_KEY stays in the Worker secret store and NEVER ships to the Android APK.
 * JARVIS_CLIENT_TOKEN is a separate revocable access code entered on the user's device.
 */

const MAX_REQUEST_BYTES = 18000;
const MAX_MESSAGE_CHARS = 2000;
const MAX_HISTORY_TURNS = 14;
const DEFAULT_MODEL = 'gpt-6-luna';
const INSTRUCTIONS = `Ти Джарвіс — дружній, уважний персональний голосовий помічник.
Відповідай українською, природно і стисло, переважно 1–4 речення, якщо не просять деталі.
Підтримуй звичайну розмову та враховуй попередній контекст.
Не стверджуй, що виконав дію на телефоні: команди пристрою обробляються локально Android-застосунком.
Якщо не знаєш або немає актуальних даних — поясни обмеження, не вигадуй.`;

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' },
  });
}

function safeBearer(value, secret) {
  if (typeof secret !== 'string' || secret.length < 20) return false;
  const expected = `Bearer ${secret}`;
  if (typeof value !== 'string' || value.length !== expected.length) return false;
  let mismatch = 0;
  for (let i = 0; i < expected.length; i++) mismatch |= value.charCodeAt(i) ^ expected.charCodeAt(i);
  return mismatch === 0;
}

function validateInput(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) return null;
  const message = typeof body.message === 'string' ? body.message.trim() : '';
  if (!message || message.length > MAX_MESSAGE_CHARS) return null;
  const history = body.history == null ? [] : body.history;
  if (!Array.isArray(history) || history.length > MAX_HISTORY_TURNS) return null;
  const result = [];
  for (const entry of history) {
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) return null;
    if (entry.role !== 'user' && entry.role !== 'assistant') return null;
    if (typeof entry.content !== 'string' || !entry.content.trim() || entry.content.length > 6000) return null;
    result.push({ role: entry.role, content: entry.content });
  }
  result.push({ role: 'user', content: message });
  return result;
}

function extractText(output) {
  if (!Array.isArray(output)) return '';
  const parts = [];
  for (const item of output) {
    if (item?.type !== 'message' || item?.role !== 'assistant' || !Array.isArray(item.content)) continue;
    for (const part of item.content) {
      if (part?.type === 'output_text' && typeof part.text === 'string') parts.push(part.text);
    }
  }
  return parts.join('\n').trim();
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/health' && request.method === 'GET') return json({ status: 'ok' });
    if (url.pathname !== '/chat' || request.method !== 'POST') return json({ error: 'Not found' }, 404);
    if (!env.OPENAI_API_KEY || !env.JARVIS_CLIENT_TOKEN) return json({ error: 'Server is not configured' }, 503);
    if (!safeBearer(request.headers.get('authorization'), env.JARVIS_CLIENT_TOKEN)) {
      return json({ error: 'Unauthorized' }, 401);
    }
    const length = Number(request.headers.get('content-length'));
    if (Number.isFinite(length) && length > MAX_REQUEST_BYTES) return json({ error: 'Request too large' }, 413);
    let text;
    try { text = await request.text(); } catch { return json({ error: 'Invalid request' }, 400); }
    if (text.length > MAX_REQUEST_BYTES) return json({ error: 'Request too large' }, 413);
    let parsed;
    try { parsed = JSON.parse(text); } catch { return json({ error: 'Invalid JSON' }, 400); }
    const input = validateInput(parsed);
    if (!input) return json({ error: 'Invalid message/history' }, 400);

    let upstream;
    try {
      upstream = await fetch('https://api.openai.com/v1/responses', {
        method: 'POST',
        headers: {
          'authorization': `Bearer ${env.OPENAI_API_KEY}`,
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          model: env.OPENAI_MODEL || DEFAULT_MODEL,
          instructions: INSTRUCTIONS,
          input,
          store: false,
          reasoning: { effort: 'none' },
          max_output_tokens: 650,
        }),
      });
    } catch {
      return json({ error: 'Model provider unavailable' }, 502);
    }
    if (!upstream.ok) {
      if (upstream.status === 429) return json({ error: 'AI provider rate limit reached' }, 429);
      return json({ error: 'AI provider error' }, 502);
    }
    let data;
    try { data = await upstream.json(); } catch { return json({ error: 'Invalid AI response' }, 502); }
    const reply = extractText(data?.output);
    if (!reply) return json({ error: 'Empty AI response' }, 502);
    return json({ reply: reply.slice(0, 6000) });
  },
};

export { validateInput, extractText, safeBearer };
