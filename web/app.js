// Faltas iPasen (web). Los datos (de la consulta al Worker o del fragmento de la URL del marcador) viven
// solo en memoria y desaparecen al recargar; solo se guardan el tema y el idioma elegidos (preferencias de
// interfaz, nunca datos de iPasen). Nada de iPasen se inserta como HTML.
import { FaltasWeb } from './shared/shared.mjs';
import { unpack } from './lib/transfer.mjs';
import { entradasDe } from './lib/history.mjs';
import { readSchedule, readCalendar, readAbsences } from './readers/readers.mjs';
import { WORKER_URL } from './config.js';
import { t, setLang, getLang, normalizeLang, fmtNum, fmtPct, fmtDate, corteKey } from './lib/i18n.mjs';

const $ = (id) => document.getElementById(id);
const VISTAS = ['bienvenida', 'datos', 'resultados', 'historial'];

const hoy = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Madrid' }).format(new Date());
const raw = (d) => JSON.stringify({ ok: true, data: d });

let datos = null;   // { schedule, calendar, absences }, solo en memoria
let niveles = [];
let resultado = null;
let ajustes = { fin: '', umbral: 0 };
let convalidadas = []; // claves de módulos convalidados, solo en memoria
let origen = 'consulta'; // 'consulta' | 'marcador': a dónde vuelve "Nueva consulta"
let vistaActual = '';
let textoError = null;  // función que devuelve el error visible, para repintarlo al cambiar de idioma
let textoEstado = null; // ídem para el estado de la consulta

// ---------- Preferencias de interfaz (tema e idioma) ----------

const PREF_TEMA = 'faltas-tema';
const PREF_IDIOMA = 'faltas-idioma';

function leerPref(clave) {
  try { return localStorage.getItem(clave); } catch (e) { return null; }
}

function guardarPref(clave, valor) {
  try { localStorage.setItem(clave, valor); } catch (e) { /* sin almacenamiento: dura hasta recargar */ }
}

function pintarTema() {
  const oscuro = document.documentElement.getAttribute('data-theme') === 'dark';
  $('tema').setAttribute('aria-pressed', String(oscuro));
  $('tema').setAttribute('aria-label', t(oscuro ? 'top.toLight' : 'top.toDark'));
}

function alternarTema() {
  const oscuro = document.documentElement.getAttribute('data-theme') !== 'dark';
  document.documentElement.setAttribute('data-theme', oscuro ? 'dark' : 'light');
  guardarPref(PREF_TEMA, oscuro ? 'dark' : 'light');
  pintarTema();
}

// Traduce los textos fijos de la página y vuelve a pintar la vista actual con los datos en memoria.
function aplicarIdioma(lang) {
  setLang(lang);
  document.documentElement.lang = getLang();
  $('idioma').value = getLang();
  for (const e of document.querySelectorAll('[data-i18n]')) e.textContent = t(e.getAttribute('data-i18n'));
  for (const e of document.querySelectorAll('[data-i18n-placeholder]')) e.setAttribute('placeholder', t(e.getAttribute('data-i18n-placeholder')));
  for (const e of document.querySelectorAll('[data-i18n-content]')) e.setAttribute('content', t(e.getAttribute('data-i18n-content')));
  pintarTema();
  pintarVer();
  if (niveles.length) pintarNiveles();
  $('copiado').textContent = '';
  if (textoError) error(textoError);
  if (textoEstado) estado(textoEstado);
  if (resultado && (vistaActual === 'resultados' || vistaActual === 'historial')) pintarResultados();
  if (resultado && vistaActual === 'historial') pintarFiltro();
}

function mostrar(vista) {
  vistaActual = vista;
  for (const v of VISTAS) $('vista-' + v).hidden = v !== vista;
  window.scrollTo(0, 0);
}

// msg: texto, función que lo devuelve (se vuelve a llamar al cambiar de idioma) o vacío para ocultarlo.
function error(msg) {
  textoError = !msg ? null : typeof msg === 'function' ? msg : () => msg;
  const texto = textoError ? textoError() : '';
  $('error').hidden = !texto;
  $('error').textContent = texto;
}

function estado(fn) {
  textoEstado = fn;
  $('c-estado').textContent = fn ? fn() : '';
}

function el(tag, props = {}, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === 'class') e.className = v; else e.setAttribute(k, v);
  }
  e.append(...kids);
  return e;
}

// ---------- Marcador ----------

function codigoMarcador() {
  const url = new URL('bookmarklet.js', location.href).href;
  return "javascript:(()=>{const s=document.createElement('script');s.type='module';s.src='" + url +
    "?v='+Date.now();document.body.appendChild(s)})()";
}

function bienvenida() {
  const codigo = codigoMarcador();
  $('marcador').setAttribute('href', codigo);
  $('marcador').addEventListener('click', (e) => e.preventDefault());
  $('copiar').addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(codigo);
      $('copiado').textContent = t('bm.copied');
    } catch (e) {
      $('copiado').textContent = t('bm.copyFail');
    }
  });
  cargarNiveles();
  mostrar('bienvenida');
}

// ---------- Importación ----------

const isArr = Array.isArray;
function valido(d) {
  return d && typeof d === 'object' && d.v === 1 && isArr(d.schedule) && isArr(d.calendar) &&
    d.absences && typeof d.absences === 'object' && isArr(d.absences.absences);
}

async function importar(fragmento) {
  let d;
  try {
    d = await unpack(fragmento);
  } catch (e) {
    d = null;
  }
  history.replaceState(null, '', location.pathname);
  if (!valido(d)) {
    error(() => t('e.invalid'));
    bienvenida();
    return;
  }
  datos = { schedule: d.schedule, calendar: d.calendar, absences: d.absences };
  formulario();
}

// ---------- Formulario ----------

function cargarNiveles() {
  if (niveles.length) return;
  niveles = JSON.parse(FaltasWeb.niveles(hoy));
  pintarNiveles();
}

// Nombre de la etapa en el idioma actual (por id); si no hay traducción, el que da FaltasWeb.
const nombreNivel = (n) => (t('nivel.' + n.id) === 'nivel.' + n.id ? n.label : t('nivel.' + n.id));

// Rellena (o vuelve a rellenar al cambiar de idioma) las etapas, conservando la elegida.
function pintarNiveles() {
  for (const p of ['', 'c-']) {
    const actual = $(p + 'etapa').value;
    $(p + 'etapa').replaceChildren(el('option', { value: '' }, t('f.etapaElige')),
      ...niveles.map((n) => el('option', { value: n.id }, nombreNivel(n))));
    $(p + 'etapa').value = actual;
    etapaCambiada(p);
  }
}

function formulario() {
  error('');
  cargarNiveles();
  origen = 'marcador';
  mostrar('datos');
}

// Al elegir etapa solo cambia la etiqueta y la pista de la fecha; umbral y fecha los escribe la persona.
const fechaCorta = (iso) => fmtDate(iso, { day: '2-digit', month: '2-digit', year: 'numeric' });

function etapaCambiada(p) {
  const n = niveles.find((x) => x.id === $(p + 'etapa').value);
  $(p + 'fin-label').textContent = n ? t(corteKey(n.cutoffLabel)) : t('f.fin');
  $(p + 'fin-ayuda').hidden = !n;
  $(p + 'fin-ayuda').textContent = n ? t('f.finHabitual', { fecha: fechaCorta(n.cutoff) }) : '';
}

// Devuelve null si el formulario es correcto, o la clave del mensaje de error.
function validar(p) {
  if (!$(p + 'etapa').value) return 'e.etapa';
  const u = $(p + 'umbral').value.trim();
  const umbral = Number(u);
  if (!u || !Number.isFinite(umbral) || umbral < 1 || umbral > 100) return 'e.umbral';
  if (!$(p + 'fin').value) return 'e.fin';
  return null;
}

// ---------- Resultados ----------

const msg = (e) => (e && e.message ? e.message : String(e));
const horas = (x) => t('r.horasN', { n: fmtNum(Math.round(x * 100) / 100) });

// Devuelve null si va bien, o el mensaje de error.
function calcular(prefijo) {
  const invalido = validar(prefijo);
  if (invalido) return t(invalido);
  ajustes = { fin: $(prefijo + 'fin').value, umbral: Number($(prefijo + 'umbral').value) };
  convalidadas = [];
  const fallo = recalcular();
  if (fallo) return fallo;
  pintarResultados();
  mostrar('resultados');
  return null;
}

// Calcula con los ajustes y las convalidaciones actuales. Devuelve null si va bien, o el mensaje de error.
function recalcular() {
  try {
    resultado = JSON.parse(FaltasWeb.calcular(
      raw(datos.schedule), raw(datos.calendar), raw(datos.absences), ajustes.fin, hoy, ajustes.umbral,
      JSON.stringify(convalidadas)));
  } catch (e) {
    return msg(e);
  }
  return null;
}

function convalidar(clave, marcar) {
  const anterior = convalidadas;
  convalidadas = marcar ? [...anterior, clave] : anterior.filter((k) => k !== clave);
  const fallo = recalcular();
  if (fallo) {
    convalidadas = anterior;
    error(() => t('e.recalcular', { detalle: fallo }));
    return;
  }
  error('');
  pintarResultados();
  const sel = marcar ? '[data-quitar="' : '[data-conv="';
  const boton = document.querySelector(sel + CSS.escape(clave) + '"]');
  if (boton) boton.focus();
  else { $('total').setAttribute('tabindex', '-1'); $('total').focus(); }
}

// ---------- Consulta a iPasen (vía Worker) ----------

const parse = (html) => new DOMParser().parseFromString(String(html), 'text/html');

// Una vuelta completa (sesión y tokens nuevos). Devuelve { respuesta } si Séneca contestó con algo
// definitivo (incluidos LOGIN_FAILED, LOCKED, RATE_LIMITED, BAD_REQUEST y MENU_NOT_FOUND, que nunca se
// reintentan), o { transitorio: true, mensaje } si fue un fallo de red, un timeout o UPSTREAM_ERROR.
async function intentoConsulta(cuerpo) {
  let respuesta;
  try {
    const r = await fetch(WORKER_URL + '/sync', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: cuerpo,
      signal: AbortSignal.timeout(100000),
    });
    respuesta = await r.json();
  } catch (e) {
    return { transitorio: true, mensaje: '' };
  }
  if (!respuesta || typeof respuesta !== 'object') return { transitorio: true, mensaje: '' };
  if (!respuesta.ok && respuesta.code === 'UPSTREAM_ERROR') {
    return { transitorio: true, mensaje: typeof respuesta.message === 'string' ? respuesta.message : '' };
  }
  return { respuesta };
}

async function consultar() {
  const boton = $('c-enviar');
  error('');
  const invalido = validar('c-');
  if (invalido) {
    error(() => t(invalido));
    return;
  }
  if (!$('c-usuario').value.trim() || !$('c-clave').value) {
    error(() => t('e.credenciales'));
    return;
  }
  boton.disabled = true;
  boton.setAttribute('aria-busy', 'true');
  estado(() => t('s.espera'));
  const cuerpo = JSON.stringify({ usuario: $('c-usuario').value.trim(), clave: $('c-clave').value });
  let res = await intentoConsulta(cuerpo);
  if (res.transitorio) {
    estado(() => t('s.lento'));
    res = await intentoConsulta(cuerpo);
  }
  estado(null);
  boton.disabled = false;
  boton.removeAttribute('aria-busy');
  if (res.transitorio) {
    error(() => t('e.noResponde') + (res.mensaje ? ' (' + res.mensaje + ')' : ''));
    return;
  }
  const respuesta = res.respuesta;
  if (!respuesta.ok) {
    // Los mensajes de iPasen/Séneca (vía Worker) se muestran tal cual.
    error(typeof respuesta.message === 'string' && respuesta.message ? respuesta.message : () => t('e.inesperado'));
    return;
  }
  let fallo = null;
  try {
    datos = {
      schedule: readSchedule(parse(respuesta.schedule)),
      calendar: readCalendar(String(respuesta.calendar)),
      absences: readAbsences(parse(respuesta.absences)),
    };
    fallo = calcular('c-');
  } catch (e) {
    fallo = msg(e);
  }
  if (fallo) {
    error(() => t('e.ilegible', { detalle: fallo }));
    return;
  }
  origen = 'consulta';
  $('c-clave').value = '';
  ocultarClave();
}

// ---------- Ver / ocultar la clave ----------

function pintarVer() {
  const visible = $('c-clave').type === 'text';
  $('c-ver').textContent = t(visible ? 'f.ocultar' : 'f.ver');
  $('c-ver').setAttribute('aria-pressed', String(visible));
  $('c-ver').setAttribute('aria-label', t(visible ? 'f.ocultarAria' : 'f.verAria'));
}

function ocultarClave() {
  $('c-clave').type = 'password';
  pintarVer();
}

function alternarClave() {
  $('c-clave').type = $('c-clave').type === 'password' ? 'text' : 'password';
  pintarVer();
}

function nueva() {
  error('');
  convalidadas = [];
  $('c-clave').value = '';
  ocultarClave();
  if (origen === 'marcador') formulario(); else mostrar('bienvenida');
}

// La regla va de 0 a 1,25 veces el umbral: el límite queda siempre en el 80 % del ancho.
const ESCALA = 1.25;
const anchoRegla = (percent) => (Math.min(Math.max(percent / (ajustes.umbral * ESCALA), 0), 1) * 100).toFixed(1) + '%';

function regla(percent, risk) {
  const fill = el('span', { class: 'regla-fill bg-' + risk });
  fill.style.width = anchoRegla(percent);
  return el('div', { class: 'regla', 'aria-hidden': 'true' }, fill, el('span', { class: 'regla-tope' }));
}

function dato(etiqueta, valor, clase = '') {
  return el('div', { class: 'stat' + (clase ? ' ' + clase : '') }, el('dt', {}, etiqueta), el('dd', {}, valor));
}

function pintarResultados() {
  const r = resultado;
  const g = r.global;
  $('total').textContent = fmtPct(g.percent);
  $('total-estado').textContent = t('risk.' + g.risk);
  $('total-estado').className = 'chip chip-' + g.risk;
  $('total-barra').className = 'regla-fill bg-' + g.risk;
  $('total-barra').style.width = anchoRegla(g.percent);
  $('total-limite').textContent = t('r.limite', { pct: fmtPct(ajustes.umbral, 0) });
  $('total-detalle').textContent = t('r.horas', { faltas: fmtNum(Math.round(g.absentHours * 100) / 100), total: fmtNum(Math.round(g.courseHours * 100) / 100) });
  $('total-dias').textContent = t('r.dias', { n: r.lectivoDaysElapsed, total: r.lectivoDaysTotal });
  $('total-dias-barra').style.width = (r.lectivoDaysTotal ? Math.min(r.lectivoDaysElapsed / r.lectivoDaysTotal, 1) * 100 : 0).toFixed(1) + '%';

  $('aviso').hidden = !r.skipped.length;
  if (r.skipped.length) {
    $('aviso-resumen').textContent = t(r.skipped.length === 1 ? 'r.skipped1' : 'r.skippedN', { n: r.skipped.length });
    $('aviso-lista').replaceChildren(...r.skipped.map((s) => el('li', {}, s)));
  }

  $('modulos').replaceChildren(...r.subjects.map((s) => {
    const boton = el('button', { type: 'button', class: 'btn btn-quiet btn-small', 'aria-pressed': 'false', 'data-conv': s.label }, t('r.convalidada'));
    boton.addEventListener('click', () => convalidar(s.label, true));
    return el('article', { class: 'modulo risk-card-' + s.risk },
      el('div', { class: 'modulo-head' },
        el('div', { class: 'modulo-id' }, el('div', { class: 'abbr' }, s.abbreviation), el('div', { class: 'nombre' }, s.name)),
        el('div', { class: 'modulo-pct' },
          el('div', { class: 'pct risk-' + s.risk }, fmtPct(s.percent)),
          el('span', { class: 'chip chip-' + s.risk }, t('risk.' + s.risk)))),
      regla(s.percent, s.risk),
      el('dl', { class: 'stats' },
        dato(t('r.margen'), horas(s.marginHours), 'stat-margen'),
        dato(t('r.justificadas'), horas(s.justifiedHours)),
        dato(t('r.sinJustificar'), horas(s.unjustifiedHours))),
      el('div', { class: 'modulo-foot' }, boton));
  }));

  $('convalidadas').hidden = !r.convalidated.length;
  $('convalidadas-lista').replaceChildren(...r.convalidated.map((c) => {
    const quitar = el('button', { type: 'button', class: 'btn btn-quiet btn-small', 'aria-pressed': 'true', 'data-quitar': c.label }, t('r.quitar'));
    quitar.addEventListener('click', () => convalidar(c.label, false));
    return el('div', { class: 'modulo convalidada' },
      el('div', { class: 'modulo-head' },
        el('div', { class: 'modulo-id' }, el('div', { class: 'abbr' }, c.abbreviation || c.label), el('div', { class: 'nombre' }, c.name)),
        quitar));
  }));
}

// ---------- Historial ----------

const capital = (s) => s.charAt(0).toUpperCase() + s.slice(1);

function pintarFiltro() {
  const filtro = $('filtro');
  const actual = filtro.value;
  filtro.replaceChildren(el('option', { value: '' }, t('h.todos')),
    ...resultado.subjects.map((s) => el('option', { value: s.label }, s.abbreviation + ' · ' + s.name)));
  filtro.value = resultado.subjects.some((s) => s.label === actual) ? actual : '';
  pintarHistorial();
}

function historial() {
  if (!resultado) return;
  $('filtro').value = '';
  pintarFiltro();
  mostrar('historial');
}

function pintarHistorial() {
  const key = $('filtro').value;
  const dias = [];
  for (const dia of resultado.history) {
    const entradas = entradasDe(dia.entries, key);
    if (!entradas.length) continue;
    dias.push(el('div', { class: 'dia' },
      el('h3', {}, capital(fmtDate(dia.date, { weekday: 'long', day: 'numeric', month: 'long' }))),
      ...entradas.map((e) => el('div', { class: 'entrada' },
        el('span', { class: 'tramo' }, e.tramo),
        el('span', { class: 'entrada-mod' }, el('strong', {}, e.label), ' ', el('span', { class: 'entrada-h' }, horas(e.hours))),
        el('span', { class: 'just ' + (e.justified ? 'just-si' : 'just-no') }, t(e.justified ? 'h.justificada' : 'h.sinJustificar'))))));
  }
  $('dias').replaceChildren(...(dias.length ? dias : [el('p', { class: 'small vacio' }, t('h.vacio'))]));
}

// ---------- Arranque ----------

$('etapa').addEventListener('change', () => etapaCambiada(''));
$('c-etapa').addEventListener('change', () => etapaCambiada('c-'));
$('c-ver').addEventListener('click', alternarClave);
$('tema').addEventListener('click', alternarTema);
$('idioma').addEventListener('change', () => {
  guardarPref(PREF_IDIOMA, normalizeLang($('idioma').value));
  aplicarIdioma($('idioma').value);
});
$('vista-datos').addEventListener('submit', (e) => {
  e.preventDefault();
  const invalido = validar('');
  const f = invalido ? null : calcular('');
  if (invalido) error(() => t(invalido));
  else if (f) error(() => t('e.calcular', { detalle: f }));
  else error('');
});
$('vista-consulta').addEventListener('submit', (e) => { e.preventDefault(); consultar(); });
$('cambiar').addEventListener('click', nueva);
$('historial-btn').addEventListener('click', historial);
$('volver').addEventListener('click', () => mostrar('resultados'));
$('filtro').addEventListener('change', pintarHistorial);

if (leerPref(PREF_TEMA) === 'dark') document.documentElement.setAttribute('data-theme', 'dark');
aplicarIdioma(normalizeLang(leerPref(PREF_IDIOMA)));

const m = /^#importar=(.*)$/.exec(location.hash);
if (m) importar(m[1]); else bienvenida();
