# Faltas iPasen

App Android que lee tus faltas de asistencia de iPasen (Séneca, Junta de Andalucía) y te dice qué
porcentaje llevas en cada módulo o asignatura frente al límite de tu etapa, antes de que sea tarde.

> No es una app oficial ni está relacionada con la Junta de Andalucía. Usa tu propia cuenta de iPasen
> igual que lo harías desde el navegador.

**Descarga:** [última versión (APK)](https://github.com/VKLF-OFFICIAL/faltas-ipasen/releases/latest) ·
**Web:** <https://vklf-official.github.io/faltas-ipasen/>

## Qué hace

- Inicia sesión en iPasen con tu usuario y lee tu horario, el calendario escolar y tus faltas.
- Calcula el % de faltas de cada módulo y el total, sobre las horas lectivas del curso hasta la fecha
  de fin (o hasta el inicio de la FCT en FP).
- Umbral según la etapa (ESO, Bachillerato, Grado Básico, Medio, Superior), editable.
- Semáforo por módulo, horas de margen, faltas justificadas e injustificadas.
- Historial día a día, filtrable por módulo.
- Sincronización en segundo plano y aviso cuando superas el umbral.
- Tema claro, oscuro y AMOLED.

## Privacidad

- Tu usuario y contraseña se guardan cifrados con una clave del Android Keystore y solo se envían a
  iPasen.
- Las faltas y el horario se guardan solo en tu móvil. No hay servidores propios ni analítica.

## Instalar

1. Descarga `faltas-ipasen.apk` desde [Releases](https://github.com/VKLF-OFFICIAL/faltas-ipasen/releases/latest).
2. Ábrelo en el móvil y permite instalar apps de este origen cuando Android lo pida.
3. Requiere Android 8.0 o superior.

## Compilar

Requisitos: Android Studio (o su JDK 17 en `jbr/`) y el SDK de Android.

```bash
./gradlew :shared:allTests :app:testDebugUnitTest   # tests (JVM y Node)
./gradlew :app:assembleDebug                         # APK de depuración
```

El release se firma con una clave que no está en el repositorio (ver `app/build.gradle.kts`); sin ella
`assembleRelease` genera un APK sin firmar.

## Estructura

- `app/` — app Android (Kotlin, Jetpack Compose). Los scripts que leen iPasen están en `app/src/main/assets/scraper/`.
- `shared/` — lógica común en Kotlin Multiplatform (cálculo de faltas, umbrales, historial), compartida con la futura versión web.
- `web/` — página de GitHub Pages.
- `docs/` — diseño y planes.

## Licencia

[PolyForm Noncommercial 1.0.0](LICENSE): puedes usarlo, estudiarlo y modificarlo para fines no comerciales.
