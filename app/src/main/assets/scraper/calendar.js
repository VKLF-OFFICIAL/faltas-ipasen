// Centro > Calendario escolar: 12 month grids of the current academic year (Sept–Aug).
// Weekends and every "festivo*" cell are non-teaching days. The centre calendar marks the first
// teaching day of each stage with "primerDiaLectivo"; Vocational Training (FP) starts on the latest
// of those marks in September, so earlier days are non-teaching for this student.
run(async function () {
  await openSection({
    id: 'h1448729343', label: 'Centro > Calendario escolar',
    paths: [[/centro/i, /^calendario/i], [/^calendario/i]], title: /CALENDARIO GR[AÁ]FICO/i,
  });
  var MONTHS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto',
    'septiembre', 'octubre', 'noviembre', 'diciembre'];
  var grids = await waitFor(function () {
    var tables = document.querySelectorAll('table.ui-datepicker-calendar');
    var titles = document.querySelectorAll('.ui-datepicker-title');
    return tables.length === 12 && titles.length === 12 ? { tables: tables, titles: titles } : null;
  }, 30000, 'los 12 meses del calendario');

  var days = [];
  var septemberStarts = [];
  Array.prototype.forEach.call(grids.tables, function (table, i) {
    var title = grids.titles[i].innerText.trim().toLowerCase().split(/\s+/);
    var month = MONTHS.indexOf(title[0]) + 1;
    var year = Number(title[1]);
    if (month < 1 || !year) throw new Error('mes no reconocido: ' + grids.titles[i].innerText);
    Array.prototype.forEach.call(table.querySelectorAll('td'), function (td) {
      var dayNumber = Number(td.innerText.trim());
      if (!dayNumber || td.classList.contains('ui-datepicker-other-month')) return;
      var date = year + '-' + String(month).padStart(2, '0') + '-' + String(dayNumber).padStart(2, '0');
      var classes = td.className;
      if (month === 9 && /primerDiaLectivo/.test(classes)) septemberStarts.push(date);
      days.push({ date: date, lectivo: !/week-end|festivo/.test(classes) });
    });
  });
  if (!septemberStarts.length) throw new Error('el calendario no marca el primer día lectivo');
  var courseStart = septemberStarts.sort()[septemberStarts.length - 1];
  days.forEach(function (d) { if (d.date < courseStart) d.lectivo = false; });
  return days;
});
