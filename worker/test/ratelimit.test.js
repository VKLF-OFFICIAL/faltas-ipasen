import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createLimiter } from '../src/ratelimit.js';

test('limiter allows up to the limit per key and resets after the window', async () => {
  let t = 0;
  const l = createLimiter({ limit: 2, periodMs: 1000, now: () => t });
  assert.equal((await l.limit({ key: 'a' })).success, true);
  assert.equal((await l.limit({ key: 'a' })).success, true);
  assert.equal((await l.limit({ key: 'a' })).success, false);
  assert.equal((await l.limit({ key: 'b' })).success, true);
  t = 999;
  assert.equal((await l.limit({ key: 'a' })).success, false);
  t = 1000;
  assert.equal((await l.limit({ key: 'a' })).success, true);
});
