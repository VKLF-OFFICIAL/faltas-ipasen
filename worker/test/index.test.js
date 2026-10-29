import { test } from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';

const env = { SYNC_LIMIT: { limit: async () => ({ success: true }) } };
const req = (method, body, origin = 'https://vklf-official.github.io') =>
  new Request('https://w.example/sync', { method, headers: { origin, 'content-type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });

test('preflight allows only the Pages origin', async () => {
  const ok = await worker.fetch(req('OPTIONS'), env);
  assert.equal(ok.headers.get('access-control-allow-origin'), 'https://vklf-official.github.io');
  const bad = await worker.fetch(req('OPTIONS', null, 'https://evil.example'), env);
  assert.equal(bad.status, 403);
});

test('missing credentials is BAD_REQUEST', async () => {
  const res = await worker.fetch(req('POST', { usuario: '' }), env);
  assert.equal(res.status, 400);
  assert.equal((await res.json()).code, 'BAD_REQUEST');
});

test('rate limited requests are refused', async () => {
  const res = await worker.fetch(req('POST', { usuario: 'a', clave: 'b' }), { SYNC_LIMIT: { limit: async () => ({ success: false }) } });
  assert.equal(res.status, 429);
  assert.equal((await res.json()).code, 'RATE_LIMITED');
});

test('per-user limit refuses with 429 and is keyed by lower-cased username', async () => {
  const keys = [];
  const e = { USER_LIMIT: { limit: async ({ key }) => { keys.push(key); return { success: false }; } } };
  const res = await worker.fetch(req('POST', { usuario: '  AbC ', clave: 'b' }), e);
  assert.equal(res.status, 429);
  assert.equal((await res.json()).code, 'RATE_LIMITED');
  assert.deepEqual(keys, ['abc']);
});

test('a JSON null body is BAD_REQUEST with CORS, not a crash', async () => {
  const r = new Request('https://w.example/sync', { method: 'POST', headers: { origin: 'https://vklf-official.github.io' }, body: 'null' });
  const res = await worker.fetch(r, env);
  assert.equal(res.status, 400);
  assert.equal(res.headers.get('access-control-allow-origin'), 'https://vklf-official.github.io');
});

test('env.clientIp keys the IP limiter when cf-connecting-ip is absent', async () => {
  const keys = [];
  const e = { clientIp: '1.2.3.4', SYNC_LIMIT: { limit: async ({ key }) => { keys.push(key); return { success: true }; } } };
  await worker.fetch(req('POST', { usuario: '' }), e);
  assert.deepEqual(keys, ['1.2.3.4']);
});
