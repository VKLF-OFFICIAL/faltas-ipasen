# Versión web — Diseño

**Fecha:** 2026-10-28
**Estado:** Pendiente de revisión del usuario
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

## Por qué hace falta un servidor intermediario

Una página en `*.github.io` no puede iniciar sesión en Séneca ni leer sus páginas: son orígenes distintos
y Séneca no envía cabeceras CORS. GitHub Pages solo sirve archivos estáticos. Por eso un **Cloudflare
Worker** (plan gratuito: 100 000 peticiones/día, 50 subpeticiones por invocación, 10 ms de CPU sin contar
esperas de red) hace de intermediario mínimo. Se despliega desde el mismo repo con GitHub Actions.

## Resultado de la prueba de viabilidad (2026-10-28)

Hecha con la sesión real del usuario en Opera; el código de la prueba se descartó.

| Paso | Viable desde servidor | Detalle |
|---|---|---|
| Login | Sí | `POST` a `/seneca/jsp/portalpasen/` (misma URL del formulario) con `USUARIO`, `CLAVECIFRADA=cifrar(clave)` (`/seneca/scripts/consejeria/cifrado.js`, RSA determinista sin secretos), `C_INTERFAZ` y los parámetros de `getParametrosAplicacion()`. Respuesta XML `<correcto>SI/NO</correcto><mensaje>`; si SI, se envía el formulario a `../Acceso.jsp?<aleatorio>`. Cookies `JSESSIONID` y `SenecaP`. |
| Abrir sección | Sí | Cada entrada del menú (`#menupasen a.nav-link`) lleva `data-menulink="../menus/<token>"` en el HTML. `Navegacion.llamadaMenu` hace `POST` a esa URL con `COD_PAGINA_ANTERIOR` y `TIEMPO_PAGINA_ANTERIOR`. Las entradas se buscan por ruta de menú, como hoy. |
| Faltas | Sí | "Seguimiento del curso > Faltas de asistencia > Consulta": la tabla `Fecha / Tramo horario / Ausencia de / Estado` viene en el HTML. |
| Horario | Sí | "Tutoría > Horario": tablas `Lunes…Viernes` y `Materias` en el HTML. |
| Calendario | Sí | "Centro > Calendario escolar": los días vienen como JSON en un script (`top.datosCalendario = jQuery.parseJSON('[{"title","start","className"}…]')`, clases `festivoAndalucia`, `festivoProvincial`, `festivoLocalidad`, `primerDiaLectivo`, `ultimoDiaLectivo`). |
| IP de Cloudflare | **Sin probar** | Hay que desplegar un Worker de prueba para saber si Séneca acepta conexiones desde Cloudflare. |

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
├─ web/        Web estática (GitHub Pages), usa shared/ compilado a JS y scraper/readers
└─ worker/     Cloudflare Worker: login + navegación por POST; devuelve el HTML de cada sección
```

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

### worker/

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
- Pantallas: Bienvenida/acceso (usuario, clave, nivel, umbral y fecha de fin con los mismos valores por
  defecto que el APK), Resumen, Historial (filtrable por módulo), Ajustes (incluye cerrar sesión).
- Sincroniza al abrir y con el botón "Sincronizar". Sin sincronización en segundo plano ni push en esta
  versión; los avisos de riesgo se muestran dentro de la web.
- Instalable como PWA (manifest + service worker solo para los archivos de la web, nunca para datos).
- Datos (último resultado, historial, ajustes) en `localStorage`/IndexedDB del navegador.

## Flujo de una sincronización (web)

1. La web envía `POST /sync` al Worker por HTTPS.
2. El Worker devuelve el HTML de horario, calendario y faltas o un código de error.
3. La web pasa cada HTML por su reader → JSON en bruto.
4. `shared` (`ScrapeParsers` + cálculo) produce el resumen, idéntico al del APK.
5. Se guarda el resultado y se actualiza el historial.

## Contraseñas y seguridad

- "Recordar contraseña" es opcional. Si se marca, la clave se cifra con AES-GCM usando una `CryptoKey`
  no exportable guardada en IndexedDB (equivalente al Keystore del APK). Si no, se pide en cada sincronización.
- Tras `LOGIN_FAILED` o `LOCKED` no se reintenta automáticamente (evita el bloqueo de Séneca), igual que el APK.
- Cabecera CSP estricta en la web; sin scripts de terceros.
- Las muestras de HTML de los tests usan datos inventados; nunca datos reales del usuario.

## Errores (web)

Mismos mensajes que el APK para los casos comunes (clave incorrecta, tiempo agotado con el paso, página
cambiada con lo que falta, sin conexión), más "Séneca ha rechazado la conexión del servidor" para
`UPSTREAM_BLOCKED`. Las filas de faltas ilegibles se muestran como en el APK (`SkippedAbsencesNote`).

## Pruebas

- `shared`: tests actuales en `commonTest`, ejecutados en JVM y JS.
- `readers`: tests con HTML de muestra (anonimizado), ejecutados en Node con un DOM (jsdom/linkedom) y
  comparados con el JSON esperado.
- `worker`: tests contra un Séneca simulado (respuestas grabadas y anonimizadas).
- APK: tras cada fase que lo toca, instalación por adb en el móvil del usuario y sincronización real.
- GitHub Actions: tests en cada push; despliegue de Pages y del Worker solo desde `main` con todo en verde.

## Orden de trabajo

1. `shared/` + migración a `kotlinx-datetime`; el APK sigue igual (verificado en el móvil).
2. Separar readers de la navegación; `readCalendar` con `datosCalendario` (verificado en el móvil).
3. Worker + prueba de IP de Cloudflare (requiere cuenta gratuita del usuario). **Si Séneca bloquea a
   Cloudflare, se para aquí** y se buscan alternativas antes de construir la web.
4. Web.
5. Repo público en GitHub, GitHub Actions, Pages y despliegue del Worker.

## Fuera de alcance (por ahora)

Sincronización en segundo plano y notificaciones push en la web; varias cuentas a la vez; clave de firma
del APK para release.
