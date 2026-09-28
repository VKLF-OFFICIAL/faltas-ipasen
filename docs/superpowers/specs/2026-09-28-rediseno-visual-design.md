# Rediseño visual y temas — Diseño

**Fecha:** 2026-09-28
**Estado:** Aprobado por el usuario ("Hazlo por favor")
**Parte de:** app Faltas iPasen (spec base: `2026-09-27-faltas-ipasen-app-design.md`)

## Objetivo

Dar a la app una estética pulida, limpia y con identidad propia, con tema claro/oscuro configurable
desde Ajustes, y añadir la información que el usuario pidió: historial de faltas, curso transcurrido
y desglose justificadas/injustificadas.

## Decisiones del usuario (2026-09-28)

| Tema | Decisión |
|---|---|
| Estilo | Minimalista y limpio: tarjetas planas, mucho aire, color solo donde informa |
| Color de marca | Paleta fija índigo del icono (#3949AB); se deja de usar Material You |
| Temas | Sistema (por defecto) · Claro · Oscuro · Oscuro AMOLED (negro puro) |
| Nombres de módulo | Abreviatura en grande + nombre completo en minúsculas normales debajo |
| Semáforo | Verde < 50 % del umbral · Ámbar 50 %–umbral · Rojo ≥ umbral |
| Contenido extra | Historial de faltas · Curso transcurrido · Justificadas vs injustificadas |
| Tipografía | Inter, incluida en la app (licencia OFL) |
| Orden de módulos | Por % de faltas, mayor primero |
| Navegación | Barra inferior: Resumen · Historial · Ajustes |
| Tocar un módulo | Abre el Historial filtrado por ese módulo |
| Animaciones | Sutiles |
| Bienvenida | Rediseñada con el mismo estilo |

## Identidad visual

- **Paleta** (tokens de `ColorScheme` de Material 3):
  - Claro: primary `#3949AB`, onPrimary `#FFFFFF`, primaryContainer `#E0E3FF`, onPrimaryContainer `#0A1570`,
    background `#F7F8FC`, surface `#FFFFFF`, surfaceVariant/surfaceContainer `#EEF0F7`, onSurface `#16181F`,
    onSurfaceVariant `#5A5F6E`, outline `#D7DAE5`, outlineVariant `#E6E8F0`.
  - Oscuro: primary `#B8C3FF`, onPrimary `#0E1D78`, primaryContainer `#2C3A8F`, onPrimaryContainer `#DDE1FF`,
    background `#111318`, surface `#1A1C22`, surfaceVariant/surfaceContainer `#23262E`, onSurface `#E3E4EA`,
    onSurfaceVariant `#A7ABB8`, outline `#3A3E49`, outlineVariant `#2C2F38`.
  - Oscuro AMOLED: como Oscuro pero background `#000000`, surface `#0B0C0F`, surfaceVariant/surfaceContainer `#16181D`,
    outlineVariant `#1F2127`.
- **Semáforo** (colores semánticos propios, no del `ColorScheme`): claro — bajo `#1E8E5A`, medio `#C77700`,
  alto `#D32F2F`; oscuro/AMOLED — bajo `#5FD39A`, medio `#FFB547`, alto `#FF6B6B`. Fondos de etiqueta con el
  mismo color al 14 % de opacidad.
- **Tipografía:** Inter 400/500/600/700 (TTF estáticos en `res/font`, licencia OFL en
  `assets/licenses/Inter-OFL.txt`). Cifras tabulares (`"tnum"`) en porcentajes y horas. El % principal a 56 sp
  SemiBold.
- **Formas:** tarjetas con esquinas de 20 dp, sin sombra (elevación 0), borde `outlineVariant` de 1 dp; barras de
  progreso finas (6 dp principal, 4 dp por módulo) con extremos redondeados.
- **Barras del sistema:** edge-to-edge; iconos de la barra de estado claros u oscuros según el tema activo (no
  el del sistema).

## Tema configurable

- Nuevo ajuste `themeMode` ∈ {SYSTEM, LIGHT, DARK, AMOLED}, por defecto SYSTEM, persistido en las
  `SharedPreferences` de ajustes.
- SYSTEM sigue al móvil (claro u oscuro normal). AMOLED solo se aplica si se elige explícitamente.
- El cambio se aplica al instante con transición de color animada (~300 ms).

## Pantallas

**Estructura:** `Scaffold` con `NavigationBar` inferior (Resumen · Historial · Ajustes) cuando hay sesión; sin
sesión se muestra solo la Bienvenida. Transición suave (fundido) al cambiar de pestaña.

**Resumen**
1. Cabecera: título "Resumen", icono de sincronizar (gira mientras sincroniza) y "Actualizado hace X min".
   Tirar hacia abajo también sincroniza.
2. Bloque principal: % de faltas sobre el total del curso (color semáforo, animado al cambiar), texto
   "de faltas del curso", barra hasta el umbral con la etiqueta del umbral, y "Te quedan N h de margen"
   (o "Has superado tu umbral del X %" en rojo).
3. Desglose: dos cifras lado a lado — "Injustificadas" y "Justificadas" en horas.
4. Curso transcurrido: "Llevas N de M días lectivos (P %)" con barra fina neutra.
5. "Módulos": una fila por módulo — abreviatura en negrita, nombre completo debajo (tamaño pequeño,
   `onSurfaceVariant`, máximo 2 líneas), % a la derecha con color semáforo, barra fina, "x h de y h ·
   margen z h". Ordenados por % desc. Tocar una fila abre Historial filtrado por ese módulo. Los módulos sin
   horas en el horario muestran "No está en tu horario".
6. Error de la última sincronización (si hay) como aviso discreto en rojo.
7. Estado vacío (sin datos aún): el icono de la app y "Sincronizando con iPasen…" o botón "Sincronizar".

**Historial**
- Filtros en fila desplazable: "Todos" + un chip por módulo con faltas (abreviatura). El filtro llega
  preseleccionado al venir desde un módulo.
- Faltas del curso (desde el inicio de curso hasta hoy/corte) agrupadas por fecha, de la más reciente a la
  más antigua; encabezado de grupo "Jueves, 24 de septiembre". Cada falta: tramo ("8:15 – 9:15"),
  abreviatura + nombre, horas, y etiqueta "Justificada" (verde) o "Injustificada" (rojo).
- Resumen del filtro arriba: "N faltas · X h".
- Vacío: "Sin faltas registradas. ¡Sigue así!".

**Ajustes** (grupos con título)
- Apariencia: selector segmentado Sistema · Claro · Oscuro · AMOLED (se aplica al tocar).
- Avisos: umbral (%) y fecha de fin de clases (inicio FCT).
- Sincronización: frecuencia 6 · 12 · 24 h.
- Cuenta: usuario, nueva contraseña (vacío = no cambiar), botón Guardar, "Cerrar sesión y borrar datos"
  con confirmación.
- Pie: "Versión 1.x · Datos solo en este móvil".

**Bienvenida:** icono de la app (80 dp) arriba, "Tus faltas de iPasen, bajo control", campos usuario y
contraseña, fecha de fin de clases y umbral (prerrellenados), botón "Empezar", y caja informativa "Tu usuario
y contraseña se guardan cifrados solo en este móvil".

## Cambios de datos necesarios

- `schedule.js` devuelve además `abbr` por sesión; `absences.js` devuelve además `tramo` ("8:15 - 9:15").
- `ScheduleSlot` añade `abbreviation` y `name` (nombre legible, sin pasar a mayúsculas); `Absence` añade
  `tramo` y `name`. Todos con valor por defecto para que el snapshot ya guardado en el móvil siga leyéndose.
  `subject` sigue siendo la clave normalizada (mayúsculas) para cruzar horario y faltas.
- `HoursStats` añade `abbreviation` y `name` para mostrar; `AbsenceStats` añade `lectivoDaysElapsed` y
  `lectivoDaysTotal` (días lectivos desde el inicio de curso hasta hoy/corte, y hasta el corte).
- Nuevo `RiskLevel` {LOW, MEDIUM, HIGH} calculado a partir de % y umbral.
- Las notificaciones usan la abreviatura del módulo (más corta) cuando existe.

## Testing

- Unitarios: parsers con los nuevos campos (y compatibilidad sin ellos), `RiskLevel`, días lectivos
  transcurridos, construcción del historial (filtro, orden, agrupación), `themeMode` en ajustes, lectura de un
  snapshot antiguo sin los campos nuevos.
- Revisión por tarea (SDD) y verificación final en el móvil del usuario: capturas de Resumen, Historial y
  Ajustes en los 4 temas.

## Fuera de alcance

- Widgets de pantalla de inicio, gráficas históricas por semana, exportar datos.
