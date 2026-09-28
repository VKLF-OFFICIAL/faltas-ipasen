// Prelude for every step. The caller defines STEP (and CREDS for login).
// Contract: every step ends by calling report() exactly once through run().
function report(payload) {
  AndroidBridge.onResult(STEP, JSON.stringify(payload));
}

function run(fn) {
  Promise.resolve()
    .then(fn)
    .then(function (data) { report({ ok: true, data: data === undefined ? null : data }); })
    .catch(function (e) {
      var payload = { ok: false, error: String(e && e.message ? e.message : e) };
      if (e && e.loginFailed) payload.loginFailed = true;
      report(payload);
    });
}

function sleep(ms) {
  return new Promise(function (resolve) { setTimeout(resolve, ms); });
}

// Polls predicate() until it returns a truthy value (returned) or the timeout expires.
function waitFor(predicate, timeoutMs) {
  var started = Date.now();
  return new Promise(function (resolve, reject) {
    (function poll() {
      var value = null;
      try { value = predicate(); } catch (ignored) {}
      if (value) return resolve(value);
      if (Date.now() - started > timeoutMs) return reject(new Error('tiempo de espera agotado en ' + STEP));
      setTimeout(poll, 250);
    })();
  });
}

// Sets an input value so Angular/React/Vue bindings notice it.
function setNativeValue(input, value) {
  var setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(input), 'value').set;
  setter.call(input, value);
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
}

function onPage(title) {
  return Array.prototype.some.call(document.querySelectorAll('h1,h2,h3,h4'), function (h) {
    return h.innerText.toUpperCase().indexOf(title) >= 0;
  });
}

// Séneca uses one-time URLs, so sections are opened through their menu entry. That reloads the
// page and the engine injects this step again; the second run finds the section already open.
// The sessionStorage mark stops a re-injection on an intermediate page from clicking twice.
async function openSection(menuId, title) {
  if (onPage(title)) return;
  var key = '__faltas_menu_' + menuId;
  if (Date.now() - Number(sessionStorage.getItem(key) || 0) > 20000) {
    var item = await waitFor(function () { return document.getElementById(menuId); }, 20000);
    sessionStorage.setItem(key, String(Date.now()));
    Navegacion.llamadaMenu(item);
  }
  await waitFor(function () { return onPage(title); }, 30000);
}

// "8:15 - 9:15" -> 1, "11:15 - 11:45" -> 0.5
function tramoHours(text) {
  var m = /(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})/.exec(String(text));
  if (!m) throw new Error('tramo horario no reconocido: ' + text);
  var minutes = (Number(m[3]) * 60 + Number(m[4])) - (Number(m[1]) * 60 + Number(m[2]));
  if (minutes <= 0) throw new Error('tramo horario no válido: ' + text);
  return Math.round(minutes / 60 * 100) / 100;
}

// "5/9/2026" or "05-09-2026" -> "2026-09-05"
function toIsoDate(text) {
  var m = /^(\d{1,2})[\/-](\d{1,2})[\/-](\d{4})$/.exec(String(text).trim());
  if (!m) throw new Error('fecha no reconocida: ' + text);
  return m[3] + '-' + m[2].padStart(2, '0') + '-' + m[1].padStart(2, '0');
}
