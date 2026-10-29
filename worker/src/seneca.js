import { cifrar } from './cifrar.js';
import { latin1, menuEntries, findEntry } from './html.js';

const BASE = 'https://seneca.juntadeandalucia.es/seneca';
const ORIGIN = 'https://seneca.juntadeandalucia.es';
const UA = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36';

// Mismas rutas de menú que el APK (app/src/main/assets/scraper/*.js).
export const SECTIONS = {
  absences: [[/faltas/i, /^consulta/i], [/^faltas de asistencia$/i], [/faltas/i, /^(consulta|faltas)/i]],
  schedule: [[/tutor/i, /^horario/i], [/^horario/i]],
  calendar: [[/centro/i, /^calendario/i], [/^calendario/i]],
};
const LABELS = { absences: 'Faltas de asistencia > Consulta', schedule: 'Tutoría > Horario', calendar: 'Centro > Calendario escolar' };

export class SenecaError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}

const rnd = (n) => Math.round(Math.random() * n);

class Session {
  constructor(fetchImpl) { this.fetchImpl = fetchImpl; this.cookies = new Map(); }

  async request(url, { method = 'GET', form } = {}) {
    let target = url;
    let body = form ? new URLSearchParams(form).toString() : undefined;
    for (let hop = 0; hop < 10; hop++) {
      const headers = { 'user-agent': UA, accept: 'text/html,application/xhtml+xml,*/*' };
      if (this.cookies.size) headers.cookie = [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; ');
      if (body !== undefined) headers['content-type'] = 'application/x-www-form-urlencoded';
      // Solo Séneca: ni redirecciones ni enlaces del menú pueden sacar las cookies de ahí.
      if (new URL(target).origin !== ORIGIN) throw new SenecaError('UPSTREAM_ERROR', 'Séneca ha redirigido a un sitio no permitido');
      let res;
      try {
        res = await this.fetchImpl(target, { method, headers, body, redirect: 'manual', signal: AbortSignal.timeout(20000) });
      } catch (e) {
        if (e && (e.name === 'TimeoutError' || e.name === 'AbortError')) throw new SenecaError('UPSTREAM_ERROR', 'iPasen tarda demasiado en responder');
        throw e;
      }
      this.store(res.headers);
      const location = res.headers.get('location');
      if (res.status >= 300 && res.status < 400 && location) {
        if (res.body && res.body.cancel) res.body.cancel().catch(() => {});
        target = new URL(location, target).href;
        method = 'GET';
        body = undefined;
        continue;
      }
      if (!res.ok) throw new SenecaError('UPSTREAM_ERROR', `Séneca respondió ${res.status}`);
      return latin1(await res.arrayBuffer());
    }
    throw new SenecaError('UPSTREAM_ERROR', 'demasiadas redirecciones de Séneca');
  }

  store(headers) {
    const list = typeof headers.getSetCookie === 'function'
      ? headers.getSetCookie()
      : (headers.get('set-cookie') || '').split(/,(?=\s*[A-Za-z0-9_]+=)/);
    for (const line of list) {
      const m = /^\s*([^=;\s]+)=([^;]*)/.exec(line);
      if (m) this.cookies.set(m[1], m[2]);
    }
  }
}

function xmlValue(xml, tag) {
  const m = new RegExp(`<${tag}>([\\s\\S]*?)</${tag}>`, 'i').exec(xml);
  return m ? m[1].trim() : '';
}

// El fetch global no puede llamarse como método de otro objeto en Workers ("Illegal invocation").
export async function sync(usuario, clave, fetchImpl = (...args) => fetch(...args)) {
  const s = new Session(fetchImpl);
  await s.request(`${BASE}/jsp/portalpasen/`);

  const xml = await s.request(`${BASE}/jsp/ComprobarUsuarioExt.jsp`, {
    method: 'POST',
    form: {
      N_V_: `NV_${rnd(10000)}`,
      rndval: String(rnd(1000000000)),
      NAV_WEB_NOMBRE: 'Chrome',
      NAV_WEB_VERSION: '130',
      RESOLUCION: '1080x2400',
      CLAVECIFRADA: cifrar(clave),
      USUARIO: usuario,
      C_INTERFAZ: 'PASEN',
    },
  });
  const correcto = xmlValue(xml, 'correcto');
  if (!correcto) throw new SenecaError('UPSTREAM_ERROR', 'Séneca no ha respondido como se esperaba');
  const mensaje = xmlValue(xml, 'mensaje') || 'Usuario o clave incorrectos';
  if (correcto !== 'SI') {
    // Nunca se reintenta: Séneca bloquea la cuenta tras varios fallos.
    if (/bloquead|caducad/i.test(mensaje)) throw new SenecaError('LOCKED', mensaje);
    throw new SenecaError('LOGIN_FAILED', mensaje);
  }

  const start = await s.request(`${BASE}/jsp/Acceso.jsp?rndval=${rnd(1000000000)}`, {
    method: 'POST',
    form: { CON_PRUEBA: 'N', USER_QR: '' },
  });
  const entries = menuEntries(start);
  if (!entries.length) throw new SenecaError('UPSTREAM_ERROR', 'Séneca no ha mostrado el menú de PASEN tras iniciar sesión');

  const out = {};
  for (const key of ['schedule', 'calendar', 'absences']) {
    const entry = findEntry(entries, SECTIONS[key]);
    if (!entry) {
      const available = entries.map((e) => e.path.join(' > ')).join('; ');
      throw new SenecaError('MENU_NOT_FOUND', `tu menú de iPasen no tiene «${LABELS[key]}». Opciones: ${available.slice(0, 400)}`);
    }
    // Las URLs del menú son de un solo uso y se abren con POST, como hace Navegacion.llamadaMenu.
    // "../menus/X" es relativo a /seneca/nav/… → /seneca/menus/X.
    out[key] = await s.request(new URL(entry.link, `${BASE}/nav/`).href, {
      method: 'POST',
      form: { COD_PAGINA_ANTERIOR: '', TIEMPO_PAGINA_ANTERIOR: '' },
    });
  }
  return out;
}
