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

// Same-origin request with the logged-in session cookies.
async function fetchJson(url, options) {
  var response = await fetch(url, Object.assign({ credentials: 'include' }, options || {}));
  if (!response.ok) throw new Error('HTTP ' + response.status + ' en ' + url);
  return response.json();
}

// "5/9/2026" or "05-09-2026" -> "2026-09-05"
function toIsoDate(text) {
  var m = /^(\d{1,2})[\/-](\d{1,2})[\/-](\d{4})$/.exec(String(text).trim());
  if (!m) throw new Error('fecha no reconocida: ' + text);
  return m[3] + '-' + m[2].padStart(2, '0') + '-' + m[1].padStart(2, '0');
}
