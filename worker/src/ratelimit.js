// Limitador de ventana fija en memoria, con la misma forma que el binding de Cloudflare
// (`limit({ key }) -> { success }`). Sirve para Deno Deploy, donde no hay binding.
export function createLimiter({ limit, periodMs, now = () => Date.now() }) {
  const windows = new Map(); // clave -> { start, count }
  return {
    async limit({ key }) {
      const t = now();
      for (const [k, w] of windows) if (t - w.start >= periodMs) windows.delete(k);
      let w = windows.get(key);
      if (!w) { w = { start: t, count: 0 }; windows.set(key, w); }
      if (w.count >= limit) return { success: false };
      w.count++;
      return { success: true };
    },
  };
}
