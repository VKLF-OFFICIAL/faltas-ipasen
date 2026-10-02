// Prelude for every step. The caller defines TOKEN, STEP (and CREDS for login).
// Contract: every step ends by calling report() exactly once through run().
// AndroidBridge is visible to every frame; TOKEN lives only in this closure, so a third-party
// iframe cannot forge results.
function report(payload) {
  AndroidBridge.onResult(TOKEN, STEP, JSON.stringify(payload));
}

function run(fn) {
  Promise.resolve()
    .then(fn)
    .then(function (data) { report({ ok: true, data: data === undefined ? null : data }); })
    .catch(function (e) {
      var payload = { ok: false, error: String(e && e.message ? e.message : e) };
      if (e && e.loginFailed) payload.loginFailed = true;
      if (e && e.timeout) payload.timeout = true;
      report(payload);
    });
}

function sleep(ms) {
  return new Promise(function (resolve) { setTimeout(resolve, ms); });
}

// Polls predicate() until it returns a truthy value (returned) or the timeout expires.
// A timeout is flagged so the app treats it as a slow Séneca (retry), not as a changed page.
// The message says what was awaited and which page was showing, so a user can report it.
function waitFor(predicate, timeoutMs, what) {
  var started = Date.now();
  return new Promise(function (resolve, reject) {
    (function poll() {
      var value = null;
      try { value = predicate(); } catch (ignored) {}
      if (value) return resolve(value);
      if (Date.now() - started > timeoutMs) {
        var e = new Error('tiempo de espera agotado en ' + STEP + (what ? ' esperando ' + what : '') +
          ' [' + describePage() + ']');
        e.timeout = true;
        return reject(e);
      }
      setTimeout(poll, 250);
    })();
  });
}

// Short summary of the current page (path, title, headings or visible notices; never form values).
// The path drops Séneca's one-time tokens.
function describePage() {
  function clean(text, max) {
    text = String(text || '').replace(/\s+/g, ' ').trim();
    return text.length > max ? text.slice(0, max) + '…' : text;
  }
  var parts = [location.pathname.replace(/\/nav\/[^\/]+/, '/nav/…')];
  if (document.title) parts.push('título «' + clean(document.title, 50) + '»');
  var headings = Array.prototype.map.call(document.querySelectorAll('h1,h2,h3,h4'), function (h) {
    return clean(h.innerText, 50);
  }).filter(Boolean).slice(0, 3);
  if (headings.length) parts.push('cabeceras «' + headings.join(' | ') + '»');
  var notice = document.querySelector('.alert, .ui-dialog, #loginMessage.show, .mensaje, .error');
  if (notice && clean(notice.innerText, 1)) parts.push('aviso «' + clean(notice.innerText, 80) + '»');
  if (document.getElementById('USUARIO')) parts.push('formulario de acceso visible');
  if (!headings.length && !notice) parts.push('texto «' + clean(document.body && document.body.innerText, 80) + '»');
  return parts.join('; ');
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
    return title.test(h.innerText);
  });
}

// Séneca's menu navigation object, once its script has loaded. It may be a top-level `let`, so it
// is looked up by name rather than on window.
function navigation() {
  /* global Navegacion */
  return typeof Navegacion !== 'undefined' && Navegacion && typeof Navegacion.llamadaMenu === 'function' ? Navegacion : null;
}

// Séneca uses one-time URLs, so sections are opened through their menu entry. That reloads the
// page and the engine injects this step again; the second run finds the section already open.
// The sessionStorage mark stops a re-injection on an intermediate page from clicking twice.
// section = { id, label, paths: [[regex per menu level, outermost first], …alternatives], title: regex }.
async function openSection(section) {
  if (onPage(section.title)) return;
  var key = '__faltas_menu_' + section.id;
  if (Date.now() - Number(sessionStorage.getItem(key) || 0) > 20000) {
    var item = await findMenuEntry(section);
    // The menu can be in the DOM before Séneca's navigation script has loaded (seen on other
    // users' phones as "Navegacion is not defined"). Wait for it; if it never appears, fall back
    // to clicking the entry, whose own handler opens the section.
    var nav = await waitFor(navigation, 15000).catch(function () { return null; });
    sessionStorage.setItem(key, String(Date.now()));
    if (nav) nav.llamadaMenu(item);
    else item.click();
  }
  await waitFor(function () { return onPage(section.title); }, 45000, 'la página ' + section.label);
}

function menuLinks() {
  return Array.prototype.slice.call(document.querySelectorAll('a[onclick*="llamadaMenu"]'));
}

// Menu levels of an entry, outermost first: ["Seguimiento del curso", "Faltas de asistencia", "Consulta"].
function menuPath(link) {
  var path = [link.textContent.replace(/\s+/g, ' ').trim()];
  var li = link.closest('li');
  while (li && li.parentElement) {
    li = li.parentElement.closest('li');
    var title = li && li.querySelector(':scope > a');
    if (title) path.unshift(title.textContent.replace(/\s+/g, ' ').trim());
  }
  return path;
}

// Each regex must match a menu level, in order, and the last one the entry itself.
function pathMatches(path, patterns) {
  var level = 0;
  for (var i = 0; i < patterns.length; i++) {
    while (level < path.length && !patterns[i].test(path[level])) level++;
    if (level >= path.length) return false;
    if (i === patterns.length - 1 && level !== path.length - 1) return false;
    level++;
  }
  return true;
}

// Menu ids differ between accounts (another user's menu had no #h1448725599), so the known id
// is only a shortcut and the entry is otherwise found by its menu path.
async function findMenuEntry(section) {
  await waitFor(function () { return menuLinks().length; }, 30000, 'el menú de Séneca');
  var started = Date.now();
  while (true) {
    var byId = document.getElementById(section.id);
    if (byId && /llamadaMenu/.test(byId.getAttribute('onclick') || '')) return byId;
    var links = menuLinks();
    for (var i = 0; i < section.paths.length; i++) {
      var byPath = links.find(function (a) { return pathMatches(menuPath(a), section.paths[i]); });
      if (byPath) return byPath;
    }
    // The menu is rendered in one go; a short grace period covers a late partial render.
    if (Date.now() - started > 5000) break;
    await sleep(250);
  }
  var available = menuLinks().map(function (a) { return menuPath(a).join(' > '); }).join('; ');
  throw new Error('tu menú de iPasen no tiene «' + section.label + '». Opciones: ' +
    (available.length > 400 ? available.slice(0, 400) + '…' : available));
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
