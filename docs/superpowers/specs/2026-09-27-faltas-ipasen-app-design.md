# App de control de faltas de asistencia (iPasen) — Diseño

**Fecha:** 2026-09-27
**Estado:** Aprobado por el usuario, pendiente de plan de implementación

## Objetivo

App Android nativa que obtiene automáticamente las faltas de asistencia del
usuario desde iPasen (portal de familias/alumnado de la Junta de Andalucía) y
le avisa mediante notificación push cuando su porcentaje de faltas, calculado
sobre las horas lectivas reales del curso, supera un umbral configurado.

Principio rector: **mínima interacción del usuario**. Todo dato que iPasen
pueda proporcionar se obtiene automáticamente; el usuario solo introduce lo
que es imposible de sacar de iPasen.

Caso de uso concreto: ciclo formativo (FP) donde las clases lectivas terminan
en una fecha concreta al empezar la FCT (formación en centros de trabajo), y
las faltas solo cuentan hasta esa fecha.

## Contexto de investigación (ya resuelto durante el diseño)

- iPasen no tiene API pública ni documentada.
- No existe ninguna librería o cliente no oficial mantenido para iPasen
  (verificado por búsqueda en GitHub/PyPI en sept. 2026).
- iPasen expone, dentro de su interfaz web, tanto el horario semanal del
  alumno como un calendario que marca qué días son lectivos/no lectivos para
  su centro. Ambos son accesibles vía scraping del HTML renderizado.
- No existe una fuente automática fiable para el calendario escolar oficial
  completo fuera de iPasen (solo PDFs oficiales por curso/provincia, sin
  JSON/API), por lo que se descarta como fuente y se usa el calendario propio
  de iPasen en su lugar.
- La fecha de corte de FCT es específica del ciclo/grupo del alumno y no está
  representada de forma fiable en el calendario general del centro dentro de
  iPasen, así que se pide al usuario una única vez (ver más abajo).

## Entradas manuales del usuario (mínimas, por diseño)

Estas son las únicas interacciones manuales necesarias, todas configuradas
una vez en la pantalla de Ajustes:

1. **Credenciales de iPasen** (usuario y contraseña) — inevitable, es lo
   único que autentica al usuario contra iPasen.
2. **Umbral de aviso (%)** — preferencia personal, iPasen no la tiene.
3. **Fecha de fin de clases lectivas / inicio de FCT** — específica del
   ciclo del alumno, no deducible de forma fiable desde iPasen.

Todo lo demás (horario semanal, calendario lectivo/no lectivo del centro,
histórico de faltas, cálculo del %, sincronización y disparo de avisos) es
automático, sin intervención del usuario.

## Arquitectura

Componentes (Android nativo, Kotlin, Jetpack Compose):

- **`WebViewScraper`**: un `WebView` sin interfaz visible que:
  1. Carga la página de login de iPasen e inyecta las credenciales
     guardadas para autenticarse.
  2. Navega a la página de horario semanal y extrae, vía JS inyectado
     (`querySelector`/`querySelectorAll` sobre el DOM ya renderizado),
     las asignaturas/módulos con sus horas por día de la semana.
  3. Navega a la página de calendario del centro y extrae qué días del
     periodo (inicio de curso → fecha de corte FCT) son lectivos.
  4. Navega a la página de faltas de asistencia y extrae el histórico
     (fecha, asignatura, hora, justificada/no justificada).
  5. Devuelve los datos extraídos a Kotlin vía `addJavascriptInterface`.
- **`SyncWorker`** (WorkManager, tarea periódica ~1 vez/día, configurable):
  orquesta el `WebViewScraper`, persiste los datos en Room, recalcula el %
  de faltas y dispara notificación si corresponde. Si WorkManager restringe
  el uso de WebView en segundo plano, se usará un `Foreground Service`
  breve (con notificación discreta) durante la sincronización como
  alternativa.
- **Almacenamiento local**: cada sincronización sustituye entera la foto de
  datos (horario, calendario lectivo, faltas), así que se guarda como un único
  fichero JSON (`kotlinx.serialization`) en lugar de Room: no hay consultas ni
  migraciones que justifiquen una base de datos. La configuración (umbral,
  fecha de corte FCT, frecuencia, estado de avisos) va en `SharedPreferences`.
- **`NotificationManager`**: notificación push cuando el % de faltas (global
  o por asignatura) cruza el umbral configurado; evita reavisar
  repetidamente por el mismo cruce.
- **UI (Compose)**:
  - Pantalla principal: % global de faltas, barra de progreso hacia el
    umbral, última sincronización, botón de sincronizar manualmente.
  - Detalle por asignatura: % individual y horas de falta por asignatura.
  - Ajustes: credenciales, umbral de aviso, fecha de corte FCT, frecuencia
    de sincronización.

## Cálculo del porcentaje de faltas

1. Horas totales del curso = para cada día marcado como lectivo en el
   calendario de iPasen entre el inicio de curso (1 de septiembre) y la
   `fecha_corte_FCT` (incluidos los días futuros), sumar las horas de clase
   que correspondan a ese día de la semana según el horario.
2. Horas de falta = faltas registradas entre el inicio de curso y
   `min(hoy, fecha_corte_FCT)`.
3. % de faltas = (horas de falta ÷ horas totales del curso) × 100, tanto a
   nivel global como desglosado por asignatura/módulo. Es la base del aviso.
   (Decisión del usuario 2026-09-28: base = horas totales hasta el corte,
   no horas dadas hasta hoy.)
4. Además se muestra el margen: horas de falta que quedan antes de llegar al
   umbral (`umbral% × horas totales − horas de falta`).
5. El cálculo se implementa como lógica pura (independiente del scraping),
   para poder testearla con datos de horario/calendario/faltas simulados.

## Seguridad y almacenamiento

- Credenciales de iPasen: cifradas con AES-256-GCM usando una clave del
  Android Keystore (no exportable) y guardadas en `SharedPreferences`. Se usa
  el Keystore directamente porque `EncryptedSharedPreferences`
  (androidx.security-crypto) está deprecado. Nunca en texto plano, nunca en
  logs. Copia de seguridad de Android desactivada (`allowBackup=false`).
- Toda comunicación va directa a los dominios oficiales de iPasen/Séneca por
  HTTPS. No hay servidor intermedio propio ni de terceros; todo el
  procesamiento ocurre en el dispositivo.

## Riesgos conocidos y cómo se abordan

- **Cambios en el HTML de iPasen**: el scraper puede romperse si Séneca
  cambia el diseño de sus páginas. No hay forma de evitarlo sin una API
  oficial; se documentarán los selectores usados para facilitar su
  actualización.
- **WebView en segundo plano restringido por Android**: mitigado con
  `Foreground Service` como alternativa si `WorkManager` + `WebView` en
  background da problemas.
- **Construcción del scraper**: como iPasen requiere credenciales reales del
  usuario, los selectores exactos del DOM se determinarán de forma empírica
  durante la implementación, inspeccionando las páginas reales con el
  usuario (vía Chrome DevTools) en una sesión de desarrollo puntual.

## Testing

- Tests unitarios (JUnit) sobre la lógica de cálculo de horas
  totales/porcentaje, usando datos de horario/calendario/faltas simulados —
  no dependen de iPasen ni del scraper.
- El scraper no es testeable de forma automatizada por depender del HTML
  real vivo de iPasen; se valida manualmente contra la cuenta real del
  usuario durante el desarrollo.

## Fuera de alcance (v1)

- Detección automática de días no lectivos fuera del calendario que expone
  iPasen.
- Detección automática de la fecha de corte de FCT (queda como posible
  mejora futura, no en v1).
- Soporte multiusuario / multi-centro / iOS.
