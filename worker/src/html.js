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
