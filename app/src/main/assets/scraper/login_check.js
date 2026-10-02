// Logged in: Séneca shows the "Salir" link (logout()). Rejected: the login page comes back with
// #loginMessage visible ("Usuario y/o contraseña incorrecto").
run(async function () {
  var outcome = await waitFor(function () {
    if (document.querySelector('a[href="javascript:logout()"]')) return 'ok';
    var message = document.getElementById('loginMessage');
    if (message && message.classList.contains('show') && message.innerText.trim()) {
      return 'error:' + message.innerText.trim().replace(/\s+/g, ' ');
    }
    return null;
  }, 40000, 'el resultado del acceso');
  if (outcome !== 'ok') {
    var e = new Error(outcome.slice('error:'.length));
    e.loginFailed = true;
    throw e;
  }
  return 'ok';
});
