import { sync, SenecaError } from './seneca.js';

const ORIGINS = new Set(['https://vklf-official.github.io', 'http://localhost:8000']);
const STATUS = { BAD_REQUEST: 400, LOGIN_FAILED: 401, LOCKED: 403, RATE_LIMITED: 429, MENU_NOT_FOUND: 422, UPSTREAM_ERROR: 502 };

function json(body, status, origin) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      ...(origin ? { 'access-control-allow-origin': origin, vary: 'Origin' } : {}),
    },
  });
}

export default {
  async fetch(request, env) {
    const origin = request.headers.get('origin');
    const allowed = ORIGINS.has(origin) ? origin : null;
    const url = new URL(request.url);
    if (url.pathname !== '/sync') return json({ ok: false, code: 'BAD_REQUEST', message: 'ruta desconocida' }, 404, allowed);
    if (!allowed) return json({ ok: false, code: 'BAD_REQUEST', message: 'origen no permitido' }, 403, null);
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: {
          'access-control-allow-origin': allowed,
          'access-control-allow-methods': 'POST',
          'access-control-allow-headers': 'content-type',
          'access-control-max-age': '86400',
          vary: 'Origin',
        },
      });
    }
    if (request.method !== 'POST') return json({ ok: false, code: 'BAD_REQUEST', message: 'usa POST' }, 405, allowed);

    if (env && env.SYNC_LIMIT) {
      const { success } = await env.SYNC_LIMIT.limit({ key: request.headers.get('cf-connecting-ip') || 'local' });
      if (!success) return json({ ok: false, code: 'RATE_LIMITED', message: 'Demasiados intentos. Espera un minuto.' }, 429, allowed);
    }

    let body;
    try { body = await request.json(); } catch { body = {}; }
    const usuario = typeof body.usuario === 'string' ? body.usuario.trim() : '';
    const clave = typeof body.clave === 'string' ? body.clave : '';
    if (!usuario || !clave || usuario.length > 100 || clave.length > 200) {
      return json({ ok: false, code: 'BAD_REQUEST', message: 'Escribe tu usuario y tu clave de iPasen.' }, 400, allowed);
    }

    try {
      const pages = await sync(usuario, clave);
      return json({ ok: true, ...pages }, 200, allowed);
    } catch (e) {
      const code = e instanceof SenecaError ? e.code : 'UPSTREAM_ERROR';
      const message = e instanceof SenecaError ? e.message : 'No se pudo conectar con iPasen.';
      return json({ ok: false, code, message }, STATUS[code] || 502, allowed);
    }
  },
};
