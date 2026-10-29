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
