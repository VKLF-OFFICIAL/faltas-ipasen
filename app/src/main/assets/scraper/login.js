// Portal PASEN (Séneca): fills the login form and submits it after reporting, because the
// submission reloads the page and destroys this JS context.
run(async function () {
  var user = await waitFor(function () { return document.getElementById('USUARIO'); }, 20000);
  var pass = document.getElementById('CLAVE_P');
  var submit = document.getElementById('Entrar');
  if (!pass || !submit) throw new Error('formulario de login no encontrado');
  setNativeValue(user, CREDS.user);
  setNativeValue(pass, CREDS.pass);
  setTimeout(function () { submit.click(); }, 300);
  return 'enviado';
});
