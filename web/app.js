// Faltas iPasen (web). Sin servidor y sin almacenamiento: los datos llegan en el fragmento de la URL,
// viven solo en memoria y desaparecen al recargar. Nada de iPasen se inserta como HTML.
import { FaltasWeb } from './shared/shared.mjs';
import { unpack } from './lib/transfer.mjs';

const $ = (id) => document.getElementById(id);
const VISTAS = ['bienvenida', 'datos', 'resultados', 'historial'];
const INVALID = 'Los datos del marcador no son válidos. Vuelve a pulsarlo en iPasen.';

const hoy = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Madrid' }).format(new Date());
const raw = (d) => JSON.stringify({ ok: true, data: d });

let datos = null;   // { schedule, calendar, absences }, solo en memoria
let niveles = [];
let resultado = null;

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

function formulario() {
  error('');
  if (!niveles.length) {
    niveles = JSON.parse(FaltasWeb.niveles(hoy));
    for (const n of niveles) $('etapa').append(el('option', { value: n.id }, n.label));
    $('etapa').value = niveles.some((n) => n.id === 'FP_SUPERIOR') ? 'FP_SUPERIOR' : niveles[0].id;
    rellenar();
  }
  mostrar('datos');
}

function rellenar() {
  const n = niveles.find((x) => x.id === $('etapa').value);
  $('umbral').value = n.threshold;
  $('fin').value = n.cutoff;
  $('fin-label').textContent = n.cutoffLabel;
}

// ---------- Resultados ----------

const num = (x) => String(Math.round(x * 100) / 100).replace('.', ',');

function calcular() {
  error('');
  try {
    resultado = JSON.parse(FaltasWeb.calcular(
      raw(datos.schedule), raw(datos.calendar), raw(datos.absences),
      $('fin').value, hoy, Number($('umbral').value)));
  } catch (e) {
    error('No se han podido calcular las faltas: ' + (e && e.message ? e.message : e));
    return;
  }
  pintarResultados();
  mostrar('resultados');
}

function pintarResultados() {
  const r = resultado;
  const umbral = Number($('umbral').value);
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
    const entradas = dia.entries.filter((e) => !key || e.label === key);
    if (!entradas.length) continue;
    dias.push(el('div', { class: 'dia' },
      el('h3', {}, capital(fechaLarga.format(new Date(dia.date + 'T00:00:00Z')))),
      ...entradas.map((e) => el('div', { class: 'entrada' },
        el('span', { class: 'small' }, e.tramo),
        el('span', {}, e.label + ' · ' + num(e.hours) + ' h'),
        el('span', { class: e.justified ? 'just-si' : 'just-no' }, e.justified ? 'Justificada' : 'Sin justificar')))));
  }
  $('dias').replaceChildren(...(dias.length ? dias : [el('p', { class: 'small' }, 'No hay faltas que mostrar.')]));
}

// ---------- Arranque ----------

$('etapa').addEventListener('change', rellenar);
$('vista-datos').addEventListener('submit', (e) => { e.preventDefault(); calcular(); });
$('cambiar').addEventListener('click', formulario);
$('historial-btn').addEventListener('click', historial);
$('volver').addEventListener('click', () => mostrar('resultados'));
$('filtro').addEventListener('change', pintarHistorial);

const m = /^#importar=(.*)$/.exec(location.hash);
if (m) importar(m[1]); else bienvenida();
