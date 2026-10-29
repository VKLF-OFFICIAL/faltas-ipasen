import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { latin1, menuEntries, findEntry } from '../src/html.js';

const menu = readFileSync(new URL('./fixtures/menupasen.html', import.meta.url), 'utf8');

test('latin1 decodes ISO-8859-1 bytes', () => {
  assert.equal(latin1(new Uint8Array([0x46, 0xED, 0x61, 0xF1]).buffer), 'Fíañ');
});

test('menuEntries gives each linked entry with its menu path', () => {
  const entries = menuEntries(menu);
  assert.deepEqual(entries[0], { path: ['Seguimiento del curso', 'Faltas de asistencia', 'Consulta'], link: '../menus/TOKEN1' });
  assert.ok(entries.some((e) => e.path.join(' > ') === 'Tutoría > Horario'));
  assert.ok(entries.some((e) => e.path.join(' > ') === 'Centro > Calendario escolar'));
});

test('findEntry matches menu paths like the APK', () => {
  const entries = menuEntries(menu);
  assert.equal(findEntry(entries, [[/faltas/i, /^consulta/i]]).link, '../menus/TOKEN1');
  assert.equal(findEntry(entries, [[/nada/i]]), null);
});
