// Entradas del historial de un módulo. `key` es la clave del módulo (subjectKey), no la etiqueta
// abreviada; con clave vacía se devuelven todas.
export function entradasDe(entries, key) {
  return key ? entries.filter((e) => e.subjectKey === key) : entries;
}
