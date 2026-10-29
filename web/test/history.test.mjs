import { test } from 'node:test';
import assert from 'node:assert/strict';
import { entradasDe } from '../lib/history.mjs';

const entries = [
  { subjectKey: 'SERVICIOS DE RED E INTERNET', label: 'SRI' },
  { subjectKey: 'BASES DE DATOS', label: 'BD' },
];

test('filtra por la clave del módulo, no por la etiqueta abreviada', () => {
  assert.deepEqual(entradasDe(entries, 'SERVICIOS DE RED E INTERNET'), [entries[0]]);
  assert.deepEqual(entradasDe(entries, 'SRI'), []);
});

test('sin clave devuelve todo', () => {
  assert.equal(entradasDe(entries, '').length, 2);
});
