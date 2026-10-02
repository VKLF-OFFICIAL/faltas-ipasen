# iPasen: cómo lo lee la app (capturado el 2026-09-28)

La app usa el **portal web de PASEN** (Séneca), que muestra los mismos datos que la app iPasen.
No hay endpoints JSON reutilizables: las secciones son páginas HTML generadas en el servidor con
URLs de un solo uso (`/seneca/nav/<token>`), así que los scripts leen el DOM y navegan con el menú
de Séneca (`Navegacion.llamadaMenu(<elemento del menú>)`), que recarga la página. El motor vuelve a
inyectar el script tras cada recarga (`WebViewScraper.runStep`), y el segundo intento encuentra la
sección abierta (`openSection` en `common.js`).

## Login
- URL: `https://seneca.juntadeandalucia.es/seneca/jsp/portalpasen/`
- Usuario / contraseña / botón: `#USUARIO` / `#CLAVE_P` / `#Entrar` (llama a `comprobarclave()` y envía el formulario `FORMULARIO_IDEN_USU` por POST a la misma URL).
- Selección adicional (perfil/centro): ninguna para una cuenta de alumno con un solo perfil.
- Indicador de sesión iniciada: enlace `a[href="javascript:logout()"]` ("Salir").
- Error de credenciales: la página de login vuelve con `#loginMessage` con clase `show` y el texto "Usuario y/o contraseña incorrecto".
- Hosts visitados: `seneca.juntadeandalucia.es` (sufijo permitido: `juntadeandalucia.es`).
- Al pulsar "atrás" Séneca puede lanzar un `confirm("¿Está seguro de que desea salir de la aplicación?")`; el WebView sin `WebChromeClient` lo responde como "cancelar".

## Menú (capturado de nuevo el 2026-10-02)
- Cada opción es `a.nav-link[onclick="Navegacion.llamadaMenu(this)"]` con id `h<número>` (a veces negativo, `h-560752947`), dentro de `li.nav-item`; los submenús son `li.submenu > a` (título) + `ul.collapse`, todo bajo `#menupasen`.
- **Los ids y las opciones cambian según la cuenta**: a otro alumno le faltaba `#h1448725599`. Los scripts usan el id conocido solo como atajo y, si no está, buscan la opción por su ruta de menú (`openSection` en `common.js`); si tampoco la encuentran, el error lista las opciones del menú de esa cuenta.
- `Navegacion` puede no existir todavía cuando el menú ya está en el DOM: se espera y, si no aparece, se pulsa la opción.
- Ruta en la cuenta de referencia: Seguimiento del curso > Faltas de asistencia > Consulta; Tutoría > Horario; Centro > Calendario escolar.

## Horario
- Menú: Tutoría > Horario, elemento `#h1448729255`. Título de la página: "HORARIO ESCOLAR DEL ALUMNO".
- Tabla de horario: primera fila `| Lunes | Martes | Miércoles | Jueves | Viernes`; cada fila empieza con el tramo (`8:15 - 9:15`); cada celda contiene `span.texto-SNC` con líneas separadas por `<br>`: abreviatura del módulo y, debajo, docentes (y a veces una actividad extra, p. ej. "Tutoría con alumnado").
- Tabla "Materias": filas `Abreviatura | Descripción | Docentes`. El script traduce la abreviatura al nombre completo (el que aparece en las faltas) y descarta las celdas cuya primera línea no es un módulo (recreo, "Tutoría para tareas administrativas").
- Duración de cada sesión: fin − inicio del tramo (1 h normalmente, 0,5 h en 11:15 - 11:45).

## Calendario
- Menú: Centro > Calendario escolar, elemento `#h1448729343`. Título: "CALENDARIO GRÁFICO DE LA LOCALIDAD".
- Muestra los 12 meses del curso seleccionado en `#C_ANNO` (por defecto el curso actual) como `table.ui-datepicker-calendar`, con título `.ui-datepicker-title` ("Septiembre  2026").
- Clases de las celdas: `ui-datepicker-week-end` (fin de semana), `festivoAndalucia`, `festivoProvincial`, `festivoLocalidad` (no lectivos), `primerDiaLectivo ultimoDiaLectivo` (inicio o fin de cada etapa educativa; en septiembre de 2026 marca los días 3, 10 y 15).
- Regla de la app: no lectivo = fin de semana o cualquier `festivo*`; además, los días anteriores a la **última** marca `primerDiaLectivo` de septiembre (inicio de FP y Bachillerato/ESO, el 15) son no lectivos.

## Faltas
- Menú: Faltas de asistencia > Consulta, elemento `#h1448725599`. Título: "FALTAS DE ASISTENCIA DEL ALUMNO".
- Filtro `#C_TIPFAL` (T = Todas, por defecto; I = Injustificadas; J = Justificadas; R = Retraso).
- Tabla: `Fecha | Tramo horario | Ausencia de | Estado`. Fecha `dd/mm/aaaa`; "Ausencia de" es el nombre completo del módulo (igual que la tabla Materias del horario); Estado `Injustificada` / `Justificada` / `Retraso`.
- Los retrasos se descartan; la duración de cada falta es la del tramo.
- Con cero faltas la página muestra "Número total de registros: 0". No se ha podido comprobar si la tabla se pagina con muchos registros.

## Si la app deja de funcionar
Repetir esta captura (Chrome con DevTools, vista móvil, sesión iniciada por el propio usuario) y
actualizar los scripts de `app/src/main/assets/scraper/`.
