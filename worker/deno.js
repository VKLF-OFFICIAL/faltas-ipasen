// Entrada para Deno Deploy: el mismo manejador que el Worker, servido con Deno.serve.
// Sin el límite de peticiones de Cloudflare: index.js lo omite cuando no hay binding.
import worker from './src/index.js';

Deno.serve((request) => worker.fetch(request, {}));
