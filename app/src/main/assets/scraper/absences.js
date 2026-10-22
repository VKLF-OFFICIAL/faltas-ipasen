// Faltas de asistencia > Consulta, filter "Todas": Fecha | Tramo horario | Ausencia de | Estado.
// "Retraso" rows are late arrivals, not absences, so they are skipped.
run(async function () {
  await openSection({
    id: 'h1448725599', label: 'Faltas de asistencia > Consulta',
    paths: [[/faltas/i, /^consulta/i], [/^faltas de asistencia$/i], [/faltas/i, /^(consulta|faltas)/i]], title: /FALTAS DE ASISTENCIA/i,
  });
  var table = await waitFor(function () {
    return Array.prototype.find.call(document.querySelectorAll('table'), function (t) {
      return t.rows[0] && /fecha/i.test(t.rows[0].innerText) && /estado/i.test(t.rows[0].innerText);
    }) || (/registros:\s*0\b/i.test(document.body.innerText) ? 'vacia' : null);
  }, 30000, 'la tabla de faltas');
  if (table === 'vacia') return [];

  var filter = document.getElementById('C_TIPFAL');
  if (filter && filter.value !== 'T') throw new Error('el filtro de faltas no está en "Todas"');

  var header = Array.prototype.map.call(table.rows[0].cells, function (c) { return c.innerText.trim().toLowerCase(); });
  var col = {
    date: header.indexOf('fecha'),
    tramo: header.indexOf('tramo horario'),
    subject: header.indexOf('ausencia de'),
    state: header.indexOf('estado'),
  };
  if (Object.keys(col).some(function (k) { return col[k] < 0; })) throw new Error('columnas de faltas desconocidas: ' + header.join(', '));

  var rowsRead = 0;
  var absences = [];
  Array.prototype.slice.call(table.rows, 1).forEach(function (row) {
    if (row.cells.length < header.length) return;
    rowsRead++;
    var state = row.cells[col.state].innerText.trim().toLowerCase();
    if (state.indexOf('retraso') >= 0) return;
    var tramo = row.cells[col.tramo].innerText.trim();
    // "Día completo" rows have no subject; the app spreads them over that day's timetable.
    if (/^d[ií]a completo$/i.test(tramo)) {
      absences.push({
        date: toIsoDate(row.cells[col.date].innerText),
        subject: '',
        fullDay: true,
        justified: state.indexOf('justificada') === 0,
        tramo: tramo,
      });
      return;
    }
    absences.push({
      date: toIsoDate(row.cells[col.date].innerText),
      subject: row.cells[col.subject].innerText.trim(),
      hours: tramoHours(row.cells[col.tramo].innerText),
      justified: state.indexOf('justificada') === 0,
      tramo: row.cells[col.tramo].innerText.trim(),
    });
  });
  var total = /registros:\s*(\d+)/i.exec(document.body.innerText);
  if (total && Number(total[1]) !== rowsRead) {
    throw new Error('la tabla de faltas muestra ' + rowsRead + ' de ' + total[1] + ' registros (¿está paginada?)');
  }
  return absences;
});
