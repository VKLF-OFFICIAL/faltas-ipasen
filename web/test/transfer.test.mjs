import { test } from 'node:test';
import assert from 'node:assert/strict';
import { pack, unpack } from '../lib/transfer.mjs';

test('ida y vuelta con tildes y ñ', async () => {
  const obj = { v: 1, schedule: [{ nombre: 'Diseño de interfaces web, niño' }], calendar: [], absences: { absences: [], skipped: [] } };
  const s = await pack(obj);
  assert.match(s, /^[A-Za-z0-9_-]+$/);
  assert.deepEqual(await unpack(s), obj);
});

test('datos grandes', async () => {
  const obj = { a: Array.from({ length: 20000 }, (_, i) => ({ i, t: 'Módulo ñ' })) };
  assert.deepEqual(await unpack(await pack(obj)), obj);
});

test('entrada corrupta falla', async () => {
  await assert.rejects(unpack('esto no es base64 !!'));
  await assert.rejects(unpack('AAAA'));
  const s = await pack({ v: 1 });
  await assert.rejects(unpack(s.slice(0, s.length - 8)));
});
