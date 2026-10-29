// Faltas iPasen (web). Sin almacenamiento: los datos (de la consulta al Worker o del fragmento de la URL
// del marcador) viven solo en memoria y desaparecen al recargar. Nada de iPasen se inserta como HTML.
import { FaltasWeb } from './shared/shared.mjs';
import { unpack } from './lib/transfer.mjs';
import { entradasDe } from './lib/history.mjs';
import { readSchedule, readCalendar, readAbsences } from './readers/readers.mjs';
import { WORKER_URL } from './config.js';

const $ = (id) => document.getElementById(id);
const VISTAS = ['bienvenida', 'datos', 'resultados', 'historial'];
const INVALID = 'Los datos del marcador no son válidos. Vuelve a pulsarlo en iPasen.';

const hoy = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Madrid' }).format(new Date());
const raw = (d) => JSON.stringify({ ok: true, data: d });

let datos = null;   // { schedule, calendar, absences }, solo en memoria
let niveles = [];
let resultado = null;
let ajustes = { fin: '', umbral: 0 };
let origen = 'consulta'; // 'consulta' | 'marcador': a dónde vuelve "Nueva consulta"

function mostrar(vista) {
  for (const v of VISTAS) $('vista-' + v).hidden = v !== vista;
  window.scrollTo(0, 0);
}

function error(msg) {
  $('error').hidden = !msg;
  $('error').textContent = msg || '';
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
      $('copiado').textContent = 'Copiado.';
    } catch (e) {
      $('copiado').textContent = 'No se pudo copiar; mantén pulsado el botón “Faltas iPasen” y copia el enlace.';
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
    error(INVALID);
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
  const def = niveles.some((n) => n.id === 'FP_SUPERIOR') ? 'FP_SUPERIOR' : niveles[0].id;
  for (const p of ['', 'c-']) {
    for (const n of niveles) $(p + 'etapa').append(el('option', { value: n.id }, n.label));
    $(p + 'etapa').value = def;
    rellenar(p);
  }
}

function formulario() {
  error('');
  cargarNiveles();
  origen = 'marcador';
  mostrar('datos');
}

function rellenar(p) {
  const n = niveles.find((x) => x.id === $(p + 'etapa').value);
  $(p + 'umbral').value = n.threshold;
  $(p + 'fin').value = n.cutoff;
  $(p + 'fin-label').textContent = n.cutoffLabel;
}

// ---------- Resultados ----------

const msg = (e) => (e && e.message ? e.message : String(e));
const num = (x) => String(Math.round(x * 100) / 100).replace('.', ',');

// Devuelve null si va bien, o el mensaje de error.
function calcular(prefijo) {
  ajustes = { fin: $(prefijo + 'fin').value, umbral: Number($(prefijo + 'umbral').value) };
  try {
    resultado = JSON.parse(FaltasWeb.calcular(
      raw(datos.schedule), raw(datos.calendar), raw(datos.absences), ajustes.fin, hoy, ajustes.umbral));
  } catch (e) {
    return msg(e);
  }
  pintarResultados();
  mostrar('resultados');
  return null;
}

// ---------- Consulta a iPasen (vía Worker) ----------

const parse = (html) => new DOMParser().parseFromString(String(html), 'text/html');
const ILEGIBLE = 'iPasen ha cambiado o no se pudo leer: ';

async function consultar() {
  const boton = $('c-enviar');
  error('');
  boton.disabled = true;
  $('c-estado').textContent = 'Conectando con iPasen…';
  let respuesta = null;
  try {
    const r = await fetch(WORKER_URL + '/sync', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ usuario: $('c-usuario').value.trim(), clave: $('c-clave').value }),
    });
    respuesta = await r.json();
  } catch (e) {
    respuesta = null;
  }
  $('c-estado').textContent = '';
  boton.disabled = false;
  if (!respuesta || typeof respuesta !== 'object') {
    error('No se pudo conectar con el servidor. Comprueba tu conexión.');
    return;
  }
  if (!respuesta.ok) {
    error(typeof respuesta.message === 'string' && respuesta.message ? respuesta.message : 'iPasen no ha respondido como se esperaba.');
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
    error(ILEGIBLE + fallo);
    return;
  }
  origen = 'consulta';
  $('c-clave').value = '';
}

function nueva() {
  error('');
  $('c-clave').value = '';
  if (origen === 'marcador') formulario(); else mostrar('bienvenida');
}

function pintarResultados() {
  const r = resultado;
  const umbral = ajustes.umbral;
  $('total').textContent = r.global.percentText;
  $('total-detalle').textContent = num(r.global.absentHours) + ' h de ' + num(r.global.courseHours) +
    ' h · ' + r.lectivoDaysElapsed + ' de ' + r.lectivoDaysTotal + ' días lectivos';

  $('aviso').hidden = !r.skipped.length;
  if (r.skipped.length) {
    $('aviso-resumen').textContent = r.skipped.length + (r.skipped.length === 1 ? ' fila no se ha podido leer' : ' filas no se han podido leer');
    $('aviso-lista').replaceChildren(...r.skipped.map((s) => el('li', {}, s)));
  }

  $('modulos').replaceChildren(...r.subjects.map((s) => {
    const frac = Math.min(Math.max(s.percent / umbral, 0), 1);
    const bar = el('div', { class: 'bar', role: 'presentation' }, el('span', { class: 'bg-' + s.risk }));
    bar.firstChild.style.width = (frac * 100).toFixed(1) + '%';
    return el('div', { class: 'modulo' },
      el('div', { class: 'modulo-head' },
        el('div', {}, el('div', { class: 'abbr' }, s.abbreviation), el('div', { class: 'nombre' }, s.name)),
        el('div', { class: 'pct risk-' + s.risk }, s.percentText)),
      bar,
      el('div', { class: 'small' },
        'Margen: ' + num(s.marginHours) + ' h · ' + num(s.justifiedHours) + ' h justificadas · ' +
        num(s.unjustifiedHours) + ' h sin justificar'));
  }));
}

// ---------- Historial ----------

const fechaLarga = new Intl.DateTimeFormat('es-ES', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' });
const capital = (s) => s.charAt(0).toUpperCase() + s.slice(1);

function historial() {
  const filtro = $('filtro');
  filtro.replaceChildren(el('option', { value: '' }, 'Todos los módulos'),
    ...resultado.subjects.map((s) => el('option', { value: s.label }, s.abbreviation + ' · ' + s.name)));
  pintarHistorial();
  mostrar('historial');
}

function pintarHistorial() {
  const key = $('filtro').value;
  const dias = [];
  for (const dia of resultado.history) {
    const entradas = entradasDe(dia.entries, key);
    if (!entradas.length) continue;
    dias.push(el('div', { class: 'dia' },
      el('h3', {}, capital(fechaLarga.format(new Date(dia.date + 'T00:00:00Z')))),
      ...entradas.map((e) => el('div', { class: 'entrada' },
        el('span', { class: 'small' }, e.tramo),
        el('span', {}, e.label + ' · ' + num(e.hours) + ' h'),
        el('span', { class: e.justified ? 'just-si' : 'just-no' }, (e.late ? 'Retraso · ' : '') + (e.justified ? 'Justificada' : 'Sin justificar'))))));
  }
  $('dias').replaceChildren(...(dias.length ? dias : [el('p', { class: 'small' }, 'No hay faltas que mostrar.')]));
}

// ---------- Arranque ----------

$('etapa').addEventListener('change', () => rellenar(''));
$('c-etapa').addEventListener('change', () => rellenar('c-'));
$('vista-datos').addEventListener('submit', (e) => {
  e.preventDefault();
  const f = calcular('');
  if (f) error('No se han podido calcular las faltas: ' + f);
  else error('');
});
$('vista-consulta').addEventListener('submit', (e) => { e.preventDefault(); consultar(); });
$('cambiar').addEventListener('click', nueva);
$('historial-btn').addEventListener('click', historial);
$('volver').addEventListener('click', () => mostrar('resultados'));
$('filtro').addEventListener('change', pintarHistorial);

const m = /^#importar=(.*)$/.exec(location.hash);
if (m) importar(m[1]); else bienvenida();
