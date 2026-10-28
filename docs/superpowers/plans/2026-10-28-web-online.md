# Web online (login en la web + Cloudflare Worker) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Una web en GitHub Pages que, cada vez que se abre, pide etapa, umbral, fecha de fin, usuario y clave de iPasen, obtiene los datos a través de un Cloudflare Worker y muestra el Resumen y el Historial calculados con el mismo código Kotlin que el APK.

**Architecture:** `worker/` (Cloudflare Worker, JS sin dependencias) hace login en Séneca, abre por `POST` las tres secciones del menú y devuelve su HTML. `scraper/readers/readers.mjs` (JS puro, funciona con cualquier `Document`) convierte ese HTML en el JSON en bruto que ya entiende `ScrapeParsers`. `shared/` exporta a JS una fachada (`FaltasWeb`) que llama a `ScrapeParsers`, `AbsenceCalculator`, `HistoryBuilder` y `RiskLevel`. `web/` es HTML + JS sin framework; GitHub Actions compila `shared` a un módulo ES y lo publica con la web.

**Tech Stack:** Kotlin 2.2.20 Multiplatform (target js con `browser()` + `useEsModules()`), JS (ES2022, módulos ES), Cloudflare Workers + Wrangler 4, `node:test` + `linkedom` para tests de los readers, GitHub Actions + Pages.

**Spec:** `docs/superpowers/specs/2026-10-28-version-web-design.md` — la sección "Cambio de decisión (2026-10-28, tarde)" manda sobre el resto.

## Global Constraints

- **Nada se guarda en el navegador** (ni `localStorage`, ni `sessionStorage`, ni IndexedDB, ni cookies propias). Cada apertura pide todo de nuevo.
- **El Worker no guarda ni registra** credenciales ni HTML (`console.log` prohibido con datos de usuario). Solo habla con `https://seneca.juntadeandalucia.es`.
- **CORS** del Worker: solo `https://vklf-official.github.io` y `http://localhost:8000`.
- **Repo público**: los fixtures de tests usan datos inventados; nunca datos reales del usuario. Nunca usar credenciales reales en pruebas automáticas; para probar el login fallido usar el usuario `usuario-que-no-existe-0000`.
- El APK no cambia en este plan.
- Textos de la web en español; aspecto igual que el APK (índigo `#3949AB`, Inter, claro/oscuro con `prefers-color-scheme`); aviso "No es una app oficial ni está relacionada con la Junta de Andalucía".
- Mismo cálculo que el APK: todo número que se muestra sale de `shared` (nada de recalcular % en JS).
- Kotlin: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` antes de `./gradlew`.
- Commits en `feat/app-faltas` terminando con `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. No commitear `graphify-out/`, `.superpowers/`, `.impeccable/`, `node_modules/`, `web/shared/`, `web/readers/`.

### Contratos JSON (los usan varias tareas)

Salida de los readers = el campo `data` que `ScrapeParsers` espera:

- `readSchedule(doc)` → `[{ "subject": "Servicios de red e Internet", "abbr": "SRI", "day": 1, "hours": 1 }, …]` (`day` 1 = lunes … 7 = domingo).
- `readCalendar(html)` → `[{ "date": "2026-09-15", "lectivo": true }, …]` (todos los días del 1-sep al 31-ago del curso).
- `readAbsences(doc)` → `{ "absences": [{ "date": "2026-10-13", "subject": "Servicios de red e Internet", "hours": 1, "justified": false, "tramo": "8:15 - 9:15" } | { "date": "…", "subject": "", "fullDay": true, "justified": false, "tramo": "Día completo" }], "skipped": ["…"] }`.

La web envuelve cada uno como `JSON.stringify({ ok: true, data })` antes de pasarlo a `FaltasWeb.calcular`.

Respuesta del Worker `POST /sync` (`Content-Type: application/json`, cuerpo `{ "usuario": "…", "clave": "…" }`):

- Éxito `200`: `{ "ok": true, "schedule": "<html>", "calendar": "<html>", "absences": "<html>" }`.
- Error `4xx/5xx`: `{ "ok": false, "code": "LOGIN_FAILED" | "LOCKED" | "MENU_NOT_FOUND" | "UPSTREAM_ERROR" | "BAD_REQUEST" | "RATE_LIMITED", "message": "texto para el usuario" }`.

---

### Task 1: Readers compartidos (`scraper/readers/readers.mjs`)

Porta la lectura de `app/src/main/assets/scraper/{schedule,absences,calendar}.js` a funciones puras sobre un `Document` (o el HTML, para el calendario), sin navegar ni esperar.

**Files:**
- Create: `scraper/readers/readers.mjs`
- Create: `scraper/readers/package.json`
- Create: `scraper/readers/test/readers.test.mjs`
- Create: `scraper/readers/test/fixtures/{horario.html,faltas.html,faltas-vacia.html,calendario.html}`
- Modify: `.gitignore` (añadir `node_modules/`)

**Interfaces:**
- Produces: `export function readSchedule(doc: Document): Slot[]`, `export function readAbsences(doc: Document): { absences, skipped }`, `export function readCalendar(html: string): Day[]`, `export function tramoHours(text: string): number`, `export function toIsoDate(text: string): string`. Errores: `throw new Error(<mensaje en español>)` con los mismos textos que los scripts del APK.

- [ ] **Step 1: Preparar el paquete de tests**

`scraper/readers/package.json`:

```json
{
  "name": "faltas-ipasen-readers",
  "private": true,
  "type": "module",
  "scripts": { "test": "node --test test/" },
  "devDependencies": { "linkedom": "0.18.12" }
}
```

```bash
cd /c/Users/jesus/faltas-ipasen-app/scraper/readers && npm install
```

Añadir `node_modules/` a `.gitignore` (raíz). Commitear `package-lock.json`.

- [ ] **Step 2: Escribir los fixtures (datos inventados, estructura real)**

`test/fixtures/horario.html` — página con dos tablas, como la de "Tutoría > Horario":

```html
<html><body>
<table>
  <tr><th></th><th>Lunes</th><th>Martes</th><th>Miércoles</th><th>Jueves</th><th>Viernes</th></tr>
  <tr><td>8:15 - 9:15</td><td>SRI<br>Aula 12</td><td>IAW<br>Aula 3</td><td></td><td>SRI<br>Aula 12</td><td>TUT<br>Aula 1</td></tr>
  <tr><td>9:15 - 10:15</td><td>SRI<br>Aula 12</td><td>ASGBD<br>Lab 2</td><td>IAW<br>Aula 3</td><td></td><td></td></tr>
  <tr><td>RECREO</td><td></td><td></td><td></td><td></td><td></td></tr>
  <tr><td>11:15 - 11:45</td><td>ASGBD<br>Lab 2</td><td></td><td></td><td></td><td></td></tr>
</table>
<table>
  <tr><th>Materias</th><th></th></tr>
  <tr><td>Abreviatura</td><td>Nombre</td></tr>
  <tr><td>SRI</td><td>Servicios de red e Internet</td></tr>
  <tr><td>IAW</td><td>Implantación de aplicaciones web</td></tr>
  <tr><td>ASGBD</td><td>Administración de sistemas gestores de bases de datos</td></tr>
</table>
</body></html>
```

(`TUT` no está en Materias → se ignora, como en el APK.)

`test/fixtures/faltas.html`:

```html
<html><body>
<select id="C_TIPFAL"><option value="T" selected>Todas</option></select>
<table>
  <tr><th>Fecha</th><th>Tramo horario</th><th>Ausencia de</th><th>Estado</th></tr>
  <tr><td>13/10/2026</td><td>8:15 - 9:15</td><td>Servicios de red e Internet</td><td>Injustificada</td></tr>
  <tr><td>13/10/2026</td><td>9:15 - 10:15</td><td>Servicios de red e Internet</td><td>Justificada</td></tr>
  <tr><td>14/10/2026</td><td>8:15 - 9:15</td><td>Implantación de aplicaciones web</td><td>Retraso</td></tr>
  <tr><td>20/10/2026</td><td>Día completo</td><td></td><td>Justificada</td></tr>
  <tr><td>ayer</td><td>8:15 - 9:15</td><td>Implantación de aplicaciones web</td><td>Injustificada</td></tr>
</table>
<p>Registros: 5</p>
</body></html>
```

`test/fixtures/faltas-vacia.html`: `<html><body><p>Registros: 0</p></body></html>`

`test/fixtures/calendario.html` — el script real de Séneca con datos de muestra (las entradas de inicio y fin vienen duplicadas con las dos clases, como en Séneca):

```html
<html><head><script>
try{
top.datosCalendario = jQuery.parseJSON('[{"title":"Día de la Hispanidad","start":"2026-10-12","className":"festivoAndalucia"},{"title":"Fiesta local","start":"2026-10-13","className":"festivoLocalidad"},{"title":"Festivo provincial","start":"2026-12-07","className":"festivoProvincial"},{"title":"Primer día lectivo:Primer Ciclo","start":"2026-09-03","className":"primerDiaLectivo"},{"title":"Primer día lectivo:Primer Ciclo","start":"2026-09-03","className":"ultimoDiaLectivo"},{"title":"Primer día lectivo:ESO, Bachillerato, Ciclos","start":"2026-09-15","className":"primerDiaLectivo"},{"title":"Primer día lectivo:ESO, Bachillerato, Ciclos","start":"2026-09-15","className":"ultimoDiaLectivo"},{"title":"Último día lectivo:ESO, Bachillerato, Ciclos","start":"2027-06-22","className":"primerDiaLectivo"},{"title":"Último día lectivo:ESO, Bachillerato, Ciclos","start":"2027-06-22","className":"ultimoDiaLectivo"}]');
}catch(e){Navegacion.setError(e);}
</script></head><body></body></html>
```

- [ ] **Step 3: Escribir los tests (fallan)**

`test/readers.test.mjs`:

```js
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

test('readAbsences reads rows, skips late arrivals and sets aside unreadable rows', () => {
  const read = readAbsences(doc('faltas.html'));
  assert.deepEqual(read.absences, [
    { date: '2026-10-13', subject: 'Servicios de red e Internet', hours: 1, justified: false, tramo: '8:15 - 9:15' },
    { date: '2026-10-13', subject: 'Servicios de red e Internet', hours: 1, justified: true, tramo: '9:15 - 10:15' },
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
```

Run: `cd /c/Users/jesus/faltas-ipasen-app/scraper/readers && npm test`
Expected: FAIL (`Cannot find module '../readers.mjs'`).

- [ ] **Step 4: Implementar `readers.mjs`**

```js
// Lectores de las páginas de Séneca (PASEN), compartidos por la web (sobre el HTML que devuelve el
// Worker) y, más adelante, por el APK. No navegan ni esperan: reciben la página ya cargada.
// Devuelven el JSON en bruto que entiende ScrapeParsers (shared/).

const DAYS = ['LUNES', 'MARTES', 'MIÉRCOLES', 'JUEVES', 'VIERNES', 'SÁBADO', 'DOMINGO'];

// innerText no existe sin maquetación: <br> y saltos cuentan como línea, el resto se normaliza.
function cellLines(cell) {
  const copy = cell.cloneNode(true);
  copy.querySelectorAll('br').forEach((br) => br.replaceWith('\n'));
  return copy.textContent.split('\n').map((line) => line.replace(/\s+/g, ' ').trim()).filter(Boolean);
}

function cellText(cell) {
  return cell.textContent.replace(/\s+/g, ' ').trim();
}

function rows(table) {
  return Array.from(table.querySelectorAll('tr'));
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
  const bodyText = doc.body ? cellText(doc.body) : '';
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
```

- [ ] **Step 5: Ejecutar los tests**

Run: `cd /c/Users/jesus/faltas-ipasen-app/scraper/readers && npm test`
Expected: todos los tests en verde (`# fail 0`).

- [ ] **Step 6: Commit**

```bash
cd /c/Users/jesus/faltas-ipasen-app
git add .gitignore scraper/readers/readers.mjs scraper/readers/package.json scraper/readers/package-lock.json scraper/readers/test
git commit -m "feat: add shared Séneca page readers for the web

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Fachada JS de `shared` y compilación para el navegador

**Files:**
- Modify: `shared/build.gradle.kts` (target js: `browser()` sin tests de navegador, `useEsModules()`, nombre de módulo `shared`)
- Create: `shared/src/jsMain/kotlin/com/faltasipasen/app/web/FaltasWeb.kt`
- Create: `shared/src/jsTest/kotlin/com/faltasipasen/app/web/FaltasWebTest.kt`

**Interfaces:**
- Consumes: `ScrapeParsers`, `AbsenceCalculator`, `HistoryBuilder`, `RiskLevel`, `EducationLevel`, `CourseDates`, `AlertFormatter` (todos en `shared/commonMain`).
- Produces (módulo ES `shared.mjs`, export `FaltasWeb`):
  - `FaltasWeb.niveles(todayIso: string): string` → JSON `[{ "id": "FP_SUPERIOR", "label": "Grado Superior", "threshold": 15.0, "cutoff": "2027-02-15", "cutoffLabel": "Fin de clases (inicio FCT)", "subjectLabel": "Módulos" }, …]` en el orden del enum.
  - `FaltasWeb.calcular(scheduleRaw, calendarRaw, absencesRaw: string, cutoffIso: string, todayIso: string, thresholdPercent: number): string` → JSON `{ "global": Stats, "subjects": [Stats], "courseStart", "cutoff", "countedUntil", "lectivoDaysElapsed", "lectivoDaysTotal", "skipped": [string], "history": [{ "date", "entries": [{ "tramo", "subjectKey", "label", "name", "hours", "justified" }] }] }` con `Stats = { "label", "abbreviation", "name", "shortLabel", "courseHours", "absentHours", "unjustifiedHours", "justifiedHours", "percent", "percentText", "marginHours", "risk": "LOW"|"MEDIUM"|"HIGH" }`. Errores de `ScrapeParsers` → lanza `Error` con el mensaje de `ScrapeException`.
  - Tarea Gradle que genera el módulo: `:shared:jsBrowserProductionLibraryDistribution` → `shared/build/dist/js/productionLibrary/shared.mjs` (si el nombre real difiere, la Task 2 lo anota y las tareas 4 y 5 lo usan).

- [ ] **Step 1: Configurar el target js**

En `shared/build.gradle.kts`, sustituir el bloque `js(IR) { … }` por:

```kotlin
    js(IR) {
        outputModuleName.set("shared")
        nodejs()
        browser {
            testTask { enabled = false }
        }
        useEsModules()
        binaries.library()
    }
```

- [ ] **Step 2: Escribir el test (falla)**

`shared/src/jsTest/kotlin/com/faltasipasen/app/web/FaltasWebTest.kt`:

```kotlin
package com.faltasipasen.app.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FaltasWebTest {
    private val schedule = """{"ok":true,"data":[{"subject":"Redes","day":1,"hours":2,"abbr":"RED"},{"subject":"Bases","day":2,"hours":1,"abbr":"BD"}]}"""
    private val calendar = """{"ok":true,"data":[{"date":"2026-09-14","lectivo":true},{"date":"2026-09-15","lectivo":true},{"date":"2026-09-21","lectivo":true}]}"""
    private val absences = """{"ok":true,"data":{"absences":[{"date":"2026-09-14","subject":"Redes","hours":1,"justified":false,"tramo":"8:15 - 9:15"}],"skipped":["x | y: fecha no reconocida"]}}"""

    @Test
    fun calcularReturnsTheSameFiguresAsTheApk() {
        val out = JSON.parse<dynamic>(FaltasWeb.calcular(schedule, calendar, absences, "2027-02-15", "2026-09-30", 15.0))
        // Redes: lunes 14 y 21 → 4 h de curso, 1 h de falta = 25 %.
        assertEquals("REDES", out.subjects[0].label)
        assertEquals(4.0, out.subjects[0].courseHours)
        assertEquals(25.0, out.subjects[0].percent)
        assertEquals("25,0 %", out.subjects[0].percentText)
        assertEquals("HIGH", out.subjects[0].risk)
        assertEquals("RED", out.subjects[0].abbreviation)
        assertEquals(5.0, out.global.courseHours)
        assertEquals(1, out.skipped.length)
        assertEquals("2026-09-14", out.history[0].date)
        assertEquals("RED", out.history[0].entries[0].label)
    }

    @Test
    fun nivelesGivesTheApkDefaults() {
        val levels = JSON.parse<Array<dynamic>>(FaltasWeb.niveles("2026-10-28"))
        val superior = levels.first { it.id == "FP_SUPERIOR" }
        assertEquals(15.0, superior.threshold)
        assertEquals("2027-02-15", superior.cutoff)
        assertTrue(levels.any { it.id == "ESO" && it.cutoff == "2027-06-22" })
    }

    @Test
    fun parserErrorsBecomeJsErrors() {
        val e = assertFailsWith<Throwable> {
            FaltasWeb.calcular("""{"ok":true,"data":[{"subject":"","day":1}]}""", calendar, absences, "2027-02-15", "2026-09-30", 15.0)
        }
        assertTrue(e.message!!.contains("asignatura vacía"))
    }
}
```

Run: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; cd /c/Users/jesus/faltas-ipasen-app && ./gradlew :shared:jsNodeTest`
Expected: FAIL de compilación (`Unresolved reference 'FaltasWeb'`).

- [ ] **Step 3: Implementar la fachada**

`shared/src/jsMain/kotlin/com/faltasipasen/app/web/FaltasWeb.kt`:

```kotlin
@file:OptIn(ExperimentalJsExport::class)

package com.faltasipasen.app.web

import com.faltasipasen.app.domain.AbsenceCalculator
import com.faltasipasen.app.domain.AlertFormatter
import com.faltasipasen.app.domain.CourseDates
import com.faltasipasen.app.domain.EducationLevel
import com.faltasipasen.app.domain.HistoryBuilder
import com.faltasipasen.app.domain.HoursStats
import com.faltasipasen.app.domain.RiskLevel
import com.faltasipasen.app.scraper.ScrapeException
import com.faltasipasen.app.scraper.ScrapeParsers
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Puerta de entrada de la web al mismo cálculo que el APK. Entradas y salidas en JSON. */
@JsExport
object FaltasWeb {
    fun niveles(todayIso: String): String {
        val today = LocalDate.parse(todayIso)
        return buildJsonArray {
            EducationLevel.entries.forEach { level ->
                addJsonObject {
                    put("id", level.name)
                    put("label", level.label)
                    put("threshold", level.defaultThreshold)
                    put("cutoff", CourseDates.defaultCutoff(today, level).toString())
                    put("cutoffLabel", level.cutoffFieldLabel)
                    put("subjectLabel", level.subjectLabel)
                }
            }
        }.toString()
    }

    fun calcular(
        scheduleRaw: String,
        calendarRaw: String,
        absencesRaw: String,
        cutoffIso: String,
        todayIso: String,
        thresholdPercent: Double,
    ): String {
        try {
            val cutoff = LocalDate.parse(cutoffIso)
            val today = LocalDate.parse(todayIso)
            val schedule = ScrapeParsers.parseSchedule(scheduleRaw)
            val calendar = ScrapeParsers.parseCalendar(calendarRaw)
            val read = ScrapeParsers.parseAbsences(absencesRaw, schedule)
            val result = AbsenceCalculator.calculate(schedule, calendar, read.absences, cutoff, today)
            val history = HistoryBuilder.build(schedule, read.absences, cutoff, today)
            return buildJsonObject {
                put("global", statsJson(result.global, thresholdPercent))
                putJsonArray("subjects") { result.subjects.forEach { add(statsJson(it, thresholdPercent)) } }
                put("courseStart", result.courseStart.toString())
                put("cutoff", result.cutoff.toString())
                put("countedUntil", result.countedUntil.toString())
                put("lectivoDaysElapsed", result.lectivoDaysElapsed)
                put("lectivoDaysTotal", result.lectivoDaysTotal)
                putJsonArray("skipped") { read.skipped.forEach { add(it) } }
                putJsonArray("history") {
                    history.forEach { day ->
                        addJsonObject {
                            put("date", day.date.toString())
                            putJsonArray("entries") {
                                day.entries.forEach { e ->
                                    addJsonObject {
                                        put("tramo", e.tramo)
                                        put("subjectKey", e.subjectKey)
                                        put("label", e.label)
                                        put("name", e.name)
                                        put("hours", e.hours)
                                        put("justified", e.justified)
                                    }
                                }
                            }
                        }
                    }
                }
            }.toString()
        } catch (e: ScrapeException) {
            throw Error(e.message ?: "error al leer iPasen")
        } catch (e: IllegalArgumentException) {
            throw Error("fecha no válida: ${e.message}")
        }
    }

    private fun statsJson(s: HoursStats, threshold: Double): JsonObject = buildJsonObject {
        put("label", s.label)
        put("abbreviation", s.abbreviation)
        put("name", s.name)
        put("shortLabel", s.shortLabel)
        put("courseHours", s.courseHours)
        put("absentHours", s.absentHours)
        put("unjustifiedHours", s.unjustifiedHours)
        put("justifiedHours", s.justifiedHours)
        put("percent", s.percent)
        put("percentText", AlertFormatter.percent(s.percent))
        put("marginHours", s.marginHours(threshold))
        put("risk", RiskLevel.of(s.percent, threshold).name)
    }
}
```

- [ ] **Step 4: Ejecutar tests y generar el módulo**

Run:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; cd /c/Users/jesus/faltas-ipasen-app && ./gradlew :shared:allTests :app:testDebugUnitTest :shared:jsBrowserProductionLibraryDistribution && ls shared/build/dist/js/productionLibrary/
```

Expected: BUILD SUCCESSFUL y existe `shared/build/dist/js/productionLibrary/shared.mjs`. Si la tarea no existe con ese nombre, buscar la equivalente con `./gradlew :shared:tasks --all | grep -i library`, usarla y anotarla en el informe.

- [ ] **Step 5: Comprobar el módulo desde Node**

```bash
cd /c/Users/jesus/faltas-ipasen-app && node --input-type=module -e "import { FaltasWeb } from './shared/build/dist/js/productionLibrary/shared.mjs'; console.log(JSON.parse(FaltasWeb.niveles('2026-10-28')).length)"
```

Expected: `7`.

- [ ] **Step 6: Commit**

```bash
cd /c/Users/jesus/faltas-ipasen-app
git add shared/build.gradle.kts shared/src/jsMain shared/src/jsTest kotlin-js-store
git commit -m "feat: export the shared calculation to the browser

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Cloudflare Worker (`worker/`)

**Files:**
- Create: `worker/package.json`, `worker/wrangler.toml`
- Create: `worker/src/index.js` (HTTP: CORS, validación, límite, errores)
- Create: `worker/src/seneca.js` (login + navegación)
- Create: `worker/src/cifrar.js`, `worker/src/html.js` (decodificar ISO-8859-1, menú)
- Create: `worker/test/{cifrar,html,seneca,index}.test.js`, `worker/test/fixtures/menupasen.html`

**Interfaces:**
- Produces: endpoint `POST /sync` y `OPTIONS /sync` con el contrato de "Contratos JSON". `export function cifrar(s: string): string`; `export function latin1(bytes: ArrayBuffer): string`; `export function menuEntries(html: string): { path: string[], link: string }[]`; `export function findEntry(entries, alternatives): entry | null`; `export async function sync(usuario, clave, fetchImpl = fetch): Promise<{schedule, calendar, absences}>` que lanza `SenecaError(code, message)`.

- [ ] **Step 1: Paquete y configuración**

`worker/package.json`:

```json
{
  "name": "faltas-ipasen-worker",
  "private": true,
  "type": "module",
  "scripts": { "test": "node --test test/", "deploy": "wrangler deploy" },
  "devDependencies": { "wrangler": "4.154.0" }
}
```

`worker/wrangler.toml`:

```toml
name = "faltas-ipasen"
main = "src/index.js"
compatibility_date = "2025-09-01"

[[ratelimits]]
name = "SYNC_LIMIT"
namespace_id = "1001"
simple = { limit = 10, period = 60 }
```

```bash
cd /c/Users/jesus/faltas-ipasen-app/worker && npm install
```

Copiar el fixture del menú (estructura real con tokens inventados):

```bash
mkdir -p /c/Users/jesus/faltas-ipasen-app/worker/test/fixtures
node -e "const j=JSON.parse(require('fs').readFileSync('C:/Users/jesus/AppData/Local/Temp/claude/C--Users-jesus/37f908ec-6de1-4c0e-b0a5-e4a4cbbbfe30/scratchpad/menupasen.json','utf8'));require('fs').writeFileSync('C:/Users/jesus/faltas-ipasen-app/worker/test/fixtures/menupasen.html', j.html)"
grep -c 'data-menulink="../menus/TOKEN' /c/Users/jesus/faltas-ipasen-app/worker/test/fixtures/menupasen.html
```

Expected: un número > 30.

- [ ] **Step 2: Tests (fallan)**

Generar tres vectores de referencia con el `cifrado.js` original de Séneca:

```bash
node -e "const vm=require('vm'),fs=require('fs');const c={};vm.createContext(c);vm.runInContext(fs.readFileSync('C:/Users/jesus/AppData/Local/Temp/claude/C--Users-jesus/37f908ec-6de1-4c0e-b0a5-e4a4cbbbfe30/scratchpad/seneca/cifrado.js','latin1'),c);for(const s of ['a','Clave123','contraseña!'])console.log(JSON.stringify(s),String(c.cifrar(s)))"
```

`worker/test/cifrar.test.js` (sustituir `<A>`, `<B>`, `<C>` por los tres valores impresos):

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cifrar } from '../src/cifrar.js';

test('cifrar matches Séneca\'s own cifrado.js', () => {
  assert.equal(cifrar('a'), '<A>');
  assert.equal(cifrar('Clave123'), '<B>');
  assert.equal(cifrar('contraseña!'), '<C>');
});
```

`worker/test/html.test.js`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { latin1, menuEntries, findEntry } from '../src/html.js';

const menu = readFileSync(new URL('./fixtures/menupasen.html', import.meta.url), 'utf8');

test('latin1 decodes ISO-8859-1 bytes', () => {
  assert.equal(latin1(new Uint8Array([0x46, 0xED, 0x61, 0xF1]).buffer), 'Fíañ');
});

test('menuEntries gives each linked entry with its menu path', () => {
  const entries = menuEntries(menu);
  assert.deepEqual(entries[0], { path: ['Seguimiento del curso', 'Faltas de asistencia', 'Consulta'], link: '../menus/TOKEN1' });
  assert.ok(entries.some((e) => e.path.join(' > ') === 'Tutoría > Horario'));
  assert.ok(entries.some((e) => e.path.join(' > ') === 'Centro > Calendario escolar'));
});

test('findEntry matches menu paths like the APK', () => {
  const entries = menuEntries(menu);
  assert.equal(findEntry(entries, [[/faltas/i, /^consulta/i]]).link, '../menus/TOKEN1');
  assert.equal(findEntry(entries, [[/nada/i]]), null);
});
```

`worker/test/seneca.test.js` — Séneca simulado con un `fetch` falso:

```js
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
```

`worker/test/index.test.js`:

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';

const env = { SYNC_LIMIT: { limit: async () => ({ success: true }) } };
const req = (method, body, origin = 'https://vklf-official.github.io') =>
  new Request('https://w.example/sync', { method, headers: { origin, 'content-type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });

test('preflight allows only the Pages origin', async () => {
  const ok = await worker.fetch(req('OPTIONS'), env);
  assert.equal(ok.headers.get('access-control-allow-origin'), 'https://vklf-official.github.io');
  const bad = await worker.fetch(req('OPTIONS', null, 'https://evil.example'), env);
  assert.equal(bad.status, 403);
});

test('missing credentials is BAD_REQUEST', async () => {
  const res = await worker.fetch(req('POST', { usuario: '' }), env);
  assert.equal(res.status, 400);
  assert.equal((await res.json()).code, 'BAD_REQUEST');
});

test('rate limited requests are refused', async () => {
  const res = await worker.fetch(req('POST', { usuario: 'a', clave: 'b' }), { SYNC_LIMIT: { limit: async () => ({ success: false }) } });
  assert.equal(res.status, 429);
  assert.equal((await res.json()).code, 'RATE_LIMITED');
});
```

Run: `cd /c/Users/jesus/faltas-ipasen-app/worker && npm test`
Expected: FAIL (módulos inexistentes).

- [ ] **Step 3: Implementar**

`worker/src/cifrar.js`:

```js
// Réplica del cifrado de la clave de Séneca (scripts/consejeria/cifrado.js): los códigos de cada
// carácter concatenados en decimal, elevados a E módulo N. No hay secretos: es el mismo cálculo que
// hace el navegador en la página de acceso.
const E = 3584956249n;
const N = 356056806984207294102423357623243547284021n;

export function cifrar(text) {
  let digits = '';
  for (let i = 0; i < text.length; i++) digits += text.charCodeAt(i);
  let x = BigInt(digits) % N;
  let result = 1n;
  for (let e = E; e > 0n; e >>= 1n) {
    if (e & 1n) result = (result * x) % N;
    x = (x * x) % N;
  }
  return result.toString();
}
```

`worker/src/html.js`:

```js
// Utilidades sobre el HTML de Séneca sin DOM (el Worker no tiene DOMParser).

// Séneca sirve ISO-8859-1: cada byte es su punto de código.
export function latin1(buffer) {
  const bytes = new Uint8Array(buffer);
  let out = '';
  for (let i = 0; i < bytes.length; i += 8192) out += String.fromCharCode.apply(null, bytes.subarray(i, i + 8192));
  return out;
}

const decodeEntities = (s) => s
  .replace(/&nbsp;/g, ' ').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"')
  .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n))).replace(/&amp;/g, '&');
const clean = (s) => decodeEntities(s.replace(/<[^>]*>/g, '')).replace(/\s+/g, ' ').trim();

// Entradas del menú PASEN (#menupasen) con su ruta: cada <li> abre un nivel cuyo título es su primer
// <a>; las entradas con data-menulink son las que abren secciones.
export function menuEntries(html) {
  const start = html.indexOf('id="menupasen"');
  if (start < 0) return [];
  const token = /<li\b[^>]*>|<\/li>|<a\b([^>]*)>([\s\S]*?)<\/a>/gi;
  token.lastIndex = start;
  const stack = []; // título de cada <li> abierto (null hasta ver su primer <a>)
  const entries = [];
  let m;
  while ((m = token.exec(html))) {
    const tag = m[0];
    if (/^<li\b/i.test(tag)) { stack.push(null); continue; }
    if (/^<\/li>/i.test(tag)) { stack.pop(); continue; }
    const text = clean(m[2]);
    if (stack.length && stack[stack.length - 1] === null) stack[stack.length - 1] = text;
    const link = /data-menulink="([^"]+)"/i.exec(m[1] || '');
    if (link) entries.push({ path: stack.filter((t) => t !== null), link: decodeEntities(link[1]) });
  }
  return entries;
}

// Cada patrón debe casar con un nivel, en orden, y el último con la propia entrada (como el APK).
function pathMatches(path, patterns) {
  let level = 0;
  for (let i = 0; i < patterns.length; i++) {
    while (level < path.length && !patterns[i].test(path[level])) level++;
    if (level >= path.length) return false;
    if (i === patterns.length - 1 && level !== path.length - 1) return false;
    level++;
  }
  return true;
}

export function findEntry(entries, alternatives) {
  for (const patterns of alternatives) {
    const found = entries.find((e) => pathMatches(e.path, patterns));
    if (found) return found;
  }
  return null;
}
```

Si el tokenizador da rutas incorrectas sobre el fixture real (p. ej. porque hay `<li>` fuera de `#menupasen` después de él), ajustarlo hasta que pase `html.test.js`; los tests mandan.

`worker/src/seneca.js`:

```js
import { cifrar } from './cifrar.js';
import { latin1, menuEntries, findEntry } from './html.js';

const BASE = 'https://seneca.juntadeandalucia.es/seneca';
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
      const res = await this.fetchImpl(target, { method, headers, body, redirect: 'manual' });
      this.store(res.headers);
      const location = res.headers.get('location');
      if (res.status >= 300 && res.status < 400 && location) {
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

export async function sync(usuario, clave, fetchImpl = fetch) {
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
```

`worker/src/index.js`:

```js
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
```

- [ ] **Step 4: Ejecutar los tests**

Run: `cd /c/Users/jesus/faltas-ipasen-app/worker && npm test`
Expected: todos en verde.

- [ ] **Step 5: Probar en local contra el Séneca real (sin credenciales reales)**

Arrancar `npx wrangler dev --port 8787` en segundo plano (Bash `run_in_background`) desde `worker/` y, cuando escuche:

```bash
curl -s -X POST http://localhost:8787/sync -H 'origin: http://localhost:8000' -H 'content-type: application/json' -d '{"usuario":"usuario-que-no-existe-0000","clave":"x"}'
```

Expected: `{"ok":false,"code":"LOGIN_FAILED","message":"…"}` con el mensaje real de Séneca (demuestra portal + cifrado + XML de punta a punta). Parar `wrangler dev` después. **Nunca usar un usuario real en esta prueba.**

- [ ] **Step 6: Commit**

```bash
cd /c/Users/jesus/faltas-ipasen-app
git add worker/package.json worker/package-lock.json worker/wrangler.toml worker/src worker/test
git commit -m "feat: add the Worker that signs in to iPasen for the web

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: La web (`web/`)

Sustituye la página de presentación por la app. Mantiene el enlace al APK.

**Files:**
- Modify: `web/index.html` (estructura de la app)
- Create: `web/styles.css` (estilos actuales de `index.html` movidos aquí + los nuevos)
- Create: `web/app.js` (módulo ES)
- Create: `web/config.js` (`export const WORKER_URL = 'http://localhost:8787';` — la Task 6 pone la URL real)
- Create: `web/dev.sh` (prepara `web/shared/` y `web/readers/` en local)
- Modify: `.gitignore` (añadir `web/shared/` y `web/readers/`)

**Interfaces:**
- Consumes: `FaltasWeb` de `./shared/shared.mjs` (Task 2), `readSchedule`, `readAbsences`, `readCalendar` de `./readers/readers.mjs` (Task 1), `POST ${WORKER_URL}/sync` (Task 3).

- [ ] **Step 1: `web/dev.sh`**

```bash
#!/usr/bin/env bash
# Prepara la web para probarla en local: http://localhost:8000
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
./gradlew -q :shared:jsBrowserProductionLibraryDistribution
rm -rf web/shared web/readers
cp -r shared/build/dist/js/productionLibrary web/shared
mkdir -p web/readers && cp scraper/readers/readers.mjs web/readers/
echo "Listo. Sirve web/ con: npx --yes http-server web -p 8000 -c-1"
```

(Usar el nombre de tarea Gradle que registró la Task 2 si fue otro.)

- [ ] **Step 2: Estructura y comportamiento**

`web/index.html` (`<script type="module" src="app.js">`, `<link rel="stylesheet" href="styles.css">`): cabecera con marca "Faltas iPasen" y enlace "Descargar APK" (`https://github.com/VKLF-OFFICIAL/faltas-ipasen/releases/latest/download/faltas-ipasen.apk`); `<main>` con:

1. `<form id="form">`: etapa (`<select id="nivel">`, opciones de `FaltasWeb.niveles(hoy)`, por defecto `FP_SUPERIOR`), umbral (`<input id="umbral" type="number" step="0.5" min="1" max="100">`, se rellena con el `threshold` de la etapa al cambiarla), fecha de fin (`<input id="fin" type="date">`, se rellena con `cutoff`; su `<label>` muestra `cutoffLabel`), usuario (`autocomplete="username"`), clave (`type="password" autocomplete="current-password"`), botón "Ver mis faltas". Texto bajo el botón: "Tu usuario y tu clave se envían a iPasen a través de nuestro servidor solo para esta consulta; no se guardan en ningún sitio."
2. `<section id="estado" role="status" aria-live="polite">`: "Conectando con iPasen…" mientras espera; errores en una tarjeta roja.
3. `<section id="resultado" hidden>`: total del curso (porcentaje grande `global.percentText`, "X h de Y h", "Llevas N de M días lectivos"); lista de módulos en el orden recibido (barra con ancho `min(percent / umbral, 1) * 100 %`, color por `risk`: LOW verde, MEDIUM ámbar, HIGH rojo; abreviatura grande + nombre debajo; `percentText`; "Margen: N h" con `marginHours`; justificadas e injustificadas en horas); aviso de filas apartadas si `skipped.length` (`<details>` con la lista); botón "Ver historial".
4. `<section id="historial" hidden>`: filtro por módulo (`<select>`, "Todos" + un `option` por `subjectKey` con su `label`), días en orden recibido (fecha con `Intl.DateTimeFormat('es-ES', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' })` y primera letra en mayúscula), cada entrada con tramo, `label`, horas y "Justificada"/"Injustificada"; botón "Volver al resumen".
5. Botón "Nueva consulta" que vuelve al formulario **vaciando la clave**.
6. Pie con "No es una app oficial ni está relacionada con la Junta de Andalucía." y enlace a la licencia.

`web/app.js`, núcleo:

```js
import { FaltasWeb } from './shared/shared.mjs';
import { readSchedule, readAbsences, readCalendar } from './readers/readers.mjs';
import { WORKER_URL } from './config.js';

// "Hoy" en Madrid, como el APK (AppGraph.today).
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Madrid' }).format(new Date());
const parse = (html) => new DOMParser().parseFromString(html, 'text/html');
const raw = (data) => JSON.stringify({ ok: true, data });

async function consultar({ usuario, clave, fin, umbral }) {
  let res;
  try {
    res = await fetch(`${WORKER_URL}/sync`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ usuario, clave }),
    });
  } catch {
    throw new Error('No se pudo conectar con el servidor. Comprueba tu conexión.');
  }
  const body = await res.json().catch(() => ({ ok: false, message: 'Respuesta no válida del servidor.' }));
  if (!body.ok) throw new Error(body.message || 'No se pudo conectar con iPasen.');
  try {
    const schedule = readSchedule(parse(body.schedule));
    const calendar = readCalendar(body.calendar);
    const absences = readAbsences(parse(body.absences));
    return JSON.parse(FaltasWeb.calcular(raw(schedule), raw(calendar), raw(absences), fin, today, Number(umbral)));
  } catch (e) {
    throw new Error('iPasen ha cambiado o no se pudo leer: ' + (e && e.message ? e.message : e));
  }
}
```

El resto de `app.js` (rellenar etapas, eventos, pintar resultado e historial, filtro) se escribe con `textContent`/`createElement` (**nunca** `innerHTML` con datos de iPasen). Mientras hay una consulta en curso el botón está desactivado. No se usa ninguna API de almacenamiento.

`web/styles.css`: los estilos actuales de `index.html` (tokens de color índigo, modo oscuro) más formulario, tarjetas, barras e historial; móvil primero (márgenes laterales 16 px, sin scroll horizontal), `prefers-color-scheme: dark`, sombras neutras, foco visible en controles, Inter.

- [ ] **Step 3: Probar en local**

```bash
cd /c/Users/jesus/faltas-ipasen-app && bash web/dev.sh
```

Arrancar en segundo plano (Bash `run_in_background`) `npx --yes http-server web -p 8000 -c-1` y, desde `worker/`, `npx wrangler dev --port 8787`. Abrir `http://localhost:8000` con el MCP `chrome-devtools` u `opera-devtools` y comprobar:
1. Las 7 etapas aparecen; al cambiar de etapa cambian umbral y fecha de fin.
2. Con usuario `usuario-que-no-existe-0000` y clave `x`, sale el mensaje de Séneca de credenciales incorrectas en la tarjeta de error y el botón vuelve a estar activo.
3. Sin errores en la consola. Capturas a 390 px y a 1280 px de ancho, sin scroll horizontal.
4. `grep -n "localStorage\|sessionStorage\|indexedDB\|innerHTML" web/app.js` no devuelve nada.

Parar los dos servidores después.

- [ ] **Step 4: Commit**

```bash
cd /c/Users/jesus/faltas-ipasen-app
git add .gitignore web/index.html web/styles.css web/app.js web/config.js web/dev.sh
git commit -m "feat: turn the Pages site into the online app

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: GitHub Actions compila y publica la web

**Files:**
- Modify: `.github/workflows/pages.yml`

**Interfaces:**
- Consumes: tarea Gradle de la Task 2, `scraper/readers/`, `worker/`, `web/`.

- [ ] **Step 1: Workflow**

Sustituir `.github/workflows/pages.yml` por:

```yaml
name: Pages
# Compila la lógica compartida a JS, ejecuta los tests y publica web/ en GitHub Pages.

on:
  push:
    branches: [master]
    paths: ["web/**", "shared/**", "scraper/**", "worker/**", ".github/workflows/pages.yml"]
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: true

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: gradle/actions/setup-gradle@v4
      - uses: actions/setup-node@v4
        with:
          node-version: "22"
      - name: Tests de shared (JVM y JS)
        run: ./gradlew --no-daemon :shared:allTests
      - name: Tests de readers y Worker
        run: |
          (cd scraper/readers && npm ci && npm test)
          (cd worker && npm ci && npm test)
      - name: Compilar shared para el navegador
        run: ./gradlew --no-daemon :shared:jsBrowserProductionLibraryDistribution
      - name: Montar la web
        run: |
          cp -r shared/build/dist/js/productionLibrary web/shared
          mkdir -p web/readers && cp scraper/readers/readers.mjs web/readers/
          rm -f web/dev.sh
      - uses: actions/configure-pages@v5
      - uses: actions/upload-pages-artifact@v3
        with:
          path: web
      - id: deployment
        uses: actions/deploy-pages@v4
```

`gradlew` necesita permiso de ejecución en Linux: `git ls-files -s gradlew` debe mostrar `100755`; si no, `git update-index --chmod=+x gradlew` en este mismo commit. `:shared:allTests` en CI no necesita el SDK de Android (solo compila `shared`); si Gradle exige el SDK por configurar `:app`, añadir el paso `android-actions/setup-android@v3` antes de los tests.

- [ ] **Step 2: Commit y empujar**

```bash
cd /c/Users/jesus/faltas-ipasen-app
git add .github/workflows/pages.yml gradlew
git commit -m "ci: build the shared module and readers into the Pages site

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin feat/app-faltas && git checkout master && git merge --ff-only feat/app-faltas && git push origin master && git checkout feat/app-faltas
```

- [ ] **Step 3: Verificar el despliegue**

```bash
gh run watch --repo VKLF-OFFICIAL/faltas-ipasen --exit-status $(gh run list --repo VKLF-OFFICIAL/faltas-ipasen --workflow pages.yml --limit 1 --json databaseId -q '.[0].databaseId')
curl -s -o /dev/null -w "%{http_code}\n" https://vklf-official.github.io/faltas-ipasen/shared/shared.mjs
```

Expected: el run termina en `success` y `shared.mjs` responde `200`.

---

### Task 6: Desplegar el Worker y prueba real con el usuario

Requiere que el usuario haya iniciado sesión en Cloudflare (`npx wrangler login`).

**Files:**
- Modify: `web/config.js` (URL real del Worker)

- [ ] **Step 1: Desplegar**

```bash
cd /c/Users/jesus/faltas-ipasen-app/worker && npx wrangler whoami && npx wrangler deploy
```

Expected: imprime `https://faltas-ipasen.<subdominio>.workers.dev`. Si rechaza `[[ratelimits]]`, quitar ese bloque, desplegar de nuevo y anotarlo.

- [ ] **Step 2: Comprobación sin credenciales reales**

```bash
curl -s -X POST https://faltas-ipasen.<subdominio>.workers.dev/sync -H 'origin: https://vklf-official.github.io' -H 'content-type: application/json' -d '{"usuario":"usuario-que-no-existe-0000","clave":"x"}'
```

Expected: `"code":"LOGIN_FAILED"` con el mensaje de Séneca.

- [ ] **Step 3: Apuntar la web al Worker y publicar**

`web/config.js`:

```js
export const WORKER_URL = 'https://faltas-ipasen.<subdominio>.workers.dev';
```

```bash
cd /c/Users/jesus/faltas-ipasen-app
git add web/config.js
git commit -m "feat: point the web at the deployed Worker

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin feat/app-faltas && git checkout master && git merge --ff-only feat/app-faltas && git push origin master && git checkout feat/app-faltas
```

- [ ] **Step 4: Prueba real con el usuario**

Pedir al usuario que abra `https://vklf-official.github.io/faltas-ipasen/`, elija su etapa y entre con **su** usuario y clave (los escribe él; nunca pasan por el chat). Comprobar con él:
1. Aparece el resumen y los % coinciden con los del APK en su móvil.
2. El Historial muestra sus faltas y filtra por módulo.
3. Recargar la página vuelve a pedir todo (nada guardado).

Si algo falla, el mensaje de error dice en qué paso; depurar con superpowers:systematic-debugging (el HTML real puede diferir de los fixtures: ajustar readers o menú con un test que lo reproduzca).

---

## Fuera de este plan

- Que el APK use `scraper/readers/` (plan propio, con prueba en el móvil).
- `Regex("\\s+")` de `ScrapeParsers` frente a U+00A0 en JS: los readers ya normalizan espacios con `\s` de JS antes de llegar a `shared`; se cierra cuando el APK use los mismos readers.
