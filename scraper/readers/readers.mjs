// Lectores de las páginas de Séneca (PASEN), compartidos por la web (sobre el HTML que devuelve el
// Worker) y, más adelante, por el APK. No navegan ni esperan: reciben la página ya cargada.
// Devuelven el JSON en bruto que entiende ScrapeParsers (shared/).

const DAYS = ['LUNES', 'MARTES', 'MIÉRCOLES', 'JUEVES', 'VIERNES', 'SÁBADO', 'DOMINGO'];

// Aproximación de innerText (sin maquetación): <br> y los bordes de bloque cuentan como salto de
// línea; el resto de espacios (también los saltos de línea del código fuente) colapsan a un espacio.
const BLOCKS = new Set(['DIV', 'P', 'LI', 'TR', 'TABLE', 'UL', 'OL', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6']);
const SKIPPED = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT']);

function flatText(node) {
  if (node.nodeType === 3) return node.textContent.replace(/\s+/g, ' ');
  if (node.nodeType !== 1) return '';
  const tag = node.tagName.toUpperCase();
  if (SKIPPED.has(tag)) return '';
  if (tag === 'BR') return '\n';
  const inner = Array.from(node.childNodes).map(flatText).join('');
  return BLOCKS.has(tag) ? '\n' + inner + '\n' : inner;
}

function cellLines(cell) {
  return flatText(cell).split('\n').map((line) => line.replace(/\s+/g, ' ').trim()).filter(Boolean);
}

function cellText(cell) {
  return cell.textContent.replace(/\s+/g, ' ').trim();
}

// Solo las filas propias de la tabla: las de una tabla anidada en una celda no cuentan (como table.rows).
function rows(table) {
  return Array.from(table.querySelectorAll('tr')).filter((tr) => {
    let p = tr.parentNode;
    while (p && p.tagName !== 'TABLE') p = p.parentNode;
    return p === table;
  });
}

function cells(row) {
  return Array.from(row.children).filter((c) => c.tagName === 'TD' || c.tagName === 'TH');
}

// "8:15 - 9:15" -> 1, "11:15 - 11:45" -> 0.5
export function tramoHours(text) {
  const m = /(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})/.exec(String(text));
  if (!m) throw new Error('tramo horario no reconocido: ' + text);
  const minutes = (Number(m[3]) * 60 + Number(m[4])) - (Number(m[1]) * 60 + Number(m[2]));
  if (minutes <= 0) throw new Error('tramo horario no válido: ' + text);
  return Math.round(minutes / 60 * 100) / 100;
}

// "5/9/2026" o "05-09-2026" -> "2026-09-05"
export function toIsoDate(text) {
  const m = /^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$/.exec(String(text).trim());
  if (!m) throw new Error('fecha no reconocida: ' + text);
  return m[3] + '-' + m[2].padStart(2, '0') + '-' + m[1].padStart(2, '0');
}

// Tutoría > Horario: la rejilla trae abreviaturas; la tabla "Materias" da el nombre completo, que es
// el que usa la página de faltas. Las celdas cuya primera línea no es una materia se ignoran.
export function readSchedule(doc) {
  const tables = Array.from(doc.querySelectorAll('table'));
  const firstRowText = (t) => (rows(t)[0] ? cellText(rows(t)[0]) : '');
  const grid = tables.find((t) => /lunes/i.test(firstRowText(t)));
  const subjects = tables.find((t) => /materias/i.test(firstRowText(t)));
  if (!grid || !subjects) throw new Error('no se encuentran las tablas de horario y materias');

  const names = {};
  rows(subjects).forEach((row) => {
    const c = cells(row);
    if (c.length < 2) return;
    const abbr = cellText(c[0]);
    const name = cellText(c[1]);
    if (abbr && name && !/^abreviatura$/i.test(abbr)) names[abbr] = name;
  });

  const columnDay = cells(rows(grid)[0]).map((cell) => DAYS.indexOf(cellText(cell).toUpperCase()) + 1);
  const slots = [];
  rows(grid).slice(1).forEach((row) => {
    const c = cells(row);
    if (!c.length) return;
    const found = [];
    c.slice(1).forEach((cell, i) => {
      const day = columnDay[i + 1];
      const abbr = cellLines(cell)[0] || '';
      if (day > 0 && names[abbr]) found.push({ subject: names[abbr], abbr, day });
    });
    // Filas sin clases (p. ej. "RECREO") pueden no tener tramo: se saltan. Una fila con clases sí lo
    // necesita (tramoHours lanza), así que sus horas nunca se pierden en silencio.
    if (!found.length) return;
    const hours = tramoHours(cellText(c[0]));
    found.forEach((slot) => slots.push({ subject: slot.subject, abbr: slot.abbr, day: slot.day, hours }));
  });
  if (!slots.length) throw new Error('horario vacío o con un formato desconocido');
  return slots;
}

// Faltas de asistencia > Consulta, filtro "Todas": Fecha | Tramo horario | Ausencia de | Estado.
// Los "Retraso" no son faltas. Una fila ilegible se aparta en `skipped`; una tabla cambiada (columnas,
// filtro, número de filas) hace fallar la lectura porque entonces todas las cifras serían erróneas.
export function readAbsences(doc) {
  const bodyText = doc.body ? flatText(doc.body).replace(/\s+/g, ' ').trim() : '';
  const table = Array.from(doc.querySelectorAll('table')).find((t) => {
    const first = rows(t)[0];
    return first && /fecha/i.test(cellText(first)) && /estado/i.test(cellText(first));
  });
  if (!table) {
    if (/registros:\s*0\b/i.test(bodyText)) return { absences: [], skipped: [] };
    throw new Error('no se encuentra la tabla de faltas');
  }
  const filter = doc.getElementById('C_TIPFAL');
  if (filter && filter.value !== 'T') throw new Error('el filtro de faltas no está en "Todas"');

  const header = cells(rows(table)[0]).map((c) => cellText(c).toLowerCase());
  const col = {
    date: header.indexOf('fecha'),
    tramo: header.indexOf('tramo horario'),
    subject: header.indexOf('ausencia de'),
    state: header.indexOf('estado'),
  };
  if (Object.values(col).some((i) => i < 0)) throw new Error('columnas de faltas desconocidas: ' + header.join(', '));

  let rowsRead = 0;
  const absences = [];
  const skipped = [];
  rows(table).slice(1).forEach((row) => {
    const c = cells(row);
    if (c.length < header.length) return;
    rowsRead++;
    const text = (i) => cellText(c[i]);
    const state = text(col.state).toLowerCase();
    if (state.indexOf('retraso') >= 0) return;
    const tramo = text(col.tramo);
    try {
      // "Día completo" no lleva materia; la app la reparte por el horario de ese día.
      if (/^d[ií]a completo$/i.test(tramo)) {
        absences.push({ date: toIsoDate(text(col.date)), subject: '', fullDay: true, justified: state.indexOf('justificada') === 0, tramo });
        return;
      }
      absences.push({
        date: toIsoDate(text(col.date)),
        subject: text(col.subject),
        hours: tramoHours(tramo),
        justified: state.indexOf('justificada') === 0,
        tramo,
      });
    } catch (e) {
      skipped.push([text(col.date), tramo, text(col.subject), text(col.state)].join(' | ') + ': ' + (e && e.message ? e.message : e));
    }
  });
  const total = /registros:\s*(\d+)/i.exec(bodyText);
  if (total && Number(total[1]) !== rowsRead) {
    throw new Error('la tabla de faltas muestra ' + rowsRead + ' de ' + total[1] + ' registros (¿está paginada?)');
  }
  return { absences, skipped };
}

// Centro > Calendario escolar: Séneca trae los días especiales como JSON en un script
// (top.datosCalendario). Lectivo = lunes a viernes, no festivo*, desde el último "primer día lectivo"
// de septiembre (la FP empieza la última), igual que el APK con las cuadrículas.
export function readCalendar(html) {
  const m = /datosCalendario\s*=\s*jQuery\.parseJSON\('([\s\S]*?)'\)\s*;/.exec(String(html));
  if (!m) throw new Error('no se encuentran los datos del calendario');
  let entries;
  try {
    entries = JSON.parse(m[1].replace(/\\'/g, "'"));
  } catch (e) {
    throw new Error('datos del calendario ilegibles');
  }
  const holidays = new Set(entries.filter((e) => /festivo/i.test(e.className || '')).map((e) => e.start));
  const septemberStarts = entries
    .filter((e) => /primerDiaLectivo/.test(e.className || '') && /^\d{4}-09-/.test(e.start || ''))
    .map((e) => e.start)
    .sort();
  if (!septemberStarts.length) throw new Error('el calendario no marca el primer día lectivo');
  const courseStart = septemberStarts[septemberStarts.length - 1];
  const year = Number(courseStart.slice(0, 4));

  const days = [];
  for (let d = new Date(Date.UTC(year, 8, 1)); d < new Date(Date.UTC(year + 1, 8, 1)); d.setUTCDate(d.getUTCDate() + 1)) {
    const date = d.toISOString().slice(0, 10);
    const weekday = d.getUTCDay(); // 0 = domingo
    const lectivo = weekday !== 0 && weekday !== 6 && !holidays.has(date) && date >= courseStart;
    days.push({ date, lectivo });
  }
  return days;
}
