import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DOMParser } from 'linkedom';
import { readSchedule, readAbsences, readCalendar, tramoHours, toIsoDate } from '../readers.mjs';

const fixture = (name) => readFileSync(new URL(`./fixtures/${name}`, import.meta.url), 'utf8');
const doc = (name) => new DOMParser().parseFromString(fixture(name), 'text/html');

test('tramoHours and toIsoDate match the APK helpers', () => {
  assert.equal(tramoHours('8:15 - 9:15'), 1);
  assert.equal(tramoHours('11:15 - 11:45'), 0.5);
  assert.throws(() => tramoHours('Día completo'), /tramo horario no reconocido/);
  assert.equal(toIsoDate('5/9/2026'), '2026-09-05');
  assert.equal(toIsoDate('05-09-2026'), '2026-09-05');
  assert.throws(() => toIsoDate('ayer'), /fecha no reconocida/);
});

test('readSchedule maps abbreviations to subject names per weekday', () => {
  assert.deepEqual(readSchedule(doc('horario.html')), [
    { subject: 'Servicios de red e Internet', abbr: 'SRI', day: 1, hours: 1 },
    { subject: 'Implantación de aplicaciones web', abbr: 'IAW', day: 2, hours: 1 },
    { subject: 'Servicios de red e Internet', abbr: 'SRI', day: 4, hours: 1 },
    { subject: 'Servicios de red e Internet', abbr: 'SRI', day: 1, hours: 1 },
    { subject: 'Administración de sistemas gestores de bases de datos', abbr: 'ASGBD', day: 2, hours: 1 },
    { subject: 'Implantación de aplicaciones web', abbr: 'IAW', day: 3, hours: 1 },
    { subject: 'Administración de sistemas gestores de bases de datos', abbr: 'ASGBD', day: 1, hours: 0.5 },
  ]);
});

test('readSchedule fails on a page without the timetable', () => {
  assert.throws(() => readSchedule(doc('faltas-vacia.html')), /tablas de horario y materias/);
});

test('readAbsences reads rows, flags late arrivals and sets aside unreadable rows', () => {
  const read = readAbsences(doc('faltas.html'));
  assert.deepEqual(read.absences, [
    { date: '2026-10-13', subject: 'Servicios de red e Internet', hours: 1, justified: false, tramo: '8:15 - 9:15' },
    { date: '2026-10-13', subject: 'Servicios de red e Internet', hours: 1, justified: true, tramo: '9:15 - 10:15' },
    { date: '2026-10-14', subject: 'Implantación de aplicaciones web', hours: 1, justified: false, tramo: '8:15 - 9:15', late: true },
    { date: '2026-10-20', subject: '', fullDay: true, justified: true, tramo: 'Día completo' },
  ]);
  assert.equal(read.skipped.length, 1);
  assert.match(read.skipped[0], /^ayer \| 8:15 - 9:15 \| Implantación de aplicaciones web \| Injustificada: fecha no reconocida/);
});

test('readAbsences returns nothing for an empty list', () => {
  assert.deepEqual(readAbsences(doc('faltas-vacia.html')), { absences: [], skipped: [] });
});

test('readAbsences fails when the table is paginated', () => {
  const html = fixture('faltas.html').replace('Registros: 5', 'Registros: 40');
  assert.throws(() => readAbsences(new DOMParser().parseFromString(html, 'text/html')), /¿está paginada\?/);
});

test('readCalendar: weekdays from the latest September start, minus holidays', () => {
  const days = readCalendar(fixture('calendario.html'));
  const byDate = Object.fromEntries(days.map((d) => [d.date, d.lectivo]));
  assert.equal(days[0].date, '2026-09-01');
  assert.equal(days[days.length - 1].date, '2027-08-31');
  assert.equal(days.length, 365);
  assert.equal(byDate['2026-09-14'], false); // before the latest September start (FP rule)
  assert.equal(byDate['2026-09-15'], true);
  assert.equal(byDate['2026-09-19'], false); // Saturday
  assert.equal(byDate['2026-10-12'], false); // festivoAndalucia
  assert.equal(byDate['2026-10-13'], false); // festivoLocalidad
  assert.equal(byDate['2026-12-07'], false); // festivoProvincial
  assert.equal(byDate['2026-10-14'], true);
});

test('readCalendar fails without calendar data or September start', () => {
  assert.throws(() => readCalendar('<html></html>'), /datos del calendario/);
  assert.throws(() => readCalendar(fixture('calendario.html').replace(/primerDiaLectivo/g, 'otro')), /primer día lectivo/);
});

test('readSchedule reads block-level cell content as separate lines, like innerText', () => {
  const html = `<html><body><table>
    <tr><th></th><th>Lunes</th><th>Martes</th></tr>
    <tr><td>8:15 - 9:15</td><td><div>SRI</div><div>Aula 12</div></td><td>SRI
   <span>Aula 12</span></td></tr>
  </table><table>
    <tr><th>Materias</th><th></th></tr>
    <tr><td>SRI</td><td>Servicios de red e Internet</td></tr>
  </table></body></html>`;
  // The Martes cell is one inline line ("SRI Aula 12"), so it does not match, as in the APK.
  assert.deepEqual(readSchedule(new DOMParser().parseFromString(html, 'text/html')), [
    { subject: 'Servicios de red e Internet', abbr: 'SRI', day: 1, hours: 1 },
  ]);
});

test('readAbsences ignores rows of a table nested inside a cell', () => {
  const nested = '<td><table><tr><td>1/1/2026</td><td>8:15 - 9:15</td><td>X</td><td>Y</td></tr></table></td>';
  const html = fixture('faltas.html').replace(
    '<td>20/10/2026</td><td>Día completo</td><td></td>',
    '<td>20/10/2026</td><td>Día completo</td>' + nested,
  );
  const read = readAbsences(new DOMParser().parseFromString(html, 'text/html'));
  assert.deepEqual(read, readAbsences(doc('faltas.html')));
  assert.equal(read.absences.length, 4);
  assert.equal(read.skipped.length, 1);
});

test('readAbsences reads the Registros count from block elements, not glued digits', () => {
  const html = fixture('faltas.html').replace('<p>Registros: 5</p>', '<section>Registros: 5</section><section>0</section>');
  const read = readAbsences(new DOMParser().parseFromString(html, 'text/html'));
  assert.equal(read.absences.length, 4);
  assert.equal(read.skipped.length, 1);
});

test('readAbsences ignores script and style text in the Registros count', () => {
  const html = fixture('faltas.html')
    .replace('<body>', '<body><script>var s = "Registros: 99";</script><style>p::after{content:"Registros: 7"}</style>');
  const read = readAbsences(new DOMParser().parseFromString(html, 'text/html'));
  assert.equal(read.absences.length, 4);
  assert.equal(read.skipped.length, 1);
});

test('readSchedule treats every HTML block element as a line break', () => {
  const html = `<html><body><table>
    <tr><th></th><th>Lunes</th></tr>
    <tr><td>8:15 - 9:15</td><td><section>SRI</section><section>Aula 12</section></td></tr>
  </table><table>
    <tr><th>Materias</th><th></th></tr>
    <tr><td>SRI</td><td>Servicios de red e Internet</td></tr>
  </table></body></html>`;
  assert.deepEqual(readSchedule(new DOMParser().parseFromString(html, 'text/html')), [
    { subject: 'Servicios de red e Internet', abbr: 'SRI', day: 1, hours: 1 },
  ]);
});
