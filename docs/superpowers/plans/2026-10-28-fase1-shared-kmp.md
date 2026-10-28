# Fase 1 — Módulo `shared/` (Kotlin Multiplatform) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mover la lógica sin dependencias de Android (dominio + `ScrapeParsers`) a un módulo Kotlin Multiplatform `shared/` que compila para JVM (lo usa el APK) y JS (lo usará la web), sin cambiar el comportamiento del APK.

**Architecture:** Nuevo módulo Gradle `:shared` con el plugin `kotlin-multiplatform` y dos targets: `jvm()` (el APK depende de él como de cualquier librería JVM) y `js(IR) { nodejs() }`. Se mantienen los paquetes `com.faltasipasen.app.domain` y `com.faltasipasen.app.scraper`, así que los imports del APK no cambian. `java.time.LocalDate` pasa a `kotlinx.datetime.LocalDate` en todo el APK; solo se convierte a `java.time` donde se usan APIs exclusivas de Java (formateo de fechas y el selector de fecha de Compose).

**Tech Stack:** Kotlin 2.2.20, kotlin-multiplatform, kotlinx-serialization 1.9.0, kotlinx-datetime 0.7.1, kotlin.test, AGP 8.13.0, JUnit 4 (tests que se quedan en `app/`).

**Spec:** `docs/superpowers/specs/2026-10-28-version-web-design.md` (sección "shared/ (Kotlin Multiplatform)" y "Orden de trabajo" paso 1).

## Global Constraints

- El APK debe comportarse exactamente igual: mismos textos, mismos cálculos, mismo formato guardado en disco (`Snapshot` en JSON con fechas ISO `"2026-09-14"`; `cutoff` en preferencias como `"2027-02-15"`).
- Paquetes sin cambios: `com.faltasipasen.app.domain`, `com.faltasipasen.app.scraper`.
- JVM target 17 en todos los módulos (igual que `app/`).
- Compilar requiere `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`. En Git Bash: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`.
- Tests: `./gradlew :shared:allTests :app:testDebugUnitTest` (ejecuta `commonTest` en JVM **y** Node; Gradle descarga Node solo).
- Commits en la rama `feat/app-faltas`, terminando con `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- No commitear `graphify-out/` (no está en el repo).

### Tabla de equivalencias `java.time` → `kotlinx.datetime` (para todas las tareas)

| java.time | kotlinx.datetime |
|---|---|
| `import java.time.LocalDate` | `import kotlinx.datetime.LocalDate` |
| `LocalDate.of(2026, 9, 14)` | `LocalDate(2026, 9, 14)` |
| `LocalDate.parse(s)` (lanza `DateTimeParseException`) | `LocalDate.parse(s)` (lanza `IllegalArgumentException`) |
| `a.isBefore(b)` / `a.isAfter(b)` | `a < b` / `a > b` |
| `!a.isBefore(b)` / `!a.isAfter(b)` | `a >= b` / `a <= b` |
| `date.dayOfWeek.value` | `date.dayOfWeek.isoDayNumber` (`import kotlinx.datetime.isoDayNumber`) |
| `date.monthValue` | `date.month.number` (`import kotlinx.datetime.number`) |
| `minOf(a, b)` | igual (`LocalDate` es `Comparable`) |
| `@Serializable(with = LocalDateSerializer::class)` | nada: `kotlinx.datetime.LocalDate` es serializable como ISO por defecto |
| pasar a/desde java (solo en `app/`) | `date.toJavaLocalDate()` / `javaDate.toKotlinLocalDate()` (`import kotlinx.datetime.toJavaLocalDate` / `toKotlinLocalDate`) |

---

### Task 1: Crear `shared/` y mover el código sin fechas

Mueve `EducationLevel`, `RiskLevel` y `AlertFormatter` (más sus tests) a `shared/`, y hace que el APK dependa de `:shared`. `AlertFormatter` deja de usar `java.util.Locale`.

**Files:**
- Modify: `settings.gradle.kts` (añadir `include(":shared")`)
- Modify: `build.gradle.kts` (raíz: plugin multiplatform `apply false`)
- Modify: `gradle/libs.versions.toml` (kotlinx-datetime y plugin multiplatform)
- Create: `shared/build.gradle.kts`
- Modify: `app/build.gradle.kts` (dependencia `implementation(project(":shared"))`)
- Move: `app/src/main/java/com/faltasipasen/app/domain/{EducationLevel,RiskLevel,AlertFormatter}.kt` → `shared/src/commonMain/kotlin/com/faltasipasen/app/domain/`
- Create: `shared/src/commonMain/kotlin/com/faltasipasen/app/domain/ThresholdAlert.kt` (sale de `ThresholdEvaluator.kt`, porque `AlertFormatter` lo usa)
- Move: `app/src/test/java/com/faltasipasen/app/domain/{EducationLevelTest,RiskLevelTest,AlertFormatterTest}.kt` → `shared/src/commonTest/kotlin/com/faltasipasen/app/domain/`

**Interfaces:**
- Consumes: nada.
- Produces: módulo `:shared` con `EducationLevel`, `RiskLevel`, `ThresholdAlert(key: String, label: String, percent: Double)`, `AlertFormatter.percent(Double): String`, `AlertFormatter.thresholdTitle(Double): String`, `AlertFormatter.thresholdBody(List<ThresholdAlert>): String` — mismas firmas que hoy.

- [ ] **Step 1: Configurar Gradle**

`gradle/libs.versions.toml`: añadir en `[versions]`:

```toml
datetime = "0.7.1"
```

en `[libraries]`:

```toml
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "datetime" }
```

en `[plugins]`:

```toml
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
```

`build.gradle.kts` (raíz), dentro de `plugins { }`:

```kotlin
    alias(libs.plugins.kotlin.multiplatform) apply false
```

`settings.gradle.kts`: cambiar `include(":app")` por:

```kotlin
include(":app", ":shared")
```

Crear `shared/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    js(IR) {
        nodejs()
        binaries.library()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
```

`app/build.gradle.kts`, en `dependencies { }`, como primera línea:

```kotlin
    implementation(project(":shared"))
```

- [ ] **Step 2: Mover los archivos con git**

```bash
cd /c/Users/jesus/faltas-ipasen-app
mkdir -p shared/src/commonMain/kotlin/com/faltasipasen/app/domain shared/src/commonTest/kotlin/com/faltasipasen/app/domain
for f in EducationLevel RiskLevel AlertFormatter; do
  git mv app/src/main/java/com/faltasipasen/app/domain/$f.kt shared/src/commonMain/kotlin/com/faltasipasen/app/domain/$f.kt
  git mv app/src/test/java/com/faltasipasen/app/domain/${f}Test.kt shared/src/commonTest/kotlin/com/faltasipasen/app/domain/${f}Test.kt
done
```

Crear `shared/src/commonMain/kotlin/com/faltasipasen/app/domain/ThresholdAlert.kt`:

```kotlin
package com.faltasipasen.app.domain

data class ThresholdAlert(val key: String, val label: String, val percent: Double)
```

Y borrar la línea `data class ThresholdAlert(val key: String, val label: String, val percent: Double)` de `app/src/main/java/com/faltasipasen/app/domain/ThresholdEvaluator.kt`.

- [ ] **Step 3: Pasar los tests movidos a kotlin.test**

```bash
cd /c/Users/jesus/faltas-ipasen-app/shared/src/commonTest/kotlin/com/faltasipasen/app/domain
sed -i -e 's/^import org\.junit\.Assert\./import kotlin.test./' -e 's/^import org\.junit\.Test$/import kotlin.test.Test/' \
  EducationLevelTest.kt RiskLevelTest.kt AlertFormatterTest.kt
```

(`assertEquals(expected, actual)` tiene el mismo orden en kotlin.test.) Añadir a `AlertFormatterTest` un caso que fija el redondeo que hoy hace `String.format` (mitad hacia arriba):

```kotlin
    @Test
    fun `percent rounds half up to one decimal`() {
        assertEquals("16,3 %", AlertFormatter.percent(16.25))
        assertEquals("0,0 %", AlertFormatter.percent(0.0))
        assertEquals("100,0 %", AlertFormatter.percent(99.96))
    }
```

- [ ] **Step 4: Ejecutar los tests de shared y ver que fallan**

Run:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; cd /c/Users/jesus/faltas-ipasen-app && ./gradlew :shared:allTests
```

Expected: FAIL de compilación en `AlertFormatter.kt` (`java.util.Locale` / `String.format` no existen en código común).

- [ ] **Step 5: Reescribir `AlertFormatter` sin Locale**

Sustituir el contenido de `shared/src/commonMain/kotlin/com/faltasipasen/app/domain/AlertFormatter.kt` por:

```kotlin
package com.faltasipasen.app.domain

import kotlin.math.abs
import kotlin.math.floor

object AlertFormatter {
    /** "16,2 %": coma decimal española y un decimal, redondeando la mitad hacia arriba como String.format. */
    fun percent(value: Double): String {
        val tenths = floor(abs(value) * 10 + 0.5).toLong()
        val sign = if (value < 0 && tenths != 0L) "-" else ""
        return "$sign${tenths / 10},${tenths % 10} %"
    }

    fun thresholdTitle(thresholdPercent: Double): String = "Has superado el ${percent(thresholdPercent)} de faltas"

    fun thresholdBody(alerts: List<ThresholdAlert>): String =
        alerts.joinToString(" · ") { "${it.label}: ${percent(it.percent)}" }
}
```

- [ ] **Step 6: Ejecutar todos los tests**

Run:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; cd /c/Users/jesus/faltas-ipasen-app && ./gradlew :shared:allTests :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. `:shared:jvmTest` y `:shared:jsNodeTest` pasan los tres tests movidos; los tests del APK siguen pasando. Si el APK no resuelve `:shared` (error de variante `androidJvm` vs `jvm`), parar e informar: el plan B es añadir el target `androidTarget()` con `com.android.library`, que no se hace sin aprobación.

- [ ] **Step 7: Commit**

```bash
cd /c/Users/jesus/faltas-ipasen-app
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml app/build.gradle.kts shared app/src
git commit -m "refactor: start a multiplatform shared module with the date-free domain

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Mover el dominio con fechas y pasar el APK a kotlinx-datetime

Mueve `Models`, `CourseDates`, `AbsenceCalculator`, `History` y `ThresholdEvaluator` (y sus tests) a `shared/`, borra `LocalDateSerializer` y cambia todo el APK a `kotlinx.datetime.LocalDate`. Va en una sola tarea porque, en cuanto `Models` usa otra `LocalDate`, el APK no compila hasta convertirlo entero.

**Files:**
- Move: `app/src/main/java/com/faltasipasen/app/domain/{Models,CourseDates,AbsenceCalculator,History,ThresholdEvaluator}.kt` → `shared/src/commonMain/kotlin/com/faltasipasen/app/domain/`
- Delete: `app/src/main/java/com/faltasipasen/app/domain/LocalDateSerializer.kt`
- Move: `app/src/test/java/com/faltasipasen/app/domain/{AbsenceCalculatorTest,CourseDatesTest,HistoryBuilderTest,ThresholdEvaluatorTest}.kt` → `shared/src/commonTest/kotlin/com/faltasipasen/app/domain/`
- Modify: `app/src/main/java/com/faltasipasen/app/AppGraph.kt:18-20`
- Modify: `app/src/main/java/com/faltasipasen/app/data/SettingsStore.kt` (import)
- Modify: `app/src/main/java/com/faltasipasen/app/sync/SyncRunner.kt:17,71`
- Modify: `app/src/main/java/com/faltasipasen/app/ui/Formatters.kt:22,26`
- Modify: `app/src/main/java/com/faltasipasen/app/ui/Components.kt:119,126`
- Modify (solo import): `app/src/main/java/com/faltasipasen/app/ui/{InputValidation,MainViewModel,SettingsScreen,SummaryScreen,WelcomeScreen}.kt`
- Modify (import + `LocalDate.of` → `LocalDate(`): `app/src/test/java/com/faltasipasen/app/{data/FileSnapshotStoreTest,data/PrefsSettingsStoreTest,sync/SyncRunnerTest,sync/SyncRunnerEdgeCasesTest,ui/FormattersTest,ui/InputValidationTest}.kt`

**Interfaces:**
- Consumes: módulo `:shared` de Task 1.
- Produces (todas con `kotlinx.datetime.LocalDate`; el resto de firmas igual que hoy):
  - `ScheduleSlot(subject: String, dayOfWeek: Int, hours: Double, abbreviation: String = "", name: String = "")`
  - `CalendarDay(date: LocalDate, lectivo: Boolean)`
  - `Absence(date: LocalDate, subject: String, hours: Double, justified: Boolean, tramo: String = "", name: String = "")`
  - `Credentials`, `HoursStats`, `AbsenceStats`
  - `CourseDates.defaultCutoff(today: LocalDate, level: EducationLevel = FP_SUPERIOR): LocalDate`, `CourseDates.courseStartFor(cutoff: LocalDate): LocalDate`
  - `AbsenceCalculator.calculate(schedule, calendar, absences, cutoff: LocalDate, today: LocalDate): AbsenceStats`
  - `HistoryBuilder.build(...)`, `filter(...)`, `filterOptions(...)`; `HistoryEntry`, `HistoryDay`, `HistoryFilterOption`
  - `ThresholdEvaluator.evaluate(stats, thresholdPercent, previouslyActive): ThresholdEvaluation`

- [ ] **Step 1: Mover archivos y tests**

```bash
cd /c/Users/jesus/faltas-ipasen-app
D=app/src/main/java/com/faltasipasen/app/domain; T=app/src/test/java/com/faltasipasen/app/domain
S=shared/src/commonMain/kotlin/com/faltasipasen/app/domain; ST=shared/src/commonTest/kotlin/com/faltasipasen/app/domain
for f in Models CourseDates AbsenceCalculator History ThresholdEvaluator; do git mv $D/$f.kt $S/$f.kt; done
git rm -q $D/LocalDateSerializer.kt
for f in AbsenceCalculatorTest CourseDatesTest HistoryBuilderTest ThresholdEvaluatorTest; do git mv $T/$f.kt $ST/$f.kt; done
```

- [ ] **Step 2: Convertir los tests movidos (kotlin.test + kotlinx-datetime)**

```bash
cd /c/Users/jesus/faltas-ipasen-app/shared/src/commonTest/kotlin/com/faltasipasen/app/domain
sed -i -e 's/^import org\.junit\.Assert\./import kotlin.test./' -e 's/^import org\.junit\.Test$/import kotlin.test.Test/' \
  -e 's/^import java\.time\.LocalDate$/import kotlinx.datetime.LocalDate/' -e 's/LocalDate\.of(/LocalDate(/g' \
  AbsenceCalculatorTest.kt CourseDatesTest.kt HistoryBuilderTest.kt ThresholdEvaluatorTest.kt
```

En `HistoryBuilderTest.kt:72` cambiar `duplicateDay.dayOfWeek.value` por `duplicateDay.dayOfWeek.isoDayNumber` y añadir `import kotlinx.datetime.isoDayNumber`.

- [ ] **Step 3: Ejecutar los tests de shared y ver que fallan**

Run:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; cd /c/Users/jesus/faltas-ipasen-app && ./gradlew :shared:allTests
```

Expected: FAIL de compilación: `java.time` no existe en código común (`Models.kt`, `CourseDates.kt`, `AbsenceCalculator.kt`, `History.kt`).

- [ ] **Step 4: Convertir el dominio a kotlinx-datetime**

`Models.kt`: cambiar `import java.time.LocalDate` por `import kotlinx.datetime.LocalDate` y quitar `@Serializable(with = LocalDateSerializer::class) ` de `CalendarDay.date` y `Absence.date`, que quedan así:

```kotlin
@Serializable
data class CalendarDay(
    val date: LocalDate,
    val lectivo: Boolean,
)

@Serializable
data class Absence(
    val date: LocalDate,
    val subject: String,
    val hours: Double,
    val justified: Boolean,
    val tramo: String = "",
    val name: String = "",
)
```

`CourseDates.kt`: imports

```kotlin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
```

y en las dos funciones:

```kotlin
        val endYear = if (today.month.number >= COURSE_START_MONTH) today.year + 1 else today.year
        return LocalDate(endYear, level.defaultCourseEndMonth, level.defaultCourseEndDay)
```

```kotlin
    fun courseStartFor(cutoff: LocalDate): LocalDate =
        LocalDate(cutoff.year - 1, COURSE_START_MONTH, 1)
```

`AbsenceCalculator.kt`: imports

```kotlin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
```

y en el cuerpo:

```kotlin
        val courseDays = calendar
            .filter { it.lectivo && it.date >= courseStart && it.date <= cutoff }
            .map { it.date }
            .distinct()
        val counted = absences.filter { it.date >= courseStart && it.date <= countedUntil }
```

```kotlin
                slots.filter { it.dayOfWeek == day.dayOfWeek.isoDayNumber }.sumOf { it.hours }
```

```kotlin
            lectivoDaysElapsed = courseDays.count { it <= countedUntil },
```

`History.kt`: `import kotlinx.datetime.LocalDate` y en `build`:

```kotlin
            .filter { it.date >= courseStart && it.date <= until }
```

`ThresholdEvaluator.kt` no usa fechas; solo se mueve.

- [ ] **Step 5: Ejecutar los tests de shared**

Run:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; cd /c/Users/jesus/faltas-ipasen-app && ./gradlew :shared:allTests
```

Expected: BUILD SUCCESSFUL, todos los tests de dominio pasan en JVM y Node.

- [ ] **Step 6: Convertir el APK**

Cambiar `import java.time.LocalDate` por `import kotlinx.datetime.LocalDate` en: `data/SettingsStore.kt`, `sync/SyncRunner.kt`, `ui/InputValidation.kt`, `ui/MainViewModel.kt`, `ui/SettingsScreen.kt`, `ui/SummaryScreen.kt`, `ui/WelcomeScreen.kt`, `ui/Formatters.kt`, `ui/Components.kt`, `AppGraph.kt`. (`SettingsStore` usa `LocalDate.parse` y `toString()`, que se comportan igual con ISO.)

`AppGraph.kt` (líneas 18-20), para que `today` siga siendo la fecha de Madrid:

```kotlin
    private val zone: ZoneId = ZoneId.of("Europe/Madrid")

    val today: () -> LocalDate = { java.time.LocalDate.now(zone).toKotlinLocalDate() }
```

con `import kotlinx.datetime.toKotlinLocalDate`.

`SyncRunner.kt:71`:

```kotlin
                val message = "La fecha de fin de curso (${stats.cutoff.toJavaLocalDate().format(cutoffDateFormat)}) " +
```

con `import kotlinx.datetime.toJavaLocalDate`.

`Formatters.kt` (líneas 22 y 26):

```kotlin
fun formatLongDate(date: LocalDate): String = date.toJavaLocalDate().format(longDate)
```

```kotlin
fun formatDayHeader(date: LocalDate): String =
    date.toJavaLocalDate().format(dayHeader).replaceFirstChar { it.titlecase(spanish) }
```

con `import kotlinx.datetime.toJavaLocalDate`. `formatMillis` sigue con `java.time.Instant` y `ZoneId`, sin cambios.

`Components.kt` (selector de fecha, líneas 119 y 126):

```kotlin
            initialSelectedDateMillis = value.toJavaLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
```

```kotlin
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toKotlinLocalDate())
```

con `import kotlinx.datetime.toJavaLocalDate` e `import kotlinx.datetime.toKotlinLocalDate` (se mantienen `java.time.Instant` y `java.time.ZoneOffset`).

Tests del APK:

```bash
cd /c/Users/jesus/faltas-ipasen-app/app/src/test/java/com/faltasipasen/app
sed -i -e 's/^import java\.time\.LocalDate$/import kotlinx.datetime.LocalDate/' -e 's/LocalDate\.of(/LocalDate(/g' \
  data/FileSnapshotStoreTest.kt data/PrefsSettingsStoreTest.kt sync/SyncRunnerTest.kt sync/SyncRunnerEdgeCasesTest.kt \
  ui/FormattersTest.kt ui/InputValidationTest.kt
```

Comprobar que no queda `java.time.LocalDate` importado:

```bash
cd /c/Users/jesus/faltas-ipasen-app && grep -rn "import java.time.LocalDate" app/src shared/src
```

Expected: ninguna línea.

- [ ] **Step 7: Ejecutar todos los tests**

Run:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; cd /c/Users/jesus/faltas-ipasen-app && ./gradlew :shared:allTests :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. En particular `FileSnapshotStoreTest` confirma que el `Snapshot` guardado se sigue leyendo (fechas ISO) y `PrefsSettingsStoreTest` que el `cutoff` guardado se sigue leyendo.

- [ ] **Step 8: Commit**

```bash
cd /c/Users/jesus/faltas-ipasen-app
git add -A app/src shared/src
git commit -m "refactor: move the dated domain to shared and use kotlinx-datetime

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Mover `ScrapeParsers` y `ScrapeException` a `shared/`

**Files:**
- Move: `app/src/main/java/com/faltasipasen/app/scraper/ScrapeParsers.kt` → `shared/src/commonMain/kotlin/com/faltasipasen/app/scraper/ScrapeParsers.kt`
- Create: `shared/src/commonMain/kotlin/com/faltasipasen/app/scraper/ScrapeException.kt`
- Modify: `app/src/main/java/com/faltasipasen/app/scraper/ScraperContract.kt` (quitar `ScrapeException`)
- Move: `app/src/test/java/com/faltasipasen/app/scraper/ScrapeParsersTest.kt` → `shared/src/commonTest/kotlin/com/faltasipasen/app/scraper/ScrapeParsersTest.kt`

**Interfaces:**
- Consumes: modelos de Task 2.
- Produces:
  - `class ScrapeException(val reason: Reason, message: String) : Exception(message)` con `enum class Reason { LOGIN_FAILED, NETWORK, TIMEOUT, PARSE_ERROR, INTERNAL }` (sin cambios)
  - `ScrapeParsers.checkOk(raw: String, step: String)`, `parseSchedule(raw: String): List<ScheduleSlot>`, `parseCalendar(raw: String): List<CalendarDay>`, `parseAbsences(raw: String, schedule: List<ScheduleSlot> = emptyList()): AbsenceRead`, `displayName(String)`, `normalizeSubject(String)`
  - `data class AbsenceRead(val absences: List<Absence>, val skipped: List<String>)`
  - El JSON de entrada (`{"ok":true,"data":…}`) es el contrato que reutilizarán la fase 2 (readers) y la web.

- [ ] **Step 1: Mover y separar `ScrapeException`**

```bash
cd /c/Users/jesus/faltas-ipasen-app
mkdir -p shared/src/commonMain/kotlin/com/faltasipasen/app/scraper shared/src/commonTest/kotlin/com/faltasipasen/app/scraper
git mv app/src/main/java/com/faltasipasen/app/scraper/ScrapeParsers.kt shared/src/commonMain/kotlin/com/faltasipasen/app/scraper/ScrapeParsers.kt
git mv app/src/test/java/com/faltasipasen/app/scraper/ScrapeParsersTest.kt shared/src/commonTest/kotlin/com/faltasipasen/app/scraper/ScrapeParsersTest.kt
```

Crear `shared/src/commonMain/kotlin/com/faltasipasen/app/scraper/ScrapeException.kt`:

```kotlin
package com.faltasipasen.app.scraper

class ScrapeException(val reason: Reason, message: String) : Exception(message) {
    /** INTERNAL: fallo de la propia app o del móvil (WebView, disco…), no de iPasen. */
    enum class Reason { LOGIN_FAILED, NETWORK, TIMEOUT, PARSE_ERROR, INTERNAL }
}
```

Borrar ese mismo bloque `class ScrapeException … }` de `app/src/main/java/com/faltasipasen/app/scraper/ScraperContract.kt` (se quedan `ScrapeResult` e `IpasenScraper`).

- [ ] **Step 2: Convertir el test**

```bash
cd /c/Users/jesus/faltas-ipasen-app/shared/src/commonTest/kotlin/com/faltasipasen/app/scraper
sed -i -e 's/^import org\.junit\.Assert\.assertEquals$/import kotlin.test.assertEquals/' \
  -e 's/^import org\.junit\.Assert\.assertTrue$/import kotlin.test.assertTrue/' \
  -e 's/^import org\.junit\.Assert\.assertThrows$/import kotlin.test.assertFailsWith/' \
  -e 's/^import org\.junit\.Test$/import kotlin.test.Test/' \
  -e 's/^import java\.time\.LocalDate$/import kotlinx.datetime.LocalDate/' -e 's/LocalDate\.of(/LocalDate(/g' \
  -e 's/assertThrows(ScrapeException::class\.java)/assertFailsWith<ScrapeException>/g' ScrapeParsersTest.kt
grep -n "assertFailsWith\|assertThrows" ScrapeParsersTest.kt
```

Expected: líneas 14 y 128 con `assertFailsWith<ScrapeException> {`; ningún `assertThrows`.

- [ ] **Step 3: Ejecutar y ver que falla**

Run:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; cd /c/Users/jesus/faltas-ipasen-app && ./gradlew :shared:allTests
```

Expected: FAIL de compilación en `ScrapeParsers.kt` (`java.time`, `DateTimeParseException`, `java.util.Locale`).

- [ ] **Step 4: Convertir `ScrapeParsers`**

Sustituir los imports

```kotlin
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale
```

por

```kotlin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
```

y cambiar:

```kotlin
                return@flatMap schedule.filter { it.dayOfWeek == date.dayOfWeek.isoDayNumber }.map { slot ->
```

```kotlin
    fun normalizeSubject(name: String): String = displayName(name).uppercase()
```

(`uppercase()` en Kotlin común no depende del idioma del sistema, igual que `Locale.ROOT`.)

```kotlin
    private fun date(value: String, step: String): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (e: IllegalArgumentException) {
            invalid(step, "fecha no válida: $value")
        }
```

- [ ] **Step 5: Ejecutar todos los tests**

Run:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; cd /c/Users/jesus/faltas-ipasen-app && ./gradlew :shared:allTests :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL; `ScrapeParsersTest` pasa en JVM y Node, `SyncRunnerTest` y `SyncRunnerEdgeCasesTest` siguen en verde.

- [ ] **Step 6: Commit**

```bash
cd /c/Users/jesus/faltas-ipasen-app
git add -A app/src shared/src
git commit -m "refactor: share the scrape parsers with the web build

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Comprobar el APK en el móvil

**Files:**
- Modify: `.superpowers/sdd/2026-09-28-faltas-ipasen-app/progress.md` (anotar la fase 1)

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: APK verificado; punto de partida de la fase 2.

- [ ] **Step 1: Compilar e instalar**

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"; cd /c/Users/jesus/faltas-ipasen-app && ./gradlew :app:assembleDebug && adb devices && adb -s 4f48fb70 install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`. Si `adb devices` no muestra `4f48fb70`, pedir al usuario que conecte el móvil con depuración USB.

- [ ] **Step 2: Verificación manual con el usuario**

Pedir al usuario que abra la app **sin cerrar sesión** y compruebe:
1. Arranca y muestra el Resumen guardado (el `Snapshot` antiguo se lee).
2. Ajustes muestra la misma fecha de fin y el mismo umbral.
3. "Sincronizar" termina bien y los % coinciden con los de antes.
4. El Historial abre y filtra por módulo.

Expected: las cuatro cosas como antes. Si algo falla, no seguir: depurar con superpowers:systematic-debugging.

- [ ] **Step 3: Anotar y commitear**

Añadir al final de `.superpowers/sdd/2026-09-28-faltas-ipasen-app/progress.md`:

```markdown
## 2026-10-28 — Versión web, fase 1
- `shared/` (Kotlin Multiplatform jvm + js) con dominio y ScrapeParsers; APK en kotlinx-datetime.
- Tests de shared en JVM y Node; APK verificado en el móvil.
- Siguiente: fase 2 (readers compartidos), spec `docs/superpowers/specs/2026-10-28-version-web-design.md`.
```

```bash
cd /c/Users/jesus/faltas-ipasen-app
git add .superpowers/sdd/2026-09-28-faltas-ipasen-app/progress.md
git commit -m "docs: record web phase 1 in the progress ledger

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Siguientes planes (se escriben al terminar esta fase)

- Fase 2: `scraper/readers` + `readCalendar` con `datosCalendario`; APK verificado.
- Fase 3: web + marcador (incluye la fachada `@JsExport` de `shared`).
- Fase 4: repo público, GitHub Actions y Pages.
