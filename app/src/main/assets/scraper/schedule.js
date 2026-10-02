// Tutoría > Horario. The grid shows subject abbreviations; the "Materias" table maps them to the
// full names used on the absences page. Slots whose first line is not a subject (e.g. tutor
// admin time) are skipped.
run(async function () {
  await openSection({
    id: 'h1448729255', label: 'Tutoría > Horario',
    paths: [[/tutor/i, /^horario/i], [/^horario/i]], title: /HORARIO ESCOLAR/i,
  });
  var tables = await waitFor(function () {
    var all = Array.prototype.slice.call(document.querySelectorAll('table'));
    var grid = all.find(function (t) { return t.rows[0] && /lunes/i.test(t.rows[0].innerText); });
    var subjects = all.find(function (t) { return t.rows[0] && /materias/i.test(t.rows[0].innerText); });
    return grid && subjects ? { grid: grid, subjects: subjects } : null;
  }, 30000, 'las tablas de horario y materias');

  var names = {};
  Array.prototype.forEach.call(tables.subjects.rows, function (row) {
    if (row.cells.length < 2) return;
    var abbr = row.cells[0].innerText.trim();
    var name = row.cells[1].innerText.trim();
    if (abbr && name && !/^abreviatura$/i.test(abbr)) names[abbr] = name;
  });

  var DAYS = ['LUNES', 'MARTES', 'MIÉRCOLES', 'JUEVES', 'VIERNES', 'SÁBADO', 'DOMINGO'];
  var columnDay = Array.prototype.map.call(tables.grid.rows[0].cells, function (cell) {
    return DAYS.indexOf(cell.innerText.trim().toUpperCase()) + 1;
  });

  var slots = [];
  Array.prototype.slice.call(tables.grid.rows, 1).forEach(function (row) {
    if (!row.cells.length) return;
    var found = [];
    Array.prototype.slice.call(row.cells, 1).forEach(function (cell, i) {
      var day = columnDay[i + 1];
      var abbr = cell.innerText.trim().split('\n')[0].trim();
      if (day > 0 && names[abbr]) found.push({ subject: names[abbr], abbr: abbr, day: day });
    });
    // Rows without classes (e.g. "RECREO") may carry no time range: skip them. A row with classes
    // still needs one (tramoHours throws), so its hours are never dropped silently.
    if (!found.length) return;
    var hours = tramoHours(row.cells[0].innerText);
    found.forEach(function (slot) { slot.hours = hours; slots.push(slot); });
  });
  if (!slots.length) throw new Error('horario vacío o con un formato desconocido');
  return slots;
});
