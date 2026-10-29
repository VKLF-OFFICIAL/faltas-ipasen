import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cifrar } from '../src/cifrar.js';

test('cifrar matches Séneca\'s own cifrado.js', () => {
  assert.equal(cifrar('a'), '36935082127850398771332827297275432777651');
  assert.equal(cifrar('Clave123'), '94400580850451503091787961074190598717597');
  assert.equal(cifrar('contraseña!'), '161722072334323363148875172725394659918452');
});
