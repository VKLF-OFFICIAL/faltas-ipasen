// Entrada para Deno Deploy: el mismo manejador que el Worker, servido con Deno.serve.
// Sin bindings de Cloudflare: los límites son en memoria (por IP y por usuario de Séneca).
import worker from './src/index.js';
import { createLimiter } from './src/ratelimit.js';

const ipLimiter = createLimiter({ limit: 10, periodMs: 60_000 });
const userLimiter = createLimiter({ limit: 3, periodMs: 15 * 60_000 });

Deno.serve((request, info) => worker.fetch(request, {
  SYNC_LIMIT: ipLimiter,
  USER_LIMIT: userLimiter,
  clientIp: info.remoteAddr.hostname,
}));
