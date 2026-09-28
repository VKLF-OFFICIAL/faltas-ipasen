# Rediseño visual y temas — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Faltas iPasen app a polished minimalist look with a fixed indigo identity, Inter typography, traffic-light risk colours, a bottom-navigation shell (Resumen · Historial · Ajustes), an absence history, course-progress info, and a theme selector (Sistema · Claro · Oscuro · AMOLED).

**Architecture:** Data additions first (abbreviation/name/tramo from the scraper, lectivo-day counts, risk level, history builder, theme setting) — all pure and unit-tested. Then a `ui.theme` package (colours, type, shapes, animated theme), shared components and pure formatters, and finally the screens, one per task, each task leaving the app compiling and runnable.

**Tech Stack:** unchanged — Kotlin 2.2.20, AGP 8.13.0, Compose BOM 2025.09.00 (Material 3), WorkManager, kotlinx.serialization, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-28-rediseno-visual-design.md` (base app spec: `docs/superpowers/specs/2026-09-27-faltas-ipasen-app-design.md`)

## Global Constraints

- Repo `C:\Users\jesus\faltas-ipasen-app` (Bash `/c/Users/jesus/faltas-ipasen-app`), branch `feat/app-faltas`, package `com.faltasipasen.app`.
- Gradle from Bash: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew <task>` (written `./gradlew` below).
- `adb` = `/c/Users/jesus/AppData/Local/Android/Sdk/platform-tools/adb`. The user's phone is connected; `adb install -r` works; input injection (`adb shell input`) is NOT allowed on this phone. Use `MSYS_NO_PATHCONV=1` for adb commands with device paths.
- User-facing text in Spanish (Spain); identifiers/commits in English. Commit trailer (second `-m`): `TRAILER=$'Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01Nni74mP4bcfJHgPoZaeheQ'`.
- Colours, typography, shapes and texts are exactly those in the spec's "Identidad visual" and "Pantallas" sections (values repeated in the tasks below).
- Snapshots already stored on the phone must keep loading: every new serialized field has a default.
- Risk level: LOW if % < umbral/2, MEDIUM if umbral/2 ≤ % < umbral, HIGH if % ≥ umbral.
- Never commit personal data (names, school, real absence records).
- Unit tests must not touch Android framework statics.

## File Structure

```
app/src/main/
├── assets/scraper/schedule.js, absences.js          (R1: emit abbr / tramo; pagination guard)
├── assets/licenses/Inter-OFL.txt                   (R3)
├── res/font/inter_regular.ttf, inter_medium.ttf, inter_semibold.ttf, inter_bold.ttf  (R3)
└── java/com/faltasipasen/app/
    ├── domain/Models.kt                             (R1, R2: new fields)
    ├── domain/RiskLevel.kt                          (R2)
    ├── domain/History.kt                            (R2: HistoryEntry, HistoryDay, HistoryBuilder)
    ├── domain/AbsenceCalculator.kt                  (R2: names + lectivo days)
    ├── domain/ThresholdEvaluator.kt                 (R2: short label)
    ├── scraper/ScrapeParsers.kt                     (R1)
    ├── data/ThemeMode.kt, data/SettingsStore.kt     (R3)
    ├── ui/theme/Color.kt, Type.kt, Theme.kt         (R3)
    ├── ui/Formatters.kt                             (R4: pure formatters, moved out of Components.kt)
    ├── ui/Components.kt                             (R4: FlatCard, ThinProgress, StatusChip, SectionTitle, ScreenHeader, CutoffDateField)
    ├── ui/MainViewModel.kt                          (R3 themeMode, R4 history + setThemeMode)
    ├── ui/MainActivity.kt                           (R3 theme, R4 shell, R5, R6, R7)
    ├── ui/SummaryScreen.kt                          (R4, replaces HomeScreen.kt)
    ├── ui/HistoryScreen.kt                          (R5)
    ├── ui/SettingsScreen.kt                         (R6 rewrite)
    └── ui/WelcomeScreen.kt                          (R7, replaces SetupScreen.kt)
app/src/test/java/com/faltasipasen/app/
    ├── scraper/ScrapeParsersTest.kt, data/FileSnapshotStoreTest.kt   (R1)
    ├── domain/RiskLevelTest.kt, HistoryBuilderTest.kt, AbsenceCalculatorTest.kt, ThresholdEvaluatorTest.kt (R2)
    ├── data/PrefsSettingsStoreTest.kt               (R3)
    └── ui/FormattersTest.kt                         (R4)
```

---

### Task R1: Abbreviation, readable name and tramo from the scraper

**Files:**
- Modify: `app/src/main/java/com/faltasipasen/app/domain/Models.kt` (ScheduleSlot, Absence)
- Modify: `app/src/main/java/com/faltasipasen/app/scraper/ScrapeParsers.kt`
- Modify: `app/src/main/assets/scraper/schedule.js`, `app/src/main/assets/scraper/absences.js`
- Test: `app/src/test/java/com/faltasipasen/app/scraper/ScrapeParsersTest.kt`, `app/src/test/java/com/faltasipasen/app/data/FileSnapshotStoreTest.kt`

**Interfaces:**
- Produces:
  - `ScheduleSlot(subject: String, dayOfWeek: Int, hours: Double, abbreviation: String = "", name: String = "")`
  - `Absence(date: LocalDate, subject: String, hours: Double, justified: Boolean, tramo: String = "", name: String = "")`
  - `subject` stays the normalized key (`ScrapeParsers.normalizeSubject`, upper case). `name` = trimmed, whitespace-collapsed original text (case preserved). `abbreviation`/`tramo` = trimmed script value or "".
  - `ScrapeParsers.displayName(name: String): String` (trim + collapse whitespace, no case change).
  - JS contract additions: schedule entries `{subject, abbr, day, hours}`; absence entries `{date, subject, hours, justified, tramo}`. Both new fields optional for the parser.
  - `absences.js` fails with a clear error when the table shows fewer rows than the page's "Número total de registros: N".

- [ ] **Step 1: Update the tests first**

In `ScrapeParsersTest.kt`, replace the two tests `parses schedule and normalizes subject names` and `parses absences with defaults` with:
```kotlin
    @Test
    fun `parses schedule and normalizes subject names`() {
        val raw = """{"ok":true,"data":[{"subject":"  Redes   locales ","day":1,"hours":1.5},{"subject":"BD","day":3}]}"""
        assertEquals(
            listOf(
                ScheduleSlot("REDES LOCALES", 1, 1.5, name = "Redes locales"),
                ScheduleSlot("BD", 3, 1.0, name = "BD"),
            ),
            ScrapeParsers.parseSchedule(raw),
        )
    }

    @Test
    fun `parses schedule abbreviations`() {
        val raw = """{"ok":true,"data":[{"subject":"Administración de sistemas operativos","abbr":" ASO ","day":2,"hours":1}]}"""
        assertEquals(
            listOf(ScheduleSlot("ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", 2, 1.0, "ASO", "Administración de sistemas operativos")),
            ScrapeParsers.parseSchedule(raw),
        )
    }

    @Test
    fun `parses absences with defaults`() {
        val raw = """{"ok":true,"data":[{"date":"2026-10-02","subject":"bd"},{"date":"2026-10-03","subject":"Redes","hours":2,"justified":true,"tramo":" 8:15 - 9:15 "}]}"""
        assertEquals(
            listOf(
                Absence(LocalDate.of(2026, 10, 2), "BD", 1.0, false, name = "bd"),
                Absence(LocalDate.of(2026, 10, 3), "REDES", 2.0, true, tramo = "8:15 - 9:15", name = "Redes"),
            ),
            ScrapeParsers.parseAbsences(raw),
        )
    }
```

In `FileSnapshotStoreTest.kt` add (imports already cover `ScheduleSlot`, `Absence`, `LocalDate`, `File`):
```kotlin
    @Test
    fun `snapshot saved before the new fields existed still loads`() {
        file().writeText(
            """{"schedule":[{"subject":"REDES","dayOfWeek":1,"hours":1.0}],""" +
                """"calendar":[{"date":"2026-09-14","lectivo":true}],""" +
                """"absences":[{"date":"2026-09-14","subject":"REDES","hours":1.0,"justified":false}],""" +
                """"syncedAtMillis":1}""",
        )
        val loaded = FileSnapshotStore(file()).current.value!!
        assertEquals(ScheduleSlot("REDES", 1, 1.0), loaded.schedule.single())
        assertEquals(Absence(LocalDate.of(2026, 9, 14), "REDES", 1.0, false), loaded.absences.single())
    }
```

- [ ] **Step 2: Run to see the failures**

Run: `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.scraper.*" --tests "com.faltasipasen.app.data.*"`
Expected: compilation FAILS (unknown parameters `name`, `tramo`, and 5-arg `ScheduleSlot`).

- [ ] **Step 3: Implement**

In `Models.kt` replace `ScheduleSlot` and `Absence` with:
```kotlin
/**
 * Una sesión semanal del horario. dayOfWeek: 1 = lunes … 7 = domingo.
 * [subject] es la clave normalizada para cruzar horario y faltas; [name] y [abbreviation] son para mostrar.
 */
@Serializable
data class ScheduleSlot(
    val subject: String,
    val dayOfWeek: Int,
    val hours: Double,
    val abbreviation: String = "",
    val name: String = "",
)

@Serializable
data class Absence(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val subject: String,
    val hours: Double,
    val justified: Boolean,
    val tramo: String = "",
    val name: String = "",
)
```

In `ScrapeParsers.kt`:
- `SlotDto` becomes `private data class SlotDto(val subject: String, val day: Int, val hours: Double = 1.0, val abbr: String = "")`.
- `AbsenceDto` gains `val tramo: String = ""` (last parameter).
- `parseSchedule` builds `ScheduleSlot(subject(dto.subject, STEP_SCHEDULE), dto.day, hours(dto.hours, STEP_SCHEDULE), dto.abbr.trim(), displayName(dto.subject))`.
- `parseAbsences` builds `Absence(date = …, subject = …, hours = …, justified = it.justified, tramo = it.tramo.trim(), name = displayName(it.subject))`.
- Add next to `normalizeSubject`:
```kotlin
    fun displayName(name: String): String = name.trim().replace(whitespace, " ")
```
  and change `normalizeSubject` to reuse it: `fun normalizeSubject(name: String): String = displayName(name).uppercase(Locale.ROOT)`.

In `schedule.js` change the push to:
```js
      if (day > 0 && names[abbr]) slots.push({ subject: names[abbr], abbr: abbr, day: day, hours: hours });
```

In `absences.js`:
- add `tramo: row.cells[col.tramo].innerText.trim(),` to the pushed object (after `hours`);
- count every data row read (including retrasos) and compare with the page total, so a paginated table can never undercount silently. Replace the rows loop and the `return` with:
```js
  var rowsRead = 0;
  var absences = [];
  Array.prototype.slice.call(table.rows, 1).forEach(function (row) {
    if (row.cells.length < header.length) return;
    rowsRead++;
    var state = row.cells[col.state].innerText.trim().toLowerCase();
    if (state.indexOf('retraso') >= 0) return;
    absences.push({
      date: toIsoDate(row.cells[col.date].innerText),
      subject: row.cells[col.subject].innerText.trim(),
      hours: tramoHours(row.cells[col.tramo].innerText),
      justified: state.indexOf('justificada') === 0,
      tramo: row.cells[col.tramo].innerText.trim(),
    });
  });
  var total = /registros:\s*(\d+)/i.exec(document.body.innerText);
  if (total && Number(total[1]) !== rowsRead) {
    throw new Error('la tabla de faltas muestra ' + rowsRead + ' de ' + total[1] + ' registros (¿está paginada?)');
  }
  return absences;
```

- [ ] **Step 4: Run all tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (all previous tests + 2 new ones: `parses schedule abbreviations`, `snapshot saved before the new fields existed still loads`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: keep module abbreviation, readable name and tramo from iPasen" -m "$TRAILER"
```

---

### Task R2: Risk level, display names, course progress and history builder

**Files:**
- Modify: `app/src/main/java/com/faltasipasen/app/domain/Models.kt` (HoursStats, AbsenceStats)
- Modify: `app/src/main/java/com/faltasipasen/app/domain/AbsenceCalculator.kt`
- Modify: `app/src/main/java/com/faltasipasen/app/domain/ThresholdEvaluator.kt`
- Create: `app/src/main/java/com/faltasipasen/app/domain/RiskLevel.kt`, `domain/History.kt`
- Test: create `app/src/test/java/com/faltasipasen/app/domain/RiskLevelTest.kt`, `domain/HistoryBuilderTest.kt`; extend `domain/AbsenceCalculatorTest.kt`, `domain/ThresholdEvaluatorTest.kt`

**Interfaces:**
- Consumes: R1 fields.
- Produces:
  - `HoursStats(label, courseHours, absentHours, unjustifiedHours, abbreviation: String = "", name: String = "")` + `val justifiedHours: Double` + `val shortLabel: String` (= abbreviation, else name, else label).
  - `AbsenceStats(global, subjects, courseStart, cutoff, countedUntil, lectivoDaysElapsed: Int = 0, lectivoDaysTotal: Int = 0)`.
  - `enum class RiskLevel { LOW, MEDIUM, HIGH; companion object { fun of(percent: Double, thresholdPercent: Double): RiskLevel } }`.
  - `data class HistoryEntry(date: LocalDate, tramo: String, subjectKey: String, abbreviation: String, name: String, hours: Double, justified: Boolean)` with `val label: String`; `data class HistoryDay(date: LocalDate, entries: List<HistoryEntry>)`; `data class HistoryFilterOption(key: String, label: String)`.
  - `object HistoryBuilder { fun build(schedule: List<ScheduleSlot>, absences: List<Absence>, cutoff: LocalDate, today: LocalDate): List<HistoryDay>; fun filter(days: List<HistoryDay>, subjectKey: String?): List<HistoryDay>; fun filterOptions(days: List<HistoryDay>): List<HistoryFilterOption> }`.
  - ThresholdEvaluator: subject alerts keep `key = label` but use `label = shortLabel`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/faltasipasen/app/domain/RiskLevelTest.kt`:
```kotlin
package com.faltasipasen.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RiskLevelTest {
    @Test
    fun `below half of the threshold is low`() {
        assertEquals(RiskLevel.LOW, RiskLevel.of(0.4, 20.0))
        assertEquals(RiskLevel.LOW, RiskLevel.of(9.99, 20.0))
    }

    @Test
    fun `from half of the threshold up to it is medium`() {
        assertEquals(RiskLevel.MEDIUM, RiskLevel.of(10.0, 20.0))
        assertEquals(RiskLevel.MEDIUM, RiskLevel.of(19.9, 20.0))
    }

    @Test
    fun `reaching the threshold is high`() {
        assertEquals(RiskLevel.HIGH, RiskLevel.of(20.0, 20.0))
        assertEquals(RiskLevel.HIGH, RiskLevel.of(35.0, 20.0))
    }
}
```

`app/src/test/java/com/faltasipasen/app/domain/HistoryBuilderTest.kt`:
```kotlin
package com.faltasipasen.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HistoryBuilderTest {
    private val cutoff = LocalDate.of(2027, 2, 15)
    private val today = LocalDate.of(2026, 10, 1)
    private val schedule = listOf(
        ScheduleSlot("ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", 1, 1.0, "ASO", "Administración de sistemas operativos"),
        ScheduleSlot("SERVICIOS DE RED E INTERNET", 2, 1.0, "SRI", "Servicios de red e Internet"),
    )
    private val absences = listOf(
        Absence(LocalDate.of(2026, 9, 24), "SERVICIOS DE RED E INTERNET", 1.0, true, "9:15 - 10:15", "Servicios de red e Internet"),
        Absence(LocalDate.of(2026, 9, 24), "ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", 1.0, false, "8:15 - 9:15", "Administración de sistemas operativos"),
        Absence(LocalDate.of(2026, 9, 29), "ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", 1.0, false, "11:45 - 12:45", ""),
        Absence(LocalDate.of(2026, 10, 5), "ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", 1.0, false, "8:15 - 9:15", ""), // después de hoy
        Absence(LocalDate.of(2026, 8, 31), "ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", 1.0, false, "8:15 - 9:15", ""), // antes del curso
        Absence(LocalDate.of(2026, 9, 25), "TUTORÍA", 1.0, false, "", "Tutoría"),
    )

    private val days = HistoryBuilder.build(schedule, absences, cutoff, today)

    @Test
    fun `days are newest first and only inside the counted period`() {
        assertEquals(
            listOf(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 24)),
            days.map { it.date },
        )
    }

    @Test
    fun `entries of a day are ordered by tramo start time`() {
        assertEquals(listOf("8:15 - 9:15", "9:15 - 10:15"), days.last().entries.map { it.tramo })
    }

    @Test
    fun `abbreviation comes from the timetable and the name falls back to it`() {
        val late = days.first().entries.single()
        assertEquals("ASO", late.abbreviation)
        assertEquals("Administración de sistemas operativos", late.name)
        val tutoria = days[1].entries.single()
        assertEquals("", tutoria.abbreviation)
        assertEquals("Tutoría", tutoria.name)
    }

    @Test
    fun `filter keeps only one subject and drops empty days`() {
        val filtered = HistoryBuilder.filter(days, "SERVICIOS DE RED E INTERNET")
        assertEquals(listOf(LocalDate.of(2026, 9, 24)), filtered.map { it.date })
        assertEquals(1, filtered.single().entries.size)
        assertEquals(days, HistoryBuilder.filter(days, null))
    }

    @Test
    fun `filter options list each subject once, sorted by label`() {
        assertEquals(
            listOf(
                HistoryFilterOption("ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", "ASO"),
                HistoryFilterOption("SERVICIOS DE RED E INTERNET", "SRI"),
                HistoryFilterOption("TUTORÍA", "Tutoría"),
            ),
            HistoryBuilder.filterOptions(days),
        )
    }
}
```

Append to `AbsenceCalculatorTest.kt` (inside the class):
```kotlin
    @Test
    fun `lectivo days are counted until today and until the cutoff`() {
        val stats = calc()
        assertEquals(3, stats.lectivoDaysElapsed) // 14/09, 15/09, 21/09
        assertEquals(4, stats.lectivoDaysTotal)   // + 05/10
    }

    @Test
    fun `subject stats carry abbreviation and readable name`() {
        val named = listOf(ScheduleSlot("REDES", 1, 2.0, "RED", "Redes"))
        val stats = AbsenceCalculator.calculate(named, calendar, absences, cutoff, today)
        val redes = stats.subjects.single { it.label == "REDES" }
        assertEquals("RED", redes.abbreviation)
        assertEquals("Redes", redes.name)
        assertEquals("RED", redes.shortLabel)
        assertEquals("BD", stats.subjects.single { it.label == "BD" }.shortLabel)
    }

    @Test
    fun `justified hours are absent minus unjustified`() {
        assertEquals(1.0, calc().global.justifiedHours, 1e-9)
    }
```

Append to `ThresholdEvaluatorTest.kt` (inside the class):
```kotlin
    @Test
    fun `subject alerts show the abbreviation but keep the full key`() {
        val aso = HoursStats("ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", 50.0, 10.0, 0.0, "ASO", "Administración de sistemas operativos")
        val result = ThresholdEvaluator.evaluate(stats(0.0, aso), 15.0, emptySet())
        assertEquals(listOf(ThresholdAlert("ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", "ASO", 20.0)), result.newAlerts)
    }
```

- [ ] **Step 2: Run to see the failures**

Run: `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.domain.*"`
Expected: compilation FAILS (unresolved `RiskLevel`, `HistoryBuilder`, `lectivoDaysElapsed`, …).

- [ ] **Step 3: Implement**

In `Models.kt` replace `HoursStats` and `AbsenceStats` with:
```kotlin
data class HoursStats(
    val label: String,
    val courseHours: Double,
    val absentHours: Double,
    val unjustifiedHours: Double,
    val abbreviation: String = "",
    val name: String = "",
) {
    val percent: Double get() = percentOf(absentHours)
    val unjustifiedPercent: Double get() = percentOf(unjustifiedHours)
    val justifiedHours: Double get() = absentHours - unjustifiedHours
    val shortLabel: String get() = abbreviation.ifEmpty { name.ifEmpty { label } }

    fun marginHours(thresholdPercent: Double): Double =
        (thresholdPercent / 100.0 * courseHours - absentHours).coerceAtLeast(0.0)

    private fun percentOf(hours: Double) = if (courseHours <= 0.0) 0.0 else hours / courseHours * 100.0
}

data class AbsenceStats(
    val global: HoursStats,
    val subjects: List<HoursStats>,
    val courseStart: LocalDate,
    val cutoff: LocalDate,
    val countedUntil: LocalDate,
    val lectivoDaysElapsed: Int = 0,
    val lectivoDaysTotal: Int = 0,
)
```

`app/src/main/java/com/faltasipasen/app/domain/RiskLevel.kt`:
```kotlin
package com.faltasipasen.app.domain

enum class RiskLevel {
    LOW, MEDIUM, HIGH;

    companion object {
        fun of(percent: Double, thresholdPercent: Double): RiskLevel = when {
            percent >= thresholdPercent -> HIGH
            percent >= thresholdPercent / 2 -> MEDIUM
            else -> LOW
        }
    }
}
```

In `AbsenceCalculator.calculate`, inside `labels.map { subject -> … }` build the `HoursStats` with two extra arguments:
```kotlin
                abbreviation = slots.firstOrNull { it.abbreviation.isNotEmpty() }?.abbreviation.orEmpty(),
                name = slots.firstOrNull { it.name.isNotEmpty() }?.name
                    ?: own.firstOrNull { it.name.isNotEmpty() }?.name.orEmpty(),
```
and replace the `return` with:
```kotlin
        return AbsenceStats(
            global = global,
            subjects = subjects,
            courseStart = courseStart,
            cutoff = cutoff,
            countedUntil = countedUntil,
            lectivoDaysElapsed = courseDays.count { !it.isAfter(countedUntil) },
            lectivoDaysTotal = courseDays.size,
        )
```

In `ThresholdEvaluator.evaluate`, change the subject line to:
```kotlin
                .forEach { add(ThresholdAlert(it.label, it.shortLabel, it.percent)) }
```

`app/src/main/java/com/faltasipasen/app/domain/History.kt`:
```kotlin
package com.faltasipasen.app.domain

import java.time.LocalDate

data class HistoryEntry(
    val date: LocalDate,
    val tramo: String,
    val subjectKey: String,
    val abbreviation: String,
    val name: String,
    val hours: Double,
    val justified: Boolean,
) {
    val label: String get() = abbreviation.ifEmpty { name.ifEmpty { subjectKey } }
}

data class HistoryDay(val date: LocalDate, val entries: List<HistoryEntry>)

data class HistoryFilterOption(val key: String, val label: String)

object HistoryBuilder {
    private val tramoStart = Regex("(\\d{1,2}):(\\d{2})")

    fun build(schedule: List<ScheduleSlot>, absences: List<Absence>, cutoff: LocalDate, today: LocalDate): List<HistoryDay> {
        val courseStart = CourseDates.courseStartFor(cutoff)
        val until = minOf(today, cutoff)
        val bySubject = schedule.groupBy { it.subject }
        return absences
            .filter { !it.date.isBefore(courseStart) && !it.date.isAfter(until) }
            .map { absence ->
                val slots = bySubject[absence.subject].orEmpty()
                HistoryEntry(
                    date = absence.date,
                    tramo = absence.tramo,
                    subjectKey = absence.subject,
                    abbreviation = slots.firstOrNull { it.abbreviation.isNotEmpty() }?.abbreviation.orEmpty(),
                    name = absence.name.ifEmpty { slots.firstOrNull { it.name.isNotEmpty() }?.name.orEmpty() },
                    hours = absence.hours,
                    justified = absence.justified,
                )
            }
            .groupBy { it.date }
            .map { (date, entries) -> HistoryDay(date, entries.sortedBy { startMinutes(it.tramo) }) }
            .sortedByDescending { it.date }
    }

    fun filter(days: List<HistoryDay>, subjectKey: String?): List<HistoryDay> {
        if (subjectKey == null) return days
        return days
            .map { day -> day.copy(entries = day.entries.filter { it.subjectKey == subjectKey }) }
            .filter { it.entries.isNotEmpty() }
    }

    fun filterOptions(days: List<HistoryDay>): List<HistoryFilterOption> =
        days.flatMap { it.entries }
            .distinctBy { it.subjectKey }
            .map { HistoryFilterOption(it.subjectKey, it.label) }
            .sortedBy { it.label }

    private fun startMinutes(tramo: String): Int {
        val match = tramoStart.find(tramo) ?: return Int.MAX_VALUE
        return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (everything; 12 new tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/faltasipasen/app/domain app/src/test/java/com/faltasipasen/app/domain
git commit -m "feat: add risk level, display names, course progress and absence history" -m "$TRAILER"
```

---

### Task R3: Theme setting and the indigo design system

**Files:**
- Create: `app/src/main/java/com/faltasipasen/app/data/ThemeMode.kt`
- Modify: `app/src/main/java/com/faltasipasen/app/data/SettingsStore.kt`
- Create: `app/src/main/res/font/inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`, `inter_bold.ttf`, `app/src/main/assets/licenses/Inter-OFL.txt`
- Create: `app/src/main/java/com/faltasipasen/app/ui/theme/Color.kt`, `Type.kt`, `Theme.kt`
- Modify: `app/src/main/java/com/faltasipasen/app/ui/MainViewModel.kt` (UiState.themeMode), `ui/MainActivity.kt` (use FaltasTheme + system bars)
- Test: `app/src/test/java/com/faltasipasen/app/data/PrefsSettingsStoreTest.kt`

**Interfaces:**
- Produces:
  - `enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }` in `com.faltasipasen.app.data`.
  - `SettingsStore.themeMode(): ThemeMode`, `SettingsStore.setThemeMode(value: ThemeMode)` (prefs key `"tema"`, default SYSTEM, unknown value → SYSTEM).
  - `ui.theme`: `FaltasTheme(mode: ThemeMode, content)`, `fun ThemeMode.isDark(systemDark: Boolean): Boolean`, `RiskColors(low, medium, high)` with `fun of(level: RiskLevel): Color`, `val LocalRiskColors`, `val Tabular: TextStyle`, `val Inter: FontFamily`, `FaltasTypography`, `FaltasShapes`.
  - `UiState.themeMode: ThemeMode`.

- [ ] **Step 1: Failing settings tests**

Append to `PrefsSettingsStoreTest.kt`:
```kotlin
    @Test
    fun `theme defaults to system and is persisted`() {
        assertEquals(ThemeMode.SYSTEM, store.themeMode())
        store.setThemeMode(ThemeMode.AMOLED)
        assertEquals(ThemeMode.AMOLED, PrefsSettingsStore(prefs, ReversingCipher()) { LocalDate.of(2026, 9, 28) }.themeMode())
    }

    @Test
    fun `unknown stored theme falls back to system`() {
        prefs.values["tema"] = "SEPIA"
        assertEquals(ThemeMode.SYSTEM, store.themeMode())
    }
```
Run `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.data.*"` → compilation FAILS.

- [ ] **Step 2: Implement the setting**

`app/src/main/java/com/faltasipasen/app/data/ThemeMode.kt`:
```kotlin
package com.faltasipasen.app.data

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
```
In the `SettingsStore` interface add `fun themeMode(): ThemeMode` and `fun setThemeMode(value: ThemeMode)`. In `PrefsSettingsStore` add:
```kotlin
    override fun themeMode(): ThemeMode =
        prefs.getString(KEY_THEME, null)?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.SYSTEM

    override fun setThemeMode(value: ThemeMode) = write { putString(KEY_THEME, value.name) }
```
and `private const val KEY_THEME = "tema"` in the companion. Run the data tests → PASS.

- [ ] **Step 3: Add the Inter font files**

```bash
cd /c/Users/jesus/faltas-ipasen-app
SCRATCH="$(mktemp -d)"
curl -sSL -o "$SCRATCH/inter.zip" https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip
unzip -q "$SCRATCH/inter.zip" -d "$SCRATCH/inter"
find "$SCRATCH/inter" \( -iname "Inter-Regular.ttf" -o -iname "Inter-Medium.ttf" -o -iname "Inter-SemiBold.ttf" -o -iname "Inter-Bold.ttf" -o -iname "LICENSE.txt" \)
```
Copy the four **static** TTFs (not the variable `InterVariable*.ttf`, not the `InterDisplay` family) to `app/src/main/res/font/` as `inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`, `inter_bold.ttf` (Android resource names: lower case + underscores), and `LICENSE.txt` to `app/src/main/assets/licenses/Inter-OFL.txt`. If release v4.1 is unavailable, use the latest 4.x release asset from https://github.com/rsms/inter/releases and say which in the report.

- [ ] **Step 4: Write the theme package**

`app/src/main/java/com/faltasipasen/app/ui/theme/Color.kt`:
```kotlin
package com.faltasipasen.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val LightColors = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E3FF),
    onPrimaryContainer = Color(0xFF0A1570),
    secondary = Color(0xFF3949AB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E3FF),
    onSecondaryContainer = Color(0xFF0A1570),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF16181F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16181F),
    surfaceVariant = Color(0xFFEEF0F7),
    onSurfaceVariant = Color(0xFF5A5F6E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4F9),
    surfaceContainer = Color(0xFFEEF0F7),
    surfaceContainerHigh = Color(0xFFE8EAF2),
    surfaceContainerHighest = Color(0xFFE2E5EE),
    outline = Color(0xFFD7DAE5),
    outlineVariant = Color(0xFFE6E8F0),
    error = Color(0xFFD32F2F),
    onError = Color(0xFFFFFFFF),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C3FF),
    onPrimary = Color(0xFF0E1D78),
    primaryContainer = Color(0xFF2C3A8F),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFB8C3FF),
    onSecondary = Color(0xFF0E1D78),
    secondaryContainer = Color(0xFF2C3A8F),
    onSecondaryContainer = Color(0xFFDDE1FF),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E4EA),
    surface = Color(0xFF1A1C22),
    onSurface = Color(0xFFE3E4EA),
    surfaceVariant = Color(0xFF23262E),
    onSurfaceVariant = Color(0xFFA7ABB8),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF16181D),
    surfaceContainer = Color(0xFF1E2027),
    surfaceContainerHigh = Color(0xFF23262E),
    surfaceContainerHighest = Color(0xFF2A2D36),
    outline = Color(0xFF3A3E49),
    outlineVariant = Color(0xFF2C2F38),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0A0A),
)

val AmoledColors = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF0B0C0F),
    surfaceVariant = Color(0xFF16181D),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF07080A),
    surfaceContainer = Color(0xFF0F1013),
    surfaceContainerHigh = Color(0xFF16181D),
    surfaceContainerHighest = Color(0xFF1D1F25),
    outlineVariant = Color(0xFF1F2127),
)
```

`app/src/main/java/com/faltasipasen/app/ui/theme/Type.kt`:
```kotlin
package com.faltasipasen.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faltasipasen.app.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Cifras de ancho fijo: los porcentajes no "bailan" al animarse. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")

private val base = Typography()

val FaltasTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    displaySmall = base.displaySmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = base.bodyMedium.copy(fontFamily = Inter),
    bodySmall = base.bodySmall.copy(fontFamily = Inter),
    labelLarge = base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
)

val FaltasShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
```

`app/src/main/java/com/faltasipasen/app/ui/theme/Theme.kt`:
```kotlin
package com.faltasipasen.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.faltasipasen.app.data.ThemeMode
import com.faltasipasen.app.domain.RiskLevel

@Immutable
data class RiskColors(val low: Color, val medium: Color, val high: Color) {
    fun of(level: RiskLevel): Color = when (level) {
        RiskLevel.LOW -> low
        RiskLevel.MEDIUM -> medium
        RiskLevel.HIGH -> high
    }
}

private val LightRisk = RiskColors(low = Color(0xFF1E8E5A), medium = Color(0xFFC77700), high = Color(0xFFD32F2F))
private val DarkRisk = RiskColors(low = Color(0xFF5FD39A), medium = Color(0xFFFFB547), high = Color(0xFFFF6B6B))

val LocalRiskColors = staticCompositionLocalOf { LightRisk }

fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
}

@Composable
fun FaltasTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val target = when {
        mode == ThemeMode.AMOLED -> AmoledColors
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalRiskColors provides if (dark) DarkRisk else LightRisk) {
        MaterialTheme(
            colorScheme = animated(target),
            typography = FaltasTypography,
            shapes = FaltasShapes,
            content = content,
        )
    }
}

@Composable
private fun animated(target: ColorScheme): ColorScheme {
    @Composable
    fun Color.anim(): Color = animateColorAsState(this, tween(300), label = "theme").value
    return target.copy(
        primary = target.primary.anim(),
        onPrimary = target.onPrimary.anim(),
        primaryContainer = target.primaryContainer.anim(),
        onPrimaryContainer = target.onPrimaryContainer.anim(),
        secondaryContainer = target.secondaryContainer.anim(),
        onSecondaryContainer = target.onSecondaryContainer.anim(),
        background = target.background.anim(),
        onBackground = target.onBackground.anim(),
        surface = target.surface.anim(),
        onSurface = target.onSurface.anim(),
        surfaceVariant = target.surfaceVariant.anim(),
        onSurfaceVariant = target.onSurfaceVariant.anim(),
        surfaceContainer = target.surfaceContainer.anim(),
        surfaceContainerHigh = target.surfaceContainerHigh.anim(),
        surfaceContainerHighest = target.surfaceContainerHighest.anim(),
        outline = target.outline.anim(),
        outlineVariant = target.outlineVariant.anim(),
    )
}
```

- [ ] **Step 5: Wire the theme into the app**

In `MainViewModel.kt`: add `val themeMode: ThemeMode` as the last field of `UiState` (import `com.faltasipasen.app.data.ThemeMode`) and set `themeMode = settings.themeMode()` in `buildState`.

In `MainActivity.kt`:
- Remove the private `AppTheme` composable and its now-unused imports (`dynamic*ColorScheme`, `darkColorScheme`, `lightColorScheme`, `LocalContext`).
- Replace `setContent { AppTheme { AppRoot() } }` with:
```kotlin
        setContent {
            val viewModel: MainViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val dark = state.themeMode.isDark(isSystemInDarkTheme())
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                )
            }
            FaltasTheme(state.themeMode) {
                Surface(color = MaterialTheme.colorScheme.background) { AppRoot(viewModel) }
            }
        }
```
  (keep the plain `enableEdgeToEdge()` call before `setContent`; imports: `androidx.activity.SystemBarStyle`, `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.material3.Surface`, `androidx.compose.foundation.isSystemInDarkTheme`, `com.faltasipasen.app.ui.theme.FaltasTheme`, `com.faltasipasen.app.ui.theme.isDark`.)
- Change `AppRoot` to take the view model as a required parameter: `private fun AppRoot(viewModel: MainViewModel)`, reading `val state by viewModel.state.collectAsStateWithLifecycle()` inside as before.

- [ ] **Step 6: Build, test, see it on the phone**

```bash
./gradlew assembleDebug testDebugUnitTest
ADB=/c/Users/jesus/AppData/Local/Android/Sdk/platform-tools/adb
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n com.faltasipasen.app/.ui.MainActivity; sleep 3
$ADB exec-out screencap -p > "$TEMP/r3.png"
```
Open the screenshot: existing screens render with the indigo palette and Inter (no Material You colours). If the phone screen is off the capture is black — say so in the report instead of guessing.

- [ ] **Step 7: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: add theme setting and indigo design system with Inter" -m "$TRAILER"
```

---

### Task R4: App shell with bottom navigation and the new Resumen screen

**Files:**
- Create: `app/src/main/java/com/faltasipasen/app/ui/Formatters.kt`, `ui/SummaryScreen.kt`
- Modify: `app/src/main/java/com/faltasipasen/app/ui/Components.kt` (rewrite), `ui/MainViewModel.kt`, `ui/MainActivity.kt`
- Delete: `app/src/main/java/com/faltasipasen/app/ui/HomeScreen.kt`
- Test: `app/src/test/java/com/faltasipasen/app/ui/FormattersTest.kt`

**Interfaces:**
- Consumes: R2 (`RiskLevel`, `HistoryBuilder`, `AbsenceStats.lectivoDays*`, `HoursStats.shortLabel/name/justifiedHours`), R3 (`LocalRiskColors`, `Tabular`, `UiState.themeMode`, `SettingsStore.setThemeMode`).
- Produces:
  - `Formatters.kt`: `formatNumber`, `formatHours`, `formatLongDate`, `formatMillis` (moved unchanged from Components.kt) + `formatDayHeader(date: LocalDate): String`, `formatTramo(tramo: String): String`, `relativeTime(thenMillis: Long, nowMillis: Long): String`.
  - `Components.kt`: `FlatCard(modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)`, `ThinProgress(progress: Float, color: Color, modifier, height: Dp = 4.dp)`, `StatusChip(text: String, color: Color)`, `SectionTitle(text: String, modifier)`, `ScreenHeader(title: String, subtitle: String?, action: (@Composable () -> Unit)? = null)`, `CutoffDateField` (unchanged).
  - `SummaryScreen.kt`: `SummaryScreen(state: UiState, onSync: () -> Unit, onOpenSubject: (String) -> Unit)` and public `AppIcon(size: Int)`.
  - `UiState.history: List<HistoryDay>`; `MainViewModel.setThemeMode(mode: ThemeMode)`.
  - `MainActivity`: `enum class Tab(label, icon) { Summary, Settings }` for now (History is added in R5).

- [ ] **Step 1: Failing formatter tests**

`app/src/test/java/com/faltasipasen/app/ui/FormattersTest.kt`:
```kotlin
package com.faltasipasen.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormattersTest {
    private val now = 1_790_000_000_000L

    @Test
    fun `relative time`() {
        assertEquals("ahora mismo", relativeTime(now - 30_000, now))
        assertEquals("hace 5 min", relativeTime(now - 5 * 60_000, now))
        assertEquals("hace 2 h", relativeTime(now - 2 * 3_600_000 - 60_000, now))
        assertEquals("hace 1 día", relativeTime(now - 26 * 3_600_000L, now))
        assertEquals("hace 3 días", relativeTime(now - 3 * 24 * 3_600_000L, now))
        assertEquals("ahora mismo", relativeTime(now + 60_000, now))
    }

    @Test
    fun `day header is capitalised Spanish`() {
        assertEquals("Jueves, 24 de septiembre", formatDayHeader(LocalDate.of(2026, 9, 24)))
    }

    @Test
    fun `tramo uses an en dash`() {
        assertEquals("8:15 – 9:15", formatTramo(" 8:15 - 9:15 "))
    }

    @Test
    fun `hours drop a trailing zero decimal`() {
        assertEquals("112 h", formatHours(112.0))
        assertEquals("18,6 h", formatHours(18.6))
    }
}
```
Run `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.ui.*"` → FAILS (unresolved `relativeTime`, …).

- [ ] **Step 2: Formatters and components**

`app/src/main/java/com/faltasipasen/app/ui/Formatters.kt`:
```kotlin
package com.faltasipasen.app.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish: Locale = Locale.forLanguageTag("es-ES")
private val madrid: ZoneId = ZoneId.of("Europe/Madrid")
private val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", spanish)
private val dayHeader: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", spanish)
private val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", spanish)
private val dash = Regex("\\s*-\\s*")

fun formatNumber(value: Double): String = DecimalFormat("0.#", DecimalFormatSymbols(spanish)).format(value)

fun formatHours(value: Double): String = "${formatNumber(value)} h"

fun formatLongDate(date: LocalDate): String = date.format(longDate)

fun formatMillis(millis: Long): String = Instant.ofEpochMilli(millis).atZone(madrid).format(dateTime)

fun formatDayHeader(date: LocalDate): String = date.format(dayHeader).replaceFirstChar { it.titlecase(spanish) }

fun formatTramo(tramo: String): String = tramo.trim().replace(dash, " – ")

fun relativeTime(thenMillis: Long, nowMillis: Long): String {
    val minutes = (nowMillis - thenMillis).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "ahora mismo"
        minutes < 60 -> "hace $minutes min"
        minutes < 24 * 60 -> "hace ${minutes / 60} h"
        else -> {
            val days = minutes / (24 * 60)
            if (days == 1L) "hace 1 día" else "hace $days días"
        }
    }
}
```

Rewrite `app/src/main/java/com/faltasipasen/app/ui/Components.kt` (the four format functions move to Formatters.kt; `PercentBar` is removed):
```kotlin
package com.faltasipasen.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun FlatCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, border = border, colors = colors, content = content)
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, border = border, colors = colors, content = content)
    }
}

@Composable
fun ThinProgress(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "progress")
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier.fillMaxWidth().height(height),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun StatusChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
}

@Composable
fun ScreenHeader(title: String, subtitle: String?, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CutoffDateField(value: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text("Fin de clases (inicio FCT): ${formatLongDate(value)}")
    }
    if (open) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
```
Run the ui tests → PASS.

- [ ] **Step 3: View model additions**

In `MainViewModel.kt`:
- Add `val history: List<HistoryDay>` to `UiState` (right after `stats`), import `com.faltasipasen.app.domain.HistoryBuilder` and `com.faltasipasen.app.domain.HistoryDay`.
- In `buildState` compute and pass it:
```kotlin
        val history = snapshot?.let {
            HistoryBuilder.build(it.schedule, it.absences, settings.cutoffDate(), graph.today())
        }.orEmpty()
```
- Add:
```kotlin
    fun setThemeMode(mode: ThemeMode) = graph.settings.setThemeMode(mode)
```

- [ ] **Step 4: The Resumen screen**

`app/src/main/java/com/faltasipasen/app/ui/SummaryScreen.kt`:
```kotlin
package com.faltasipasen.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.faltasipasen.app.R
import com.faltasipasen.app.domain.AbsenceStats
import com.faltasipasen.app.domain.AlertFormatter
import com.faltasipasen.app.domain.HoursStats
import com.faltasipasen.app.domain.RiskLevel
import com.faltasipasen.app.ui.theme.LocalRiskColors
import com.faltasipasen.app.ui.theme.Tabular
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(state: UiState, onSync: () -> Unit, onOpenSubject: (String) -> Unit) {
    PullToRefreshBox(isRefreshing = state.syncing, onRefresh = onSync, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryHeader(state, onSync) }
            state.lastError?.let { error -> item { ErrorNote(error) } }
            val stats = state.stats
            if (stats == null) {
                item { EmptySummary(state.syncing, onSync) }
            } else {
                item { MainPercentCard(stats.global, state.thresholdPercent) }
                item { BreakdownRow(stats.global) }
                item { CourseProgressCard(stats) }
                item { SectionTitle("Módulos") }
                items(stats.subjects, key = { it.label }) { subject ->
                    SubjectRow(subject, state.thresholdPercent, onClick = { onOpenSubject(subject.label) })
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(state: UiState, onSync: () -> Unit) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    val subtitle = state.lastSyncMillis?.let { "Actualizado ${relativeTime(it, now)}" } ?: "Sin sincronizar todavía"
    ScreenHeader(title = "Resumen", subtitle = subtitle) {
        val rotation by rememberInfiniteTransition(label = "sync").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
            label = "rotation",
        )
        IconButton(onClick = onSync, enabled = !state.syncing) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = "Sincronizar ahora",
                modifier = if (state.syncing) Modifier.rotate(rotation) else Modifier,
            )
        }
    }
}

@Composable
private fun MainPercentCard(global: HoursStats, threshold: Double) {
    val risk = RiskLevel.of(global.percent, threshold)
    val color = LocalRiskColors.current.of(risk)
    val animated by animateFloatAsState(global.percent.toFloat(), tween(700), label = "percent")
    FlatCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                AlertFormatter.percent(animated.toDouble()),
                style = MaterialTheme.typography.displayLarge.merge(Tabular),
                color = color,
            )
            Text("de faltas del curso", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ThinProgress((global.percent / threshold).toFloat(), color, height = 6.dp)
            Row(Modifier.fillMaxWidth()) {
                Text("0 %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    "Umbral ${AlertFormatter.percent(threshold)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (risk == RiskLevel.HIGH) {
                    "Has superado tu umbral del ${AlertFormatter.percent(threshold)}"
                } else {
                    "Te quedan ${formatHours(global.marginHours(threshold))} de margen"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (risk == RiskLevel.HIGH) color else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BreakdownRow(global: HoursStats) {
    val risk = LocalRiskColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("Injustificadas", formatHours(global.unjustifiedHours), risk.high, Modifier.weight(1f))
        StatCard("Justificadas", formatHours(global.justifiedHours), risk.low, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, dot: Color, modifier: Modifier) {
    FlatCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleLarge.merge(Tabular))
        }
    }
}

@Composable
private fun CourseProgressCard(stats: AbsenceStats) {
    val total = stats.lectivoDaysTotal
    val elapsed = stats.lectivoDaysElapsed
    val fraction = if (total == 0) 0.0 else elapsed.toDouble() / total
    FlatCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Curso transcurrido", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Llevas $elapsed de $total días lectivos (${AlertFormatter.percent(fraction * 100)})",
                style = MaterialTheme.typography.bodyMedium.merge(Tabular),
            )
            ThinProgress(fraction.toFloat(), MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SubjectRow(subject: HoursStats, threshold: Double, onClick: () -> Unit) {
    val hasSchedule = subject.courseHours > 0.0
    val color = if (hasSchedule) {
        LocalRiskColors.current.of(RiskLevel.of(subject.percent, threshold))
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    FlatCard(onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(subject.shortLabel, style = MaterialTheme.typography.titleMedium)
                    if (subject.abbreviation.isNotEmpty() && subject.name.isNotEmpty()) {
                        Text(
                            subject.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(AlertFormatter.percent(subject.percent), style = MaterialTheme.typography.titleMedium.merge(Tabular), color = color)
            }
            if (hasSchedule) {
                ThinProgress((subject.percent / threshold).toFloat(), color)
                Text(
                    "${formatHours(subject.absentHours)} de ${formatHours(subject.courseHours)} · margen ${formatHours(subject.marginHours(threshold))}",
                    style = MaterialTheme.typography.labelMedium.merge(Tabular),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "No está en tu horario · ${formatHours(subject.absentHours)} de falta",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorNote(message: String) {
    val color = LocalRiskColors.current.high
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f)).padding(14.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun EmptySummary(syncing: Boolean, onSync: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(size = 72)
        Text(
            if (syncing) "Sincronizando con iPasen…" else "Aún no hay datos de iPasen",
            style = MaterialTheme.typography.titleMedium,
        )
        if (!syncing) Button(onClick = onSync) { Text("Sincronizar") }
    }
}

/** Icono de la app dibujado con Compose (painterResource no admite iconos adaptativos). */
@Composable
fun AppIcon(size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.28f).dp)).background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size((size * 1.5f).dp))
    }
}
```

- [ ] **Step 5: Shell with bottom navigation**

In `MainActivity.kt` replace `private enum class Screen { Home, Settings }` and the body of `AppRoot` with:
```kotlin
private enum class Tab(val label: String, val icon: ImageVector) {
    Summary("Resumen", Icons.Rounded.Home),
    Settings("Ajustes", Icons.Rounded.Settings),
}

@Composable
private fun AppRoot(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Summary) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    if (!state.hasCredentials) {
        SetupScreen(
            initialThreshold = state.thresholdPercent,
            initialCutoff = state.cutoffDate,
            onSubmit = { username, password, threshold, cutoff ->
                viewModel.completeSetup(username, password, threshold, cutoff)
                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                tab = Tab.Summary
            },
        )
        return
    }

    BackHandler(enabled = tab != Tab.Summary) { tab = Tab.Summary }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Crossfade(targetState = tab, animationSpec = tween(220), label = "tab", modifier = Modifier.padding(padding)) { current ->
            when (current) {
                Tab.Summary -> SummaryScreen(state, onSync = viewModel::syncNow, onOpenSubject = { })
                Tab.Settings -> SettingsScreen(
                    state = state,
                    onBack = { tab = Tab.Summary },
                    onSave = { threshold, cutoff, interval, username, newPassword ->
                        viewModel.saveSettings(threshold, cutoff, interval, username, newPassword)
                        tab = Tab.Summary
                    },
                    onLogout = {
                        viewModel.logout()
                        tab = Tab.Summary
                    },
                )
            }
        }
    }
}
```
(`onOpenSubject` is wired to the History tab in R5.) Imports to add: `androidx.compose.animation.Crossfade`, `androidx.compose.animation.core.tween`, `androidx.compose.foundation.layout.padding`, `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.rounded.Home`, `androidx.compose.material.icons.rounded.Settings`, `androidx.compose.material3.Icon`, `androidx.compose.material3.NavigationBar`, `androidx.compose.material3.NavigationBarItem`, `androidx.compose.material3.Scaffold`, `androidx.compose.material3.Text`, `androidx.compose.ui.Modifier`, `androidx.compose.ui.graphics.vector.ImageVector`.

Delete `HomeScreen.kt`.

- [ ] **Step 6: Build, test, see it on the phone**

```bash
./gradlew assembleDebug testDebugUnitTest
ADB=/c/Users/jesus/AppData/Local/Android/Sdk/platform-tools/adb
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n com.faltasipasen.app/.ui.MainActivity; sleep 4
$ADB exec-out screencap -p > "$TEMP/r4.png"
```
Open the screenshot and check it against the spec's Resumen section (big coloured %, "de faltas del curso", bar with "Umbral", margin line, the two stat cards, course progress, module rows). Module titles show the upper-case key until the phone syncs with the R1 scraper — expected. Report exactly what you saw; a black capture means the screen was off.

- [ ] **Step 7: Commit**

```bash
git add -A app/src/main/java/com/faltasipasen/app/ui app/src/test/java/com/faltasipasen/app/ui
git commit -m "feat: add bottom navigation and the redesigned summary screen" -m "$TRAILER"
```

---

### Task R5: Historial screen

**Files:**
- Create: `app/src/main/java/com/faltasipasen/app/ui/HistoryScreen.kt`
- Modify: `app/src/main/java/com/faltasipasen/app/ui/MainActivity.kt`

**Interfaces:**
- Consumes: `HistoryBuilder.filter/filterOptions`, `HistoryDay`, `HistoryEntry.label`, `formatDayHeader`, `formatTramo`, `formatHours`, `FlatCard`, `StatusChip`, `ScreenHeader`, `LocalRiskColors`, `Tabular`, `UiState.history`.
- Produces: `HistoryScreen(history: List<HistoryDay>, filter: String?, onFilterChange: (String?) -> Unit)`; `Tab.History` between Summary and Settings.

- [ ] **Step 1: Write the screen**

`app/src/main/java/com/faltasipasen/app/ui/HistoryScreen.kt`:
```kotlin
package com.faltasipasen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.faltasipasen.app.domain.HistoryBuilder
import com.faltasipasen.app.domain.HistoryDay
import com.faltasipasen.app.domain.HistoryEntry
import com.faltasipasen.app.ui.theme.LocalRiskColors
import com.faltasipasen.app.ui.theme.Tabular

@Composable
fun HistoryScreen(history: List<HistoryDay>, filter: String?, onFilterChange: (String?) -> Unit) {
    val options = remember(history) { HistoryBuilder.filterOptions(history) }
    val days = remember(history, filter) { HistoryBuilder.filter(history, filter) }
    val entries = days.flatMap { it.entries }
    val count = entries.size
    val subtitle = "${if (count == 1) "1 falta" else "$count faltas"} · ${formatHours(entries.sumOf { it.hours })}"

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 20.dp)) { ScreenHeader(title = "Historial", subtitle = subtitle) }
        if (options.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = filter == null, onClick = { onFilterChange(null) }, label = { Text("Todos") }) }
                items(options, key = { it.key }) { option ->
                    FilterChip(
                        selected = filter == option.key,
                        onClick = { onFilterChange(option.key) },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        if (days.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (filter == null) "Sin faltas registradas. ¡Sigue así!" else "Sin faltas en este módulo.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                days.forEach { day ->
                    item(key = "day-${day.date}") {
                        Text(
                            formatDayHeader(day.date),
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(day.entries, key = { "${it.date}-${it.tramo}-${it.subjectKey}" }) { HistoryRow(it) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    val risk = LocalRiskColors.current
    FlatCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (entry.tramo.isNotEmpty()) {
                    Text(
                        formatTramo(entry.tramo),
                        style = MaterialTheme.typography.labelMedium.merge(Tabular),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(entry.label, style = MaterialTheme.typography.titleSmall)
                if (entry.abbreviation.isNotEmpty() && entry.name.isNotEmpty()) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entry.justified) StatusChip("Justificada", risk.low) else StatusChip("Injustificada", risk.high)
                Text(formatHours(entry.hours), style = MaterialTheme.typography.labelMedium.merge(Tabular))
            }
        }
    }
}
```

- [ ] **Step 2: Add the tab**

In `MainActivity.kt`:
- `Tab` becomes `Summary("Resumen", Icons.Rounded.Home), History("Historial", Icons.AutoMirrored.Rounded.List), Settings("Ajustes", Icons.Rounded.Settings)` (import `androidx.compose.material.icons.automirrored.rounded.List`).
- Add `var historyFilter by rememberSaveable { mutableStateOf<String?>(null) }` next to `tab`.
- `NavigationBarItem` `onClick`: `{ if (item == Tab.History) historyFilter = null; tab = item }`.
- `Tab.Summary -> SummaryScreen(state, onSync = viewModel::syncNow, onOpenSubject = { key -> historyFilter = key; tab = Tab.History })`
- `Tab.History -> HistoryScreen(state.history, historyFilter, onFilterChange = { historyFilter = it })`
- In the Settings `onLogout` also reset `historyFilter = null`.

- [ ] **Step 3: Build, test, install**

```bash
./gradlew assembleDebug testDebugUnitTest
/c/Users/jesus/AppData/Local/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```
(The History tab cannot be opened from adb on this phone; the controller verifies it with the user in R8.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/faltasipasen/app/ui
git commit -m "feat: add absence history tab with module filters" -m "$TRAILER"
```

---

### Task R6: Ajustes tab with theme selector

**Files:**
- Modify: `app/src/main/java/com/faltasipasen/app/ui/SettingsScreen.kt` (rewrite), `ui/MainActivity.kt`

**Interfaces:**
- Consumes: `ThemeMode`, `MainViewModel.setThemeMode`, `InputValidation`, `CutoffDateField`, `FlatCard`, `ScreenHeader`, `LocalRiskColors`, `BuildConfig.VERSION_NAME`.
- Produces: `SettingsScreen(state: UiState, onThemeChange: (ThemeMode) -> Unit, onSave: (Double, LocalDate, Int, String, String) -> Unit, onLogout: () -> Unit)` — no `onBack` any more.

- [ ] **Step 1: Rewrite the screen**

`app/src/main/java/com/faltasipasen/app/ui/SettingsScreen.kt`:
```kotlin
package com.faltasipasen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.faltasipasen.app.BuildConfig
import com.faltasipasen.app.data.ThemeMode
import com.faltasipasen.app.ui.theme.LocalRiskColors
import kotlinx.coroutines.delay
import java.time.LocalDate

private val themeLabels = mapOf(
    ThemeMode.SYSTEM to "Sistema",
    ThemeMode.LIGHT to "Claro",
    ThemeMode.DARK to "Oscuro",
    ThemeMode.AMOLED to "AMOLED",
)

@Composable
fun SettingsScreen(
    state: UiState,
    onThemeChange: (ThemeMode) -> Unit,
    onSave: (thresholdPercent: Double, cutoff: LocalDate, intervalHours: Int, username: String, newPassword: String) -> Unit,
    onLogout: () -> Unit,
) {
    var thresholdText by rememberSaveable { mutableStateOf(formatNumber(state.thresholdPercent)) }
    var cutoff by rememberSaveable { mutableStateOf(state.cutoffDate) }
    var interval by rememberSaveable { mutableIntStateOf(state.syncIntervalHours) }
    var username by rememberSaveable { mutableStateOf(state.username) }
    var newPassword by remember { mutableStateOf("") }
    var confirmLogout by remember { mutableStateOf(false) }
    var savedAt by remember { mutableLongStateOf(0L) }
    val threshold = InputValidation.parseThreshold(thresholdText)

    LaunchedEffect(savedAt) {
        if (savedAt != 0L) {
            delay(2_500)
            savedAt = 0L
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = "Ajustes", subtitle = null)

        SettingsSection("Apariencia") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.themeMode == mode,
                        onClick = { onThemeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        label = { Text(themeLabels.getValue(mode), maxLines = 1) },
                    )
                }
            }
        }

        SettingsSection("Avisos") {
            OutlinedTextField(
                value = thresholdText,
                onValueChange = { thresholdText = it },
                label = { Text("Avisarme al llegar al (%)") },
                singleLine = true,
                isError = threshold == null,
                supportingText = { if (threshold == null) Text("Escribe un número entre 1 y 100") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            CutoffDateField(value = cutoff, onChange = { cutoff = it }, modifier = Modifier.fillMaxWidth())
        }

        SettingsSection("Sincronización") {
            Text("Comprobar iPasen cada", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputValidation.SYNC_INTERVALS.forEach { hours ->
                    FilterChip(selected = interval == hours, onClick = { interval = hours }, label = { Text("$hours h") })
                }
            }
        }

        SettingsSection("Cuenta de iPasen") {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Nueva contraseña (vacío = no cambiar)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = {
                onSave(threshold!!, cutoff, interval, username, newPassword)
                newPassword = ""
                savedAt = System.currentTimeMillis()
            },
            enabled = threshold != null && username.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (savedAt != 0L) "Cambios guardados" else "Guardar cambios") }

        OutlinedButton(
            onClick = { confirmLogout = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalRiskColors.current.high),
        ) { Text("Cerrar sesión y borrar datos") }

        Text(
            "Versión ${BuildConfig.VERSION_NAME} · Datos solo en este móvil",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("¿Borrar todo?") },
            text = { Text("Se borrarán de este móvil tu usuario, tu contraseña y los datos descargados de iPasen.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        FlatCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}
```

- [ ] **Step 2: Wire it**

In `MainActivity.kt` replace the `Tab.Settings ->` branch with:
```kotlin
                Tab.Settings -> SettingsScreen(
                    state = state,
                    onThemeChange = viewModel::setThemeMode,
                    onSave = viewModel::saveSettings,
                    onLogout = {
                        viewModel.logout()
                        historyFilter = null
                        tab = Tab.Summary
                    },
                )
```

- [ ] **Step 3: Build, test, install**

Run: `./gradlew assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL, all tests pass. Install with `adb install -r` as before.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/faltasipasen/app/ui
git commit -m "feat: redesign settings tab with theme selector" -m "$TRAILER"
```

---

### Task R7: Welcome screen

**Files:**
- Create: `app/src/main/java/com/faltasipasen/app/ui/WelcomeScreen.kt`
- Delete: `app/src/main/java/com/faltasipasen/app/ui/SetupScreen.kt`
- Modify: `app/src/main/java/com/faltasipasen/app/ui/MainActivity.kt` (call `WelcomeScreen` instead of `SetupScreen`, same arguments)

**Interfaces:**
- Consumes: `AppIcon` (R4), `CutoffDateField`, `InputValidation`, `formatNumber`.
- Produces: `WelcomeScreen(initialThreshold: Double, initialCutoff: LocalDate, onSubmit: (String, String, Double, LocalDate) -> Unit)` — same contract as the old `SetupScreen`.

- [ ] **Step 1: Write the screen**

`app/src/main/java/com/faltasipasen/app/ui/WelcomeScreen.kt`:
```kotlin
package com.faltasipasen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun WelcomeScreen(
    initialThreshold: Double,
    initialCutoff: LocalDate,
    onSubmit: (username: String, password: String, thresholdPercent: Double, cutoff: LocalDate) -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var thresholdText by rememberSaveable { mutableStateOf(formatNumber(initialThreshold)) }
    var cutoff by rememberSaveable { mutableStateOf(initialCutoff) }
    val threshold = InputValidation.parseThreshold(thresholdText)

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        AppIcon(size = 80)
        Text("Faltas iPasen", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Tus faltas de iPasen, bajo control",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Usuario de iPasen") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Las faltas cuentan hasta el fin de las clases (inicio de la FCT). Comprueba la fecha:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        CutoffDateField(value = cutoff, onChange = { cutoff = it }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = thresholdText,
            onValueChange = { thresholdText = it },
            label = { Text("Avisarme al llegar al (%)") },
            singleLine = true,
            isError = threshold == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSubmit(username, password, threshold!!, cutoff) },
            enabled = threshold != null && InputValidation.credentialsValid(username, password),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Empezar") }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                "Tu usuario y contraseña se guardan cifrados solo en este móvil.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
```

- [ ] **Step 2: Wire it, remove the old screen**

In `MainActivity.kt` replace `SetupScreen(` with `WelcomeScreen(` (same named arguments). Delete `SetupScreen.kt`. Remove any import made unused.

- [ ] **Step 3: Build, test, install**

Run: `./gradlew assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL, all tests pass. Install with `adb install -r`.

- [ ] **Step 4: Commit**

```bash
git add -A app/src/main/java/com/faltasipasen/app/ui
git commit -m "feat: redesign the welcome screen" -m "$TRAILER"
```

---

### Task R8: Verification on the user's phone (controller, with the user)

- [ ] Install the final build; ask the user to tap sync (the new abbreviation/tramo fields arrive with this sync).
- [ ] For each theme, the user selects it in Ajustes; capture `adb exec-out screencap -p > "$TEMP/<tema>-<pestaña>.png"` for Resumen, Historial and Ajustes; check against the spec (colours, Inter, semáforo, abbreviations + names, history grouped by date, AMOLED pure black background, status-bar icons readable in every theme).
- [ ] The user taps a module in Resumen → Historial opens filtered by it; "Todos" clears the filter.
- [ ] Anything found goes through a normal fix task (implementer + review); then report to the user with the screenshots.
