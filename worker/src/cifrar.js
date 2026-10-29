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
