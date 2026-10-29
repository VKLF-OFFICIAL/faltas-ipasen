import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DICT, LANGS, t, setLang, normalizeLang, fmtPct, corteKey } from '../lib/i18n.mjs';

const claves = Object.keys(DICT.es);

test('los cuatro idiomas tienen exactamente las mismas claves y ningún texto vacío', () => {
  assert.deepEqual(LANGS, ['es', 'en', 'fr', 'de']);
  for (const l of LANGS) {
    assert.deepEqual(Object.keys(DICT[l]).sort(), [...claves].sort(), l);
    for (const k of claves) assert.ok(DICT[l][k].trim(), l + ' ' + k);
  }
});

test('las variables {x} son las mismas en todas las traducciones', () => {
  const vars = (s) => (s.match(/\{\w+\}/g) || []).sort();
  for (const l of LANGS) for (const k of claves) assert.deepEqual(vars(DICT[l][k]), vars(DICT.es[k]), l + ' ' + k);
});

test('index.html solo usa claves que existen', () => {
  const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8');
  const usadas = [...html.matchAll(/data-i18n(?:-[a-z]+)?="([^"]+)"/g)].map((m) => m[1]);
  assert.ok(usadas.length > 20);
  for (const k of usadas) assert.ok(k in DICT.es, k);
});

test('t sustituye variables y setLang cae al español si el idioma no existe', () => {
  setLang('en');
  assert.equal(t('r.dias', { n: 3, total: 10 }), '3 of 10 school days');
  assert.equal(setLang('xx'), 'es');
  assert.equal(normalizeLang(null), 'es');
  assert.equal(t('no.existe'), 'no.existe');
});

test('porcentajes con el formato del idioma y etiquetas de fecha de fin', () => {
  setLang('es');
  assert.match(fmtPct(18.18), /^18,2\s%$/);
  setLang('en');
  assert.equal(fmtPct(18.18), '18.2%');
  setLang('es');
  assert.equal(corteKey('Fin de clases (inicio FCT)'), 'corte.fct');
  assert.equal(corteKey('Fin del curso'), 'corte.curso');
});
