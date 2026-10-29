import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DOMParser } from 'linkedom';
import { SECTIONS, findEntry, menuLinks, menuPath } from '../lib/menu.mjs';

const doc = new DOMParser().parseFromString(
  `<html><body>${readFileSync(new URL('../../worker/test/fixtures/menupasen.html', import.meta.url), 'utf8')}</body></html>`, 'text/html');
const links = menuLinks(doc);

test('hay entradas con ruta completa', () => {
  assert.ok(links.length > 3);
  assert.deepEqual(menuPath(links.find((a) => a.dataset.menulink === '../menus/TOKEN1')),
    ['Seguimiento del curso', 'Faltas de asistencia', 'Consulta']);
});

for (const [key, expected] of [['absences', 'Faltas de asistencia > Consulta'], ['schedule', 'Tutoría > Horario'], ['calendar', 'Centro > Calendario escolar']]) {
  test(`encuentra ${expected}`, () => {
    const a = findEntry(links, SECTIONS[key].paths);
    assert.ok(a, key);
    assert.ok(menuPath(a).join(' > ').endsWith(expected), menuPath(a).join(' > '));
    assert.ok(a.dataset.menulink);
  });
}

test('sin coincidencia devuelve null', () => {
  assert.equal(findEntry(links, [[/inexistente/i]]), null);
});
