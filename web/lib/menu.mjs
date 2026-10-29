// Localiza las entradas del menú PASEN en el DOM. Misma lógica que el APK
// (app/src/main/assets/scraper/common.js) y que worker/src/seneca.js, versión DOM.

export const SECTIONS = {
  absences: { label: 'Faltas de asistencia > Consulta', paths: [[/faltas/i, /^consulta/i], [/^faltas de asistencia$/i], [/faltas/i, /^(consulta|faltas)/i]] },
  schedule: { label: 'Tutoría > Horario', paths: [[/tutor/i, /^horario/i], [/^horario/i]] },
  calendar: { label: 'Centro > Calendario escolar', paths: [[/centro/i, /^calendario/i], [/^calendario/i]] },
};

const clean = (s) => String(s || '').replace(/\s+/g, ' ').trim();

// Niveles del menú de una entrada, de fuera a dentro: ["Seguimiento del curso", "Faltas de asistencia", "Consulta"].
export function menuPath(a) {
  const path = [clean(a.textContent)];
  let li = a.closest('li');
  while (li && li.parentElement) {
    li = li.parentElement.closest('li');
    const title = li && Array.from(li.children).find((c) => c.tagName === 'A');
    if (title) path.unshift(clean(title.textContent));
  }
  return path;
}

// Cada patrón debe casar con un nivel, en orden, y el último con la propia entrada.
export function pathMatches(path, patterns) {
  let level = 0;
  for (let i = 0; i < patterns.length; i++) {
    while (level < path.length && !patterns[i].test(path[level])) level++;
    if (level >= path.length) return false;
    if (i === patterns.length - 1 && level !== path.length - 1) return false;
    level++;
  }
  return true;
}

export function findEntry(links, alternatives) {
  for (const patterns of alternatives) {
    const found = links.find((a) => pathMatches(menuPath(a), patterns));
    if (found) return found;
  }
  return null;
}

export function menuLinks(doc) {
  return Array.from(doc.querySelectorAll('#menupasen a[data-menulink]'));
}
