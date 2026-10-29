import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { sync, SenecaError } from '../src/seneca.js';

const BASE = 'https://seneca.juntadeandalucia.es/seneca';
const menu = readFileSync(new URL('./fixtures/menupasen.html', import.meta.url), 'utf8');
// Séneca sirve ISO-8859-1: codificar cada carácter como un byte.
const latin1Bytes = (s) => Uint8Array.from([...s].map((c) => c.charCodeAt(0) & 0xff));

function fakeSeneca({ correcto = 'SI', mensaje = 'Bienvenido' } = {}) {
  const calls = [];
  const fetchImpl = async (url, init = {}) => {
    calls.push({ url: String(url), method: init.method || 'GET', body: init.body ? String(init.body) : '', cookie: init.headers && init.headers.cookie });
    const u = String(url);
    const html = (s, headers = {}) => new Response(latin1Bytes(s), { status: 200, headers: { 'content-type': 'text/html', ...headers } });
    if (u === `${BASE}/jsp/portalpasen/`) return html('<form name="FORMULARIO_IDEN_USU"></form>', { 'set-cookie': 'JSESSIONID=abc; Path=/' });
    if (u.startsWith(`${BASE}/jsp/ComprobarUsuarioExt.jsp`)) {
      return new Response(latin1Bytes(`<?xml version="1.0"?><respuesta><correcto>${correcto}</correcto><mensaje>${mensaje}</mensaje></respuesta>`), { headers: { 'content-type': 'text/xml' } });
    }
    if (u.startsWith(`${BASE}/jsp/Acceso.jsp`)) return new Response(null, { status: 302, headers: { location: `${BASE}/nav/START`, 'set-cookie': 'SenecaP=xyz; Path=/' } });
    if (u === `${BASE}/nav/START`) return html(`<html><body>${menu}</body></html>`);
    const m = /\/menus\/(TOKEN\d+)$/.exec(u);
    if (m) return html(`<html><body>SECCION ${m[1]}</body></html>`);
    return new Response('not found', { status: 404 });
  };
  return { fetchImpl, calls };
}

test('sync logs in, keeps cookies and returns the three sections', async () => {
  const { fetchImpl, calls } = fakeSeneca();
  const out = await sync('usuario1', 'Clave123', fetchImpl);
  assert.match(out.absences, /SECCION TOKEN1\b/);
  assert.match(out.schedule, /SECCION TOKEN\d+/);
  assert.match(out.calendar, /SECCION TOKEN\d+/);
  const login = calls.find((c) => c.url.includes('ComprobarUsuarioExt.jsp'));
  assert.equal(login.method, 'POST');
  assert.match(login.body, /USUARIO=usuario1/);
  assert.match(login.body, /C_INTERFAZ=PASEN/);
  assert.match(login.body, /CLAVECIFRADA=\d+/);
  assert.doesNotMatch(login.body, /Clave123/);
  const section = calls.find((c) => /\/menus\/TOKEN1$/.test(c.url));
  assert.equal(section.method, 'POST');
  assert.match(section.cookie, /JSESSIONID=abc/);
  assert.match(section.cookie, /SenecaP=xyz/);
});

test('a rejected login is LOGIN_FAILED with Séneca\'s message and no retry', async () => {
  const { fetchImpl, calls } = fakeSeneca({ correcto: 'NO', mensaje: 'Usuario o clave incorrectos' });
  await assert.rejects(sync('u', 'mal', fetchImpl), (e) => e instanceof SenecaError && e.code === 'LOGIN_FAILED' && /incorrectos/.test(e.message));
  assert.equal(calls.filter((c) => c.url.includes('ComprobarUsuarioExt.jsp')).length, 1);
});

test('a blocked user is LOCKED', async () => {
  const { fetchImpl } = fakeSeneca({ correcto: 'NO', mensaje: 'Usuario bloqueado' });
  await assert.rejects(sync('u', 'x', fetchImpl), (e) => e.code === 'LOCKED');
});
