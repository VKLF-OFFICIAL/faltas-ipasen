// Transporte de los datos del marcador a la web: JSON -> gzip -> base64url (en el fragmento de la URL).

async function pipe(bytes, stream) {
  const out = new Blob([bytes]).stream().pipeThrough(stream);
  return new Uint8Array(await new Response(out).arrayBuffer());
}

export async function pack(obj) {
  const gz = await pipe(new TextEncoder().encode(JSON.stringify(obj)), new CompressionStream('gzip'));
  let bin = '';
  for (let i = 0; i < gz.length; i += 8192) bin += String.fromCharCode.apply(null, gz.subarray(i, i + 8192));
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export async function unpack(str) {
  if (typeof str !== 'string' || !/^[A-Za-z0-9_-]+$/.test(str)) throw new Error('datos no válidos');
  const bin = atob(str.replace(/-/g, '+').replace(/_/g, '/'));
  const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
  const raw = await pipe(bytes, new DecompressionStream('gzip'));
  return JSON.parse(new TextDecoder().decode(raw));
}
