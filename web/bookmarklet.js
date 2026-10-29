// Se ejecuta dentro de Séneca (mismo origen), insertado por el marcador "Faltas iPasen".
// Lee horario, calendario y faltas con la sesión abierta del usuario y abre la web con los datos
// en el fragmento de la URL (nunca salen a ningún servidor).
import { readSchedule, readAbsences, readCalendar } from './readers/readers.mjs';
import { SECTIONS, findEntry, menuLinks, menuPath } from './lib/menu.mjs';
import { pack } from './lib/transfer.mjs';

const BASE = new URL('./', import.meta.url).href;
const parse = (h) => new DOMParser().parseFromString(h, 'text/html');

function overlay() {
  const old = document.getElementById('faltas-ipasen-capa');
  if (old) old.remove();
  const box = document.createElement('div');
  box.id = 'faltas-ipasen-capa';
  box.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:2147483647;background:#3949AB;color:#fff;' +
    'font:15px/1.4 system-ui,sans-serif;padding:12px 48px 12px 16px;box-shadow:0 2px 8px rgba(0,0,0,.3)';
  const text = document.createElement('div');
  const close = document.createElement('button');
  close.textContent = '×';
  close.setAttribute('aria-label', 'Cerrar');
  close.style.cssText = 'position:absolute;top:6px;right:8px;width:36px;height:36px;border:0;background:transparent;color:#fff;font-size:26px;cursor:pointer';
  close.onclick = () => box.remove();
  box.append(text, close);
  document.body.appendChild(box);
  return { set: (t, err) => { text.textContent = t; box.style.background = err ? '#B71C1C' : '#3949AB'; } };
}

async function fetchSection(a) {
  const res = await fetch(new URL(a.dataset.menulink, location.href), {
    method: 'POST',
    credentials: 'include',
    body: new URLSearchParams({ COD_PAGINA_ANTERIOR: '', TIEMPO_PAGINA_ANTERIOR: '' }),
  });
  if (!res.ok) throw new Error('iPasen respondió ' + res.status + ' (¿sesión caducada?)');
  return new TextDecoder('iso-8859-1').decode(await res.arrayBuffer());
}

export async function leer(links, ui) {
  const data = { v: 1 };
  const plan = [
    ['schedule', 'Leyendo horario…', (h) => readSchedule(parse(h))],
    ['calendar', 'Leyendo calendario…', (h) => readCalendar(h)],
    ['absences', 'Leyendo faltas…', (h) => readAbsences(parse(h))],
  ];
  for (const [key, label, read] of plan) {
    ui.step = label;
    ui.set(label);
    const entry = findEntry(links, SECTIONS[key].paths);
    if (!entry) {
      const available = links.map((a) => menuPath(a).join(' > ')).join('; ');
      throw new Error('tu menú de iPasen no tiene «' + SECTIONS[key].label + '». Opciones: ' +
        (available.length > 400 ? available.slice(0, 400) + '…' : available));
    }
    data[key] = read(await fetchSection(entry));
  }
  return data;
}

async function main() {
  if (location.hostname !== 'seneca.juntadeandalucia.es' || !document.getElementById('menupasen')) {
    alert('Abre iPasen e inicia sesión, y pulsa el marcador otra vez.');
    return;
  }
  const ui = overlay();
  ui.step = '';
  try {
    const data = await leer(menuLinks(document), ui);
    ui.step = 'Preparando los datos…';
    ui.set(ui.step);
    const packed = await pack(data);
    ui.set('Abriendo Faltas iPasen…');
    location.href = BASE + '#importar=' + packed;
  } catch (e) {
    ui.set(ui.step + ' ' + (e && e.message ? e.message : e) +
      ' Si la sesión ha caducado, vuelve a entrar en iPasen y pulsa el marcador otra vez.', true);
  }
}

main();
