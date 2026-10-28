# Versión web — Diseño

**Fecha:** 2026-10-28
**Estado:** Aprobado por el usuario ("sí, sigue"), con la fuente marcador en lugar de Cloudflare
**Parte de:** app Faltas iPasen (spec base: `2026-09-27-faltas-ipasen-app-design.md`, rediseño: `2026-09-28-rediseno-visual-design.md`)

## Objetivo

Tener, además del APK, una web que haga lo mismo que la app: iniciar sesión en iPasen, leer horario,
calendario y faltas, y calcular el % de faltas por módulo frente al umbral. El APK **no se abandona**:
web y APK viven en el mismo repo y se mantienen a la vez; todo lo que pueda compartirse se escribe una vez.

## Decisiones del usuario (2026-10-28)

| Tema | Decisión |
|---|---|
| Alcance | La web hace lo mismo que el APK, de la misma forma (login con usuario y clave de iPasen) |
| Coste | Todo gratis; sin el hosting de Comvive |
| Alojamiento | GitHub: repo, GitHub Pages para la web y GitHub Actions para desplegar |
| Repo | Público (no contiene credenciales ni datos personales) |
| APK | Se sigue manteniendo; los cambios deben poder llegar a web y APK |
| Cloudflare | **Por ahora no.** La web recibe los datos con un marcador (bookmarklet) que se ejecuta dentro de Séneca. El Worker queda como segunda fuente futura |

## Cambio de decisión (2026-10-28, tarde) — manda sobre el resto del documento

El usuario quiere la web **completamente online**: "que al abrirla te pida la información sobre ti y
tus datos de iPasen y te dé los resultados, que haga esto cada vez que abres la página". Eligió
"Login en la web" con un Cloudflare Worker. Por tanto:

- **Fuente de datos: Worker** (sección `worker/`). El marcador se descarta.
- **Nada se guarda**: cada vez que se abre la web pide etapa, umbral, fecha de fin, usuario y clave;
  muestra Resumen e Historial de esa consulta. Sin `localStorage`, sin "recordar contraseña", sin PWA.
- Prueba (2026-10-28): un Worker desplegado alcanza Séneca (HTTP 200, formulario de login, ~250 ms).
- Login exacto (de `IdenUsuExtResp.js`): `POST /seneca/jsp/ComprobarUsuarioExt.jsp` con
  `N_V_=NV_<0..10000>`, `rndval=<0..1e9>`, `NAV_WEB_NOMBRE`, `NAV_WEB_VERSION`, `RESOLUCION`,
  `CLAVECIFRADA=cifrar(clave)`, `USUARIO`, `C_INTERFAZ=PASEN`; respuesta XML `correcto`/`mensaje`; si
  `SI`, enviar el formulario `FORMULARIO_IDEN_USU` (`CON_PRUEBA=N`, `USER_QR=`) a
  `/seneca/jsp/Acceso.jsp?rndval=<n>` y seguir redirecciones hasta la página con `#menupasen`.
- `cifrar(s)` = concatenar en decimal los `charCodeAt` de `s` → `x`; resultado `x^3584956249 mod
  356056806984207294102423357623243547284021` en decimal. Verificado con `BigInt` contra el script
  original de Séneca: 307/307 coincidencias.
- Los readers de la web se escriben ahora en `scraper/readers/`. Que el APK use esos mismos readers
  queda para un plan posterior; hasta entonces el APK sigue con sus scripts.

## De dónde saca los datos la web

Una página en `*.github.io` no puede iniciar sesión en Séneca ni leer sus páginas: son orígenes distintos
y Séneca no envía cabeceras CORS. GitHub Pages solo sirve archivos estáticos. Hay dos formas de salvarlo;
la web tiene un único punto de entrada de datos (`fuente`) para admitir las dos:

1. **Marcador (esta versión).** El usuario entra en Séneca, pulsa el marcador y este, desde dentro de
   Séneca (mismo origen), abre las tres secciones con `fetch` + `POST` a sus `data-menulink`, las lee con
   los readers compartidos y envía el JSON a la web. La contraseña nunca sale de Séneca y no hay servidor.
2. **Cloudflare Worker (más adelante).** La web pide usuario y clave y el Worker hace login y navegación
   (plan gratuito: 100 000 peticiones/día, 50 subpeticiones por invocación, 10 ms de CPU sin contar
   esperas de red). Requiere cuenta de Cloudflare; se diseña abajo pero no se construye ahora.

## Resultado de la prueba de viabilidad (2026-10-28)

Hecha con la sesión real del usuario en Opera; el código de la prueba se descartó.

| Paso | Viable desde servidor | Detalle |
|---|---|---|
| Login | Sí | `POST` a `/seneca/jsp/portalpasen/` (misma URL del formulario) con `USUARIO`, `CLAVECIFRADA=cifrar(clave)` (`/seneca/scripts/consejeria/cifrado.js`, RSA determinista sin secretos), `C_INTERFAZ` y los parámetros de `getParametrosAplicacion()`. Respuesta XML `<correcto>SI/NO</correcto><mensaje>`; si SI, se envía el formulario a `../Acceso.jsp?<aleatorio>`. Cookies `JSESSIONID` y `SenecaP`. |
| Abrir sección | Sí | Cada entrada del menú (`#menupasen a.nav-link`) lleva `data-menulink="../menus/<token>"` en el HTML. `Navegacion.llamadaMenu` hace `POST` a esa URL con `COD_PAGINA_ANTERIOR` y `TIEMPO_PAGINA_ANTERIOR`. Las entradas se buscan por ruta de menú, como hoy. |
| Faltas | Sí | "Seguimiento del curso > Faltas de asistencia > Consulta": la tabla `Fecha / Tramo horario / Ausencia de / Estado` viene en el HTML. |
| Horario | Sí | "Tutoría > Horario": tablas `Lunes…Viernes` y `Materias` en el HTML. |
| Calendario | Sí | "Centro > Calendario escolar": los días vienen como JSON en un script (`top.datosCalendario = jQuery.parseJSON('[{"title","start","className"}…]')`, clases `festivoAndalucia`, `festivoProvincial`, `festivoLocalidad`, `primerDiaLectivo`, `ultimoDiaLectivo`). |
| IP de Cloudflare | **Sin probar** | Solo afecta a la fuente Worker (futura). |
| Scripts externos en Séneca | Sí | La página de Séneca con sesión no envía CSP (ni cabecera ni `<meta>`) y carga un `<script src>` de otro dominio. El marcador puede ser un cargador que trae el código desde GitHub Pages. |

Las páginas llegan en `ISO-8859-1`.

## Arquitectura

```
faltas-ipasen-app/
├─ shared/     Kotlin Multiplatform (android + js): modelos, AbsenceCalculator, ThresholdEvaluator,
│              RiskLevel, EducationLevel, CourseDates, History, AlertFormatter, ScrapeParsers
├─ scraper/    JS compartido
│   ├─ readers/    funciones puras sobre un Document: readAbsences, readSchedule, readCalendar
│   └─ webview/    navegación del APK (openSection, waitFor, puente) + pasos que llaman a los readers
├─ app/        APK Android (Compose), depende de shared/ y empaqueta scraper/ como assets
├─ web/        Web estática (GitHub Pages), usa shared/ compilado a JS; incluye bookmarklet.js
└─ worker/     (futuro) Cloudflare Worker: login + navegación por POST; devuelve el HTML de cada sección
```

### Marcador (bookmarklet)

- El marcador es un cargador de una línea que inserta `<script src="https://<usuario>.github.io/<repo>/bookmarklet.js">`
  en la página de Séneca. El código real vive en Pages, así que se actualiza con cada despliegue sin
  que el usuario tenga que volver a crear el marcador.
- `bookmarklet.js` (generado desde `scraper/readers` + un navegador por `fetch`):
  1. Comprueba que está en `seneca.juntadeandalucia.es` con sesión (existe `#menupasen`); si no, avisa.
  2. Muestra una capa "Leyendo faltas…" con el paso actual.
  3. Busca por ruta de menú las entradas de horario, calendario y faltas (misma lógica que el APK) y hace
     `POST` a cada `data-menulink` con `COD_PAGINA_ANTERIOR`/`TIEMPO_PAGINA_ANTERIOR`; decodifica `ISO-8859-1`.
  4. Pasa cada HTML por `DOMParser` y su reader → `{schedule, calendar, absences}`.
  5. Comprime el JSON (`CompressionStream('gzip')` + base64url) y abre la web con los datos en el
     fragmento: `https://<usuario>.github.io/<repo>/#importar=<datos>`. El fragmento no se envía a
     ningún servidor. Abre en la misma pestaña (evita el bloqueo de ventanas emergentes tras esperas).
  6. Errores (menú no encontrado, tabla cambiada, sesión caducada) se muestran en la capa con el paso y
     lo que falta, igual que los diagnósticos del APK.
- La web, al ver `#importar=`, descomprime, procesa con `shared`, guarda y limpia el fragmento con
  `history.replaceState`.
- Usar `fetch` sobre los menús consume esos tokens de un solo uso; tras el marcador, si el usuario sigue
  en Séneca y un menú falla, basta con recargar. Se avisa en la capa final.
- Página "Instalar marcador" en la web: enlace arrastrable en escritorio e instrucciones para Chrome/Opera
  en Android (guardar marcador, editar URL, invocarlo escribiendo su nombre en la barra).

### shared/ (Kotlin Multiplatform)

- Se mueve el código de `domain/` y `scraper/ScrapeParsers.kt` que no depende de Android.
- `java.time.LocalDate` → `kotlinx.datetime.LocalDate`; `java.util.Locale` en `AlertFormatter` y
  `ScrapeParsers` se sustituye por código sin dependencia de plataforma (formato fijo en español).
- Los tests de dominio y de `ScrapeParsers` pasan a `commonTest` (`kotlin.test`) y se ejecutan en JVM y JS.
- La web consume `shared/` como módulo JS exportado (`@JsExport` en una fachada pequeña:
  `calcular(rawJson, ajustes) → resumen`), no la API completa.

### scraper/ (lectores compartidos)

- Hoy cada script navega **y** lee. Se separa:
  - **readers/**: `readAbsences(doc)`, `readSchedule(doc)`, `readCalendar(doc)` reciben un `Document` y
    devuelven el mismo JSON que hoy produce cada paso (`{absences, skipped}`, slots, días). No navegan ni
    esperan.
  - **webview/**: los pasos del APK abren la sección con el menú y esperan a que exista la tabla, como hoy,
    y luego llaman al reader con `document`.
- `readCalendar` pasa a leer `datosCalendario` del HTML en vez de las 12 cuadrículas del datepicker. Así
  vale igual en el WebView y sobre HTML sin ejecutar. Hay que confirmar que `primerDiaLectivo` aparece en
  `datosCalendario` para mantener la regla de inicio de FP.
- La web usa los readers con `new DOMParser().parseFromString(html, 'text/html')`. Como `innerText` no
  existe sin maquetación, los readers usan `textContent` normalizado.

### worker/ (futuro, no se construye en esta versión)

- `POST /sync` con `{usuario, clave}` → en la misma invocación: GET del portal, login, `Acceso.jsp`,
  localizar las tres entradas del menú por ruta y hacer `POST` a cada una → responde
  `{schedule: html, calendar: html, absences: html}`.
- Sin estado entre peticiones; no guarda ni registra credenciales ni HTML.
- CORS solo para el origen de GitHub Pages. Límite de peticiones por IP.
- Errores con códigos estables: `LOGIN_FAILED` (con el mensaje de Séneca), `LOCKED` (usuario bloqueado o
  clave caducada), `MENU_NOT_FOUND` (con las rutas disponibles), `UPSTREAM_BLOCKED` (Séneca rechaza la
  conexión), `UPSTREAM_ERROR`, `TIMEOUT` (con el paso).

### web/

- HTML, CSS y JS sin framework pesado; mismo aspecto que el APK: tema índigo (`#3949AB`), Inter, temas
  Sistema/Claro/Oscuro/AMOLED, barra inferior Resumen · Historial · Ajustes.
- Pantallas: Bienvenida (nivel, umbral y fecha de fin con los mismos valores por defecto que el APK, más
  instalar el marcador), Resumen, Historial (filtrable por módulo), Ajustes (incluye borrar datos).
- "Sincronizar" en esta versión = abrir Séneca en otra pestaña y explicar que se pulse el marcador. Sin
  sincronización en segundo plano ni push; los avisos de riesgo se muestran dentro de la web.
- Con la fuente Worker (futura) la Bienvenida pedirá también usuario y clave, como el APK.
- Instalable como PWA (manifest + service worker solo para los archivos de la web, nunca para datos).
- Datos (último resultado, historial, ajustes) en `localStorage`/IndexedDB del navegador.

## Flujo de una sincronización (web, con marcador)

1. El usuario entra en Séneca y pulsa el marcador.
2. El marcador lee las tres secciones dentro de Séneca y produce el JSON en bruto con los readers.
3. Abre la web con el JSON comprimido en `#importar=`.
4. `shared` (`ScrapeParsers` + cálculo) produce el resumen, idéntico al del APK.
5. Se guarda el resultado y se actualiza el historial.

Con la fuente Worker (futura), los pasos 1–3 se sustituyen por `POST /sync` + readers en la web.

## Contraseñas y seguridad

- Con el marcador la web nunca ve la contraseña.
- La web solo acepta `#importar=` con el formato esperado (versión, tres claves, validación de `shared`);
  cualquier otra cosa se descarta con un mensaje.
- (Fuente Worker, futura) "Recordar contraseña" es opcional. Si se marca, la clave se cifra con AES-GCM usando una `CryptoKey`
  no exportable guardada en IndexedDB (equivalente al Keystore del APK). Si no, se pide en cada sincronización.
- Tras `LOGIN_FAILED` o `LOCKED` no se reintenta automáticamente (evita el bloqueo de Séneca), igual que el APK.
- Cabecera CSP estricta en la web; sin scripts de terceros.
- Las muestras de HTML de los tests usan datos inventados; nunca datos reales del usuario.

## Errores (web)

Mismos mensajes que el APK para los casos comunes (página cambiada con lo que falta, sesión caducada,
sin conexión), mostrados en la capa del marcador o en la web según dónde ocurran. (Fuente Worker: además
clave incorrecta y "Séneca ha rechazado la conexión del servidor".) Las filas de faltas ilegibles se muestran como en el APK (`SkippedAbsencesNote`).

## Pruebas

- `shared`: tests actuales en `commonTest`, ejecutados en JVM y JS.
- `readers`: tests con HTML de muestra (anonimizado), ejecutados en Node con un DOM (jsdom/linkedom) y
  comparados con el JSON esperado.
- Marcador: test del navegador por `fetch` con respuestas simuladas; prueba real en la sesión de Séneca
  del usuario en Opera antes de publicar.
- Codificación `#importar=`: ida y vuelta (comprimir → descomprimir) en tests.
- APK: tras cada fase que lo toca, instalación por adb en el móvil del usuario y sincronización real.
- GitHub Actions: tests en cada push; despliegue de Pages solo desde `main` con todo en verde.

## Orden de trabajo

1. `shared/` + migración a `kotlinx-datetime`; el APK sigue igual (verificado en el móvil).
2. Separar readers de la navegación; `readCalendar` con `datosCalendario` (verificado en el móvil).
3. Web + marcador.
4. Repo público en GitHub (revisado antes de subir), GitHub Actions y Pages.
5. (Futuro, cuando el usuario quiera) Worker de Cloudflare como segunda fuente.

## Fuera de alcance (por ahora)

Worker de Cloudflare; sincronización en segundo plano y notificaciones push en la web; varias cuentas a
la vez; clave de firma del APK para release.
