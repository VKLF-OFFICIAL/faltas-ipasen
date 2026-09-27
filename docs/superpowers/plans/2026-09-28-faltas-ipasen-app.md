# Faltas iPasen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android app that logs into iPasen with the student's stored credentials, reads their timetable, school calendar and absences, computes the absence percentage over the total teaching hours up to the FCT start date, and sends a push notification when it crosses a threshold.

**Architecture:** Single-module Kotlin app. Pure domain logic (calculation, threshold evaluation, parsing of scraper output) is JVM-unit-tested. A hidden `WebView` runs injected JavaScript against the real iPasen site and returns JSON through a JS bridge; a `CoroutineWorker` runs the sync periodically, stores a JSON snapshot and fires notifications. Jetpack Compose UI with three screens (setup, home, settings). Manual DI through an `AppGraph` held by the `Application`.

**Tech Stack:** Kotlin 2.2.20, AGP 8.13.0, Gradle 9.1.0, Jetpack Compose (BOM 2025.09.00, Material 3), WorkManager 2.10.3, kotlinx.serialization 1.9.0, kotlinx.coroutines 1.10.2, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-27-faltas-ipasen-app-design.md`

## Global Constraints

- Project root: `C:\Users\jesus\faltas-ipasen-app` (Bash path `/c/Users/jesus/faltas-ipasen-app`). Package / applicationId: `com.faltasipasen.app`.
- `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36`. AGP downloads SDK platform 36 automatically (licenses are already accepted in `C:\Users\jesus\AppData\Local\Android\Sdk`).
- JDK: Android Studio's bundled JBR. Every Gradle command runs from Bash as `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew <task>` (written below as `./gradlew <task>` — always prefix the `JAVA_HOME=...`).
- `adb` = `/c/Users/jesus/AppData/Local/Android/Sdk/platform-tools/adb`.
- The ONLY manual inputs in the app: iPasen username + password, FCT cut-off date (prefilled with 15 Feb of the current course), threshold % (prefilled 15). Everything else comes from iPasen automatically.
- % of absences = absent hours (from course start, 1 Sept, to `min(today, cutoff)`) ÷ total teaching hours of the whole course (all lectivo days from 1 Sept to cutoff, including future ones) × 100. This percentage drives the alert.
- Credentials: encrypted with an Android Keystore AES-256-GCM key; never logged, never in plain text; `android:allowBackup="false"`.
- No server of our own; network traffic only to iPasen / Junta de Andalucía hosts. The WebView refuses navigations to other hosts.
- All user-facing text in Spanish (Spain). Code identifiers and commit messages in English.
- Never commit personal data (real names, DNI, real absence records, screenshots of iPasen) — `docs/scraper-selectors.md` contains selectors and URL shapes only.
- Commit trailer: every commit message ends with the attribution trailer lines the harness provides for the executing session (at plan time: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and `Claude-Session: https://claude.ai/code/session_01Nni74mP4bcfJHgPoZaeheQ`). Steps write this as `-m "$TRAILER"`; define it once per shell, e.g. `TRAILER=$'Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01Nni74mP4bcfJHgPoZaeheQ'`.
- Unit tests: `./gradlew testDebugUnitTest`. They must not touch Android framework statics (only interfaces such as `SharedPreferences`).

## File Structure

```
faltas-ipasen-app/
├── settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradlew*, .gitignore, README.md
├── docs/scraper-selectors.md                      (Task 11) what was discovered in iPasen
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/scraper/                    JS injected into the WebView
        │   │   ├── common.js                      helpers + result contract (Task 8)
        │   │   ├── login.js, login_check.js       (stub Task 8, real Task 11)
        │   │   └── schedule.js, calendar.js, absences.js  (stub Task 8, real Task 11)
        │   ├── res/values/strings.xml
        │   └── java/com/faltasipasen/app/
        │       ├── FaltasApp.kt                   Application: graph, channels, scheduling (Task 9)
        │       ├── AppGraph.kt                    manual DI (Task 9)
        │       ├── domain/
        │       │   ├── Models.kt                  ScheduleSlot, CalendarDay, Absence, Credentials, HoursStats, AbsenceStats (Task 2)
        │       │   ├── LocalDateSerializer.kt     (Task 2)
        │       │   ├── CourseDates.kt             course start / default cutoff (Task 2)
        │       │   ├── AbsenceCalculator.kt       (Task 2)
        │       │   ├── ThresholdEvaluator.kt      (Task 3)
        │       │   └── AlertFormatter.kt          (Task 3)
        │       ├── scraper/
        │       │   ├── ScraperContract.kt         IpasenScraper, ScrapeResult, ScrapeException (Task 4)
        │       │   ├── ScrapeParsers.kt           JSON → domain (Task 4)
        │       │   ├── IpasenSite.kt              URLs / steps / allowed hosts (skeleton Task 8, values Task 11)
        │       │   └── WebViewScraper.kt          WebView engine (Task 8)
        │       ├── data/
        │       │   ├── Snapshot.kt                Snapshot, SnapshotStore, FileSnapshotStore (Task 5)
        │       │   ├── SettingsStore.kt           interface + PrefsSettingsStore (Task 6)
        │       │   └── KeystoreCipher.kt          StringCipher + KeystoreCipher (Task 6)
        │       ├── sync/
        │       │   ├── SyncRunner.kt              orchestration (Task 7)
        │       │   ├── SyncWorker.kt              (Task 9)
        │       │   └── SyncScheduler.kt           (Task 9)
        │       ├── notify/NotificationAlertSink.kt (Task 9)
        │       └── ui/
        │           ├── MainActivity.kt            theme + root navigation (Task 1 minimal, Task 10 real)
        │           ├── MainViewModel.kt           (Task 10)
        │           ├── InputValidation.kt         (Task 10)
        │           ├── Components.kt              formatters, date field, percent bar (Task 10)
        │           └── SetupScreen.kt, HomeScreen.kt, SettingsScreen.kt (Task 10)
        └── test/java/com/faltasipasen/app/
            ├── testutil/InMemorySharedPreferences.kt, testutil/ReversingCipher.kt (Task 6)
            ├── domain/AbsenceCalculatorTest.kt, CourseDatesTest.kt (Task 2)
            ├── domain/ThresholdEvaluatorTest.kt, AlertFormatterTest.kt (Task 3)
            ├── scraper/ScrapeParsersTest.kt (Task 4)
            ├── data/FileSnapshotStoreTest.kt (Task 5), PrefsSettingsStoreTest.kt (Task 6)
            ├── sync/SyncRunnerTest.kt (Task 7)
            └── ui/InputValidationTest.kt (Task 10)
```

---

### Task 1: Project scaffold that builds

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`, `local.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/java/com/faltasipasen/app/ui/MainActivity.kt`
- Generated: `gradlew`, `gradlew.bat`, `gradle/wrapper/*`

**Interfaces:**
- Produces: a compiling app module with Compose, WorkManager, serialization and coroutines on the classpath; `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` work.

- [ ] **Step 1: Create a minimal settings file and generate the Gradle wrapper**

The repo already exists (spec committed). Gradle is not installed globally, so bootstrap the wrapper with a downloaded distribution:

```bash
cd /c/Users/jesus/faltas-ipasen-app
printf 'rootProject.name = "FaltasIpasen"\n' > settings.gradle.kts
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
SCRATCH="$(mktemp -d)"
curl -sSL -o "$SCRATCH/gradle.zip" https://services.gradle.org/distributions/gradle-9.1.0-bin.zip
unzip -q "$SCRATCH/gradle.zip" -d "$SCRATCH"
"$SCRATCH/gradle-9.1.0/bin/gradle" wrapper --gradle-version 9.1.0
ls gradlew gradlew.bat gradle/wrapper
```
Expected: the three paths are listed.

- [ ] **Step 2: Write the build files**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "FaltasIpasen"
include(":app")
```

`build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.13.0"
kotlin = "2.2.20"
coreKtx = "1.17.0"
lifecycle = "2.9.4"
activityCompose = "1.11.0"
composeBom = "2025.09.00"
work = "2.10.3"
serialization = "1.9.0"
coroutines = "1.10.2"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

`app/build.gradle.kts`:
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.faltasipasen.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.faltasipasen.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`.gitignore`:
```
.gradle/
.kotlin/
build/
local.properties
.idea/
*.iml
captures/
.DS_Store
```

`local.properties` (ignored by git):
```properties
sdk.dir=C\:\\Users\\jesus\\AppData\\Local\\Android\\Sdk
```

- [ ] **Step 3: Write the manifest, strings and a minimal activity**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">Faltas iPasen</string>
</resources>
```

`app/src/main/java/com/faltasipasen/app/ui/MainActivity.kt`:
```kotlin
package com.faltasipasen.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text("Faltas iPasen") } }
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` (the test task reports no tests). If a version fails to resolve, read the exact error and change only the offending version in `libs.versions.toml` to the nearest published one on https://maven.google.com / Maven Central; mention the change in the commit message.

- [ ] **Step 5: Commit**

```bash
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat app
git commit -m "build: scaffold Android app with Compose, WorkManager and serialization" -m "$TRAILER"
```

---

### Task 2: Domain models and absence calculation

**Files:**
- Create: `app/src/main/java/com/faltasipasen/app/domain/Models.kt`, `domain/LocalDateSerializer.kt`, `domain/CourseDates.kt`, `domain/AbsenceCalculator.kt`
- Test: `app/src/test/java/com/faltasipasen/app/domain/CourseDatesTest.kt`, `domain/AbsenceCalculatorTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class ScheduleSlot(val subject: String, val dayOfWeek: Int /*1=lunes..7=domingo*/, val hours: Double)`
  - `@Serializable data class CalendarDay(val date: LocalDate, val lectivo: Boolean)`
  - `@Serializable data class Absence(val date: LocalDate, val subject: String, val hours: Double, val justified: Boolean)`
  - `data class Credentials(val username: String, val password: String)`
  - `data class HoursStats(val label: String, val courseHours: Double, val absentHours: Double, val unjustifiedHours: Double)` with `percent`, `unjustifiedPercent`, `fun marginHours(thresholdPercent: Double): Double`
  - `data class AbsenceStats(val global: HoursStats, val subjects: List<HoursStats>, val courseStart: LocalDate, val cutoff: LocalDate, val countedUntil: LocalDate)`
  - `object CourseDates { fun defaultCutoff(today: LocalDate): LocalDate; fun courseStartFor(cutoff: LocalDate): LocalDate }`
  - `object AbsenceCalculator { const val GLOBAL_LABEL = "Total"; fun calculate(schedule: List<ScheduleSlot>, calendar: List<CalendarDay>, absences: List<Absence>, cutoff: LocalDate, today: LocalDate): AbsenceStats }`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/faltasipasen/app/domain/CourseDatesTest.kt`:
```kotlin
package com.faltasipasen.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CourseDatesTest {
    @Test
    fun `default cutoff in autumn is 15 February of next year`() {
        assertEquals(LocalDate.of(2027, 2, 15), CourseDates.defaultCutoff(LocalDate.of(2026, 9, 28)))
    }

    @Test
    fun `default cutoff in January is 15 February of the same year`() {
        assertEquals(LocalDate.of(2027, 2, 15), CourseDates.defaultCutoff(LocalDate.of(2027, 1, 10)))
    }

    @Test
    fun `default cutoff in summer still belongs to the course that just ended`() {
        assertEquals(LocalDate.of(2027, 2, 15), CourseDates.defaultCutoff(LocalDate.of(2027, 7, 1)))
    }

    @Test
    fun `course starts on 1 September before the cutoff`() {
        assertEquals(LocalDate.of(2026, 9, 1), CourseDates.courseStartFor(LocalDate.of(2027, 2, 15)))
    }
}
```

`app/src/test/java/com/faltasipasen/app/domain/AbsenceCalculatorTest.kt`:
```kotlin
package com.faltasipasen.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AbsenceCalculatorTest {
    private val cutoff = LocalDate.of(2027, 2, 15)
    private val today = LocalDate.of(2026, 9, 30)

    // Lunes: REDES 2 sesiones de 1 h. Martes: BD 1 h.
    private val schedule = listOf(
        ScheduleSlot("REDES", 1, 1.0),
        ScheduleSlot("REDES", 1, 1.0),
        ScheduleSlot("BD", 2, 1.0),
    )

    private val calendar = listOf(
        CalendarDay(LocalDate.of(2026, 8, 31), true),   // lunes antes del inicio de curso: no cuenta
        CalendarDay(LocalDate.of(2026, 9, 14), true),   // lunes
        CalendarDay(LocalDate.of(2026, 9, 15), true),   // martes
        CalendarDay(LocalDate.of(2026, 9, 21), true),   // lunes
        CalendarDay(LocalDate.of(2026, 9, 22), false),  // martes no lectivo
        CalendarDay(LocalDate.of(2026, 10, 5), true),   // lunes futuro: cuenta para el total del curso
    )

    private val absences = listOf(
        Absence(LocalDate.of(2026, 9, 14), "REDES", 1.0, justified = false),
        Absence(LocalDate.of(2026, 9, 15), "BD", 1.0, justified = true),
        Absence(LocalDate.of(2026, 10, 5), "REDES", 1.0, justified = false), // posterior a hoy: aún no cuenta
    )

    private fun calc(
        cutoff: LocalDate = this.cutoff,
        today: LocalDate = this.today,
        absences: List<Absence> = this.absences,
    ) = AbsenceCalculator.calculate(schedule, calendar, absences, cutoff, today)

    private fun AbsenceStats.subject(label: String) = subjects.single { it.label == label }

    @Test
    fun `course hours include every lectivo day until the cutoff, also future ones`() {
        val stats = calc()
        assertEquals(6.0, stats.subject("REDES").courseHours, 1e-9)
        assertEquals(1.0, stats.subject("BD").courseHours, 1e-9)
        assertEquals(7.0, stats.global.courseHours, 1e-9)
    }

    @Test
    fun `absences are counted only until today`() {
        val stats = calc()
        assertEquals(1.0, stats.subject("REDES").absentHours, 1e-9)
        assertEquals(1.0, stats.subject("BD").absentHours, 1e-9)
        assertEquals(2.0, stats.global.absentHours, 1e-9)
        assertEquals(LocalDate.of(2026, 9, 30), stats.countedUntil)
    }

    @Test
    fun `percent is absent hours over total course hours`() {
        val stats = calc()
        assertEquals(100.0 / 6, stats.subject("REDES").percent, 1e-9)
        assertEquals(100.0, stats.subject("BD").percent, 1e-9)
        assertEquals(200.0 / 7, stats.global.percent, 1e-9)
    }

    @Test
    fun `unjustified hours are tracked separately`() {
        val stats = calc()
        assertEquals(1.0, stats.global.unjustifiedHours, 1e-9)
        assertEquals(100.0 / 7, stats.global.unjustifiedPercent, 1e-9)
        assertEquals(0.0, stats.subject("BD").unjustifiedHours, 1e-9)
    }

    @Test
    fun `days and absences after the cutoff are ignored`() {
        val stats = calc(cutoff = LocalDate.of(2026, 9, 21), today = LocalDate.of(2027, 1, 1))
        assertEquals(4.0, stats.subject("REDES").courseHours, 1e-9)
        assertEquals(1.0, stats.subject("REDES").absentHours, 1e-9)
        assertEquals(LocalDate.of(2026, 9, 21), stats.countedUntil)
        assertEquals(LocalDate.of(2025, 9, 1), stats.courseStart)
    }

    @Test
    fun `subject that only appears in absences has zero course hours and zero percent`() {
        val stats = calc(absences = absences + Absence(LocalDate.of(2026, 9, 14), "TUTORIA", 1.0, false))
        val tutoria = stats.subject("TUTORIA")
        assertEquals(0.0, tutoria.courseHours, 1e-9)
        assertEquals(1.0, tutoria.absentHours, 1e-9)
        assertEquals(0.0, tutoria.percent, 1e-9)
    }

    @Test
    fun `subjects are sorted by percent descending`() {
        assertEquals(listOf("BD", "REDES"), calc().subjects.map { it.label })
    }

    @Test
    fun `no data gives zero everywhere`() {
        val stats = AbsenceCalculator.calculate(emptyList(), emptyList(), emptyList(), cutoff, today)
        assertEquals(0.0, stats.global.courseHours, 1e-9)
        assertEquals(0.0, stats.global.percent, 1e-9)
        assertEquals(emptyList<HoursStats>(), stats.subjects)
    }

    @Test
    fun `margin is the hours left before reaching the threshold, never negative`() {
        assertEquals(5.0, HoursStats("X", 100.0, 10.0, 0.0).marginHours(15.0), 1e-9)
        assertEquals(0.0, HoursStats("X", 100.0, 20.0, 0.0).marginHours(15.0), 1e-9)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest`
Expected: compilation FAILS with unresolved references `CourseDates`, `AbsenceCalculator`, `ScheduleSlot`…

- [ ] **Step 3: Implement**

`app/src/main/java/com/faltasipasen/app/domain/LocalDateSerializer.kt`:
```kotlin
package com.faltasipasen.app.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
```

`app/src/main/java/com/faltasipasen/app/domain/Models.kt`:
```kotlin
package com.faltasipasen.app.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Una sesión semanal del horario. dayOfWeek: 1 = lunes … 7 = domingo. */
@Serializable
data class ScheduleSlot(val subject: String, val dayOfWeek: Int, val hours: Double)

@Serializable
data class CalendarDay(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val lectivo: Boolean,
)

@Serializable
data class Absence(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val subject: String,
    val hours: Double,
    val justified: Boolean,
)

data class Credentials(val username: String, val password: String)

data class HoursStats(
    val label: String,
    val courseHours: Double,
    val absentHours: Double,
    val unjustifiedHours: Double,
) {
    val percent: Double get() = percentOf(absentHours)
    val unjustifiedPercent: Double get() = percentOf(unjustifiedHours)

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
)
```

`app/src/main/java/com/faltasipasen/app/domain/CourseDates.kt`:
```kotlin
package com.faltasipasen.app.domain

import java.time.LocalDate

object CourseDates {
    private const val COURSE_START_MONTH = 9

    /** 15 de febrero del curso en vigor (el curso empieza en septiembre). */
    fun defaultCutoff(today: LocalDate): LocalDate {
        val year = if (today.monthValue >= COURSE_START_MONTH) today.year + 1 else today.year
        return LocalDate.of(year, 2, 15)
    }

    fun courseStartFor(cutoff: LocalDate): LocalDate = LocalDate.of(cutoff.year - 1, COURSE_START_MONTH, 1)
}
```

`app/src/main/java/com/faltasipasen/app/domain/AbsenceCalculator.kt`:
```kotlin
package com.faltasipasen.app.domain

import java.time.LocalDate

object AbsenceCalculator {
    const val GLOBAL_LABEL = "Total"

    fun calculate(
        schedule: List<ScheduleSlot>,
        calendar: List<CalendarDay>,
        absences: List<Absence>,
        cutoff: LocalDate,
        today: LocalDate,
    ): AbsenceStats {
        val courseStart = CourseDates.courseStartFor(cutoff)
        val countedUntil = minOf(today, cutoff)

        val courseDays = calendar
            .filter { it.lectivo && !it.date.isBefore(courseStart) && !it.date.isAfter(cutoff) }
            .map { it.date }
            .distinct()
        val counted = absences.filter { !it.date.isBefore(courseStart) && !it.date.isAfter(countedUntil) }

        val labels = (schedule.map { it.subject } + counted.map { it.subject }).distinct()
        val subjects = labels.map { subject ->
            val slots = schedule.filter { it.subject == subject }
            val courseHours = courseDays.sumOf { day ->
                slots.filter { it.dayOfWeek == day.dayOfWeek.value }.sumOf { it.hours }
            }
            val own = counted.filter { it.subject == subject }
            HoursStats(
                label = subject,
                courseHours = courseHours,
                absentHours = own.sumOf { it.hours },
                unjustifiedHours = own.filterNot { it.justified }.sumOf { it.hours },
            )
        }.sortedWith(compareByDescending<HoursStats> { it.percent }.thenBy { it.label })

        val global = HoursStats(
            label = GLOBAL_LABEL,
            courseHours = subjects.sumOf { it.courseHours },
            absentHours = subjects.sumOf { it.absentHours },
            unjustifiedHours = subjects.sumOf { it.unjustifiedHours },
        )
        return AbsenceStats(global, subjects, courseStart, cutoff, countedUntil)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.domain.*"`
Expected: PASS (13 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/faltasipasen/app/domain app/src/test/java/com/faltasipasen/app/domain
git commit -m "feat: compute absence percentage over total course hours" -m "$TRAILER"
```

---

### Task 3: Threshold evaluation and alert text

**Files:**
- Create: `app/src/main/java/com/faltasipasen/app/domain/ThresholdEvaluator.kt`, `domain/AlertFormatter.kt`
- Test: `app/src/test/java/com/faltasipasen/app/domain/ThresholdEvaluatorTest.kt`, `domain/AlertFormatterTest.kt`

**Interfaces:**
- Consumes: `AbsenceStats`, `HoursStats` (Task 2).
- Produces:
  - `data class ThresholdAlert(val key: String, val label: String, val percent: Double)`
  - `data class ThresholdEvaluation(val newAlerts: List<ThresholdAlert>, val activeKeys: Set<String>)`
  - `object ThresholdEvaluator { const val GLOBAL_KEY = "__total__"; fun evaluate(stats: AbsenceStats, thresholdPercent: Double, previouslyActive: Set<String>): ThresholdEvaluation }`
  - `object AlertFormatter { fun percent(value: Double): String; fun thresholdTitle(thresholdPercent: Double): String; fun thresholdBody(alerts: List<ThresholdAlert>): String }`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/faltasipasen/app/domain/ThresholdEvaluatorTest.kt`:
```kotlin
package com.faltasipasen.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ThresholdEvaluatorTest {
    private fun stats(globalAbsent: Double, vararg subjects: HoursStats) = AbsenceStats(
        global = HoursStats("Total", 100.0, globalAbsent, 0.0),
        subjects = subjects.toList(),
        courseStart = LocalDate.of(2026, 9, 1),
        cutoff = LocalDate.of(2027, 2, 15),
        countedUntil = LocalDate.of(2026, 10, 1),
    )

    @Test
    fun `crossing the threshold for the first time creates an alert`() {
        val result = ThresholdEvaluator.evaluate(stats(16.0), 15.0, emptySet())
        assertEquals(listOf(ThresholdAlert(ThresholdEvaluator.GLOBAL_KEY, "Total", 16.0)), result.newAlerts)
        assertEquals(setOf(ThresholdEvaluator.GLOBAL_KEY), result.activeKeys)
    }

    @Test
    fun `reaching exactly the threshold counts`() {
        assertEquals(1, ThresholdEvaluator.evaluate(stats(15.0), 15.0, emptySet()).newAlerts.size)
    }

    @Test
    fun `an already notified crossing is not repeated`() {
        val result = ThresholdEvaluator.evaluate(stats(18.0), 15.0, setOf(ThresholdEvaluator.GLOBAL_KEY))
        assertEquals(emptyList<ThresholdAlert>(), result.newAlerts)
        assertEquals(setOf(ThresholdEvaluator.GLOBAL_KEY), result.activeKeys)
    }

    @Test
    fun `dropping below clears the key so a later crossing alerts again`() {
        val below = ThresholdEvaluator.evaluate(stats(10.0), 15.0, setOf(ThresholdEvaluator.GLOBAL_KEY))
        assertEquals(emptySet<String>(), below.activeKeys)
        val again = ThresholdEvaluator.evaluate(stats(16.0), 15.0, below.activeKeys)
        assertEquals(1, again.newAlerts.size)
    }

    @Test
    fun `subjects over the threshold alert with their own key`() {
        val redes = HoursStats("REDES", 50.0, 10.0, 0.0) // 20 %
        val bd = HoursStats("BD", 50.0, 1.0, 0.0)        // 2 %
        val result = ThresholdEvaluator.evaluate(stats(11.0, redes, bd), 15.0, emptySet())
        assertEquals(listOf(ThresholdAlert("REDES", "REDES", 20.0)), result.newAlerts)
    }

    @Test
    fun `subjects without course hours never alert`() {
        val ghost = HoursStats("TUTORIA", 0.0, 3.0, 3.0)
        assertEquals(emptyList<ThresholdAlert>(), ThresholdEvaluator.evaluate(stats(0.0, ghost), 15.0, emptySet()).newAlerts)
    }
}
```

`app/src/test/java/com/faltasipasen/app/domain/AlertFormatterTest.kt`:
```kotlin
package com.faltasipasen.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertFormatterTest {
    @Test
    fun `percent uses Spanish decimal comma and one decimal`() {
        assertEquals("16,2 %", AlertFormatter.percent(16.2))
        assertEquals("15,0 %", AlertFormatter.percent(15.0))
    }

    @Test
    fun `title mentions the threshold`() {
        assertEquals("Has superado el 15,0 % de faltas", AlertFormatter.thresholdTitle(15.0))
    }

    @Test
    fun `body lists every alert`() {
        val body = AlertFormatter.thresholdBody(
            listOf(ThresholdAlert("__total__", "Total", 16.2), ThresholdAlert("REDES", "REDES", 21.0)),
        )
        assertEquals("Total: 16,2 % · REDES: 21,0 %", body)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.domain.*"`
Expected: compilation FAILS with unresolved `ThresholdEvaluator`, `AlertFormatter`.

- [ ] **Step 3: Implement**

`app/src/main/java/com/faltasipasen/app/domain/ThresholdEvaluator.kt`:
```kotlin
package com.faltasipasen.app.domain

data class ThresholdAlert(val key: String, val label: String, val percent: Double)

data class ThresholdEvaluation(val newAlerts: List<ThresholdAlert>, val activeKeys: Set<String>)

object ThresholdEvaluator {
    const val GLOBAL_KEY = "__total__"

    fun evaluate(stats: AbsenceStats, thresholdPercent: Double, previouslyActive: Set<String>): ThresholdEvaluation {
        val over = buildList {
            if (stats.global.courseHours > 0.0 && stats.global.percent >= thresholdPercent) {
                add(ThresholdAlert(GLOBAL_KEY, stats.global.label, stats.global.percent))
            }
            stats.subjects
                .filter { it.courseHours > 0.0 && it.percent >= thresholdPercent }
                .forEach { add(ThresholdAlert(it.label, it.label, it.percent)) }
        }
        return ThresholdEvaluation(
            newAlerts = over.filter { it.key !in previouslyActive },
            activeKeys = over.map { it.key }.toSet(),
        )
    }
}
```

`app/src/main/java/com/faltasipasen/app/domain/AlertFormatter.kt`:
```kotlin
package com.faltasipasen.app.domain

import java.util.Locale

object AlertFormatter {
    private val spanish: Locale = Locale.forLanguageTag("es-ES")

    fun percent(value: Double): String = String.format(spanish, "%.1f %%", value)

    fun thresholdTitle(thresholdPercent: Double): String = "Has superado el ${percent(thresholdPercent)} de faltas"

    fun thresholdBody(alerts: List<ThresholdAlert>): String =
        alerts.joinToString(" · ") { "${it.label}: ${percent(it.percent)}" }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.domain.*"`
Expected: PASS (22 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/faltasipasen/app/domain app/src/test/java/com/faltasipasen/app/domain
git commit -m "feat: detect new threshold crossings and format alert text" -m "$TRAILER"
```

---

### Task 4: Scraper contract and JSON parsers

The injected JavaScript (Tasks 8 and 11) reports every step as a JSON string with this envelope:
- success: `{"ok": true, "data": <payload>}`
- failure: `{"ok": false, "error": "<texto>"}`; a rejected login adds `"loginFailed": true`.

Payloads:
- `schedule`: `[{"subject": "Redes Locales", "day": 1, "hours": 1.0}]` — one entry per weekly session; `day` 1 = lunes … 7 = domingo; `hours` optional (default 1.0).
- `calendar`: `[{"date": "2026-09-15", "lectivo": true}]` — every day from 1 Sept to the cutoff at least.
- `absences`: `[{"date": "2026-10-02", "subject": "Redes Locales", "hours": 1.0, "justified": false}]` — full-session absences only (no "retrasos"); `hours` default 1.0, `justified` default false.

Subject names are normalized (trim, collapse spaces, upper case) so the timetable and the absences match.

**Files:**
- Create: `app/src/main/java/com/faltasipasen/app/scraper/ScraperContract.kt`, `scraper/ScrapeParsers.kt`
- Test: `app/src/test/java/com/faltasipasen/app/scraper/ScrapeParsersTest.kt`

**Interfaces:**
- Consumes: `ScheduleSlot`, `CalendarDay`, `Absence`, `Credentials` (Task 2).
- Produces:
  - `data class ScrapeResult(val schedule: List<ScheduleSlot>, val calendar: List<CalendarDay>, val absences: List<Absence>)`
  - `interface IpasenScraper { suspend fun scrape(credentials: Credentials): ScrapeResult }`
  - `class ScrapeException(val reason: Reason, message: String) : Exception(message)` with `enum class Reason { LOGIN_FAILED, NETWORK, TIMEOUT, PARSE_ERROR }`
  - `object ScrapeParsers { fun checkOk(raw: String, step: String); fun parseSchedule(raw: String): List<ScheduleSlot>; fun parseCalendar(raw: String): List<CalendarDay>; fun parseAbsences(raw: String): List<Absence>; fun normalizeSubject(name: String): String }`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/faltasipasen/app/scraper/ScrapeParsersTest.kt`:
```kotlin
package com.faltasipasen.app.scraper

import com.faltasipasen.app.domain.Absence
import com.faltasipasen.app.domain.CalendarDay
import com.faltasipasen.app.domain.ScheduleSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScrapeParsersTest {
    private fun reasonOf(block: () -> Unit): ScrapeException.Reason =
        assertThrows(ScrapeException::class.java) { block() }.reason

    @Test
    fun `parses schedule and normalizes subject names`() {
        val raw = """{"ok":true,"data":[{"subject":"  Redes   locales ","day":1,"hours":1.5},{"subject":"BD","day":3}]}"""
        assertEquals(
            listOf(ScheduleSlot("REDES LOCALES", 1, 1.5), ScheduleSlot("BD", 3, 1.0)),
            ScrapeParsers.parseSchedule(raw),
        )
    }

    @Test
    fun `rejects a day of week out of range`() {
        val raw = """{"ok":true,"data":[{"subject":"BD","day":8}]}"""
        assertEquals(ScrapeException.Reason.PARSE_ERROR, reasonOf { ScrapeParsers.parseSchedule(raw) })
    }

    @Test
    fun `rejects non positive durations`() {
        val raw = """{"ok":true,"data":[{"subject":"BD","day":2,"hours":0}]}"""
        assertEquals(ScrapeException.Reason.PARSE_ERROR, reasonOf { ScrapeParsers.parseSchedule(raw) })
    }

    @Test
    fun `parses calendar days`() {
        val raw = """{"ok":true,"data":[{"date":"2026-09-15","lectivo":true},{"date":"2026-12-24","lectivo":false}]}"""
        assertEquals(
            listOf(CalendarDay(LocalDate.of(2026, 9, 15), true), CalendarDay(LocalDate.of(2026, 12, 24), false)),
            ScrapeParsers.parseCalendar(raw),
        )
    }

    @Test
    fun `rejects invalid dates`() {
        val raw = """{"ok":true,"data":[{"date":"15/09/2026","lectivo":true}]}"""
        assertEquals(ScrapeException.Reason.PARSE_ERROR, reasonOf { ScrapeParsers.parseCalendar(raw) })
    }

    @Test
    fun `parses absences with defaults`() {
        val raw = """{"ok":true,"data":[{"date":"2026-10-02","subject":"bd"},{"date":"2026-10-03","subject":"Redes","hours":2,"justified":true}]}"""
        assertEquals(
            listOf(
                Absence(LocalDate.of(2026, 10, 2), "BD", 1.0, false),
                Absence(LocalDate.of(2026, 10, 3), "REDES", 2.0, true),
            ),
            ScrapeParsers.parseAbsences(raw),
        )
    }

    @Test
    fun `script failure is a parse error with the script message`() {
        val raw = """{"ok":false,"error":"tabla no encontrada"}"""
        val e = assertThrows(ScrapeException::class.java) { ScrapeParsers.parseAbsences(raw) }
        assertEquals(ScrapeException.Reason.PARSE_ERROR, e.reason)
        assertTrue(e.message!!.contains("tabla no encontrada"))
    }

    @Test
    fun `rejected login is reported as login failure`() {
        val raw = """{"ok":false,"error":"Usuario o clave incorrectos","loginFailed":true}"""
        assertEquals(ScrapeException.Reason.LOGIN_FAILED, reasonOf { ScrapeParsers.checkOk(raw, "login") })
    }

    @Test
    fun `successful step passes the ok check`() {
        ScrapeParsers.checkOk("""{"ok":true,"data":"ok"}""", "login")
    }

    @Test
    fun `non JSON output is a parse error`() {
        assertEquals(ScrapeException.Reason.PARSE_ERROR, reasonOf { ScrapeParsers.parseSchedule("undefined") })
    }

    @Test
    fun `wrong payload shape is a parse error`() {
        assertEquals(ScrapeException.Reason.PARSE_ERROR, reasonOf { ScrapeParsers.parseSchedule("""{"ok":true,"data":{"a":1}}""") })
    }

    @Test
    fun `empty subject is rejected`() {
        val raw = """{"ok":true,"data":[{"subject":"   ","day":1}]}"""
        assertEquals(ScrapeException.Reason.PARSE_ERROR, reasonOf { ScrapeParsers.parseSchedule(raw) })
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.scraper.*"`
Expected: compilation FAILS with unresolved `ScrapeParsers`, `ScrapeException`.

- [ ] **Step 3: Implement**

`app/src/main/java/com/faltasipasen/app/scraper/ScraperContract.kt`:
```kotlin
package com.faltasipasen.app.scraper

import com.faltasipasen.app.domain.Absence
import com.faltasipasen.app.domain.CalendarDay
import com.faltasipasen.app.domain.Credentials
import com.faltasipasen.app.domain.ScheduleSlot

data class ScrapeResult(
    val schedule: List<ScheduleSlot>,
    val calendar: List<CalendarDay>,
    val absences: List<Absence>,
)

interface IpasenScraper {
    suspend fun scrape(credentials: Credentials): ScrapeResult
}

class ScrapeException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { LOGIN_FAILED, NETWORK, TIMEOUT, PARSE_ERROR }
}
```

`app/src/main/java/com/faltasipasen/app/scraper/ScrapeParsers.kt`:
```kotlin
package com.faltasipasen.app.scraper

import com.faltasipasen.app.domain.Absence
import com.faltasipasen.app.domain.CalendarDay
import com.faltasipasen.app.domain.ScheduleSlot
import com.faltasipasen.app.scraper.ScrapeException.Reason
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

@Serializable
private data class SlotDto(val subject: String, val day: Int, val hours: Double = 1.0)

@Serializable
private data class DayDto(val date: String, val lectivo: Boolean)

@Serializable
private data class AbsenceDto(
    val date: String,
    val subject: String,
    val hours: Double = 1.0,
    val justified: Boolean = false,
)

object ScrapeParsers {
    private val json = Json { ignoreUnknownKeys = true }
    private val whitespace = Regex("\\s+")

    fun checkOk(raw: String, step: String) {
        payload(raw, step)
    }

    fun parseSchedule(raw: String): List<ScheduleSlot> =
        decode<List<SlotDto>>(raw, STEP_SCHEDULE).map { dto ->
            if (dto.day !in 1..7) invalid(STEP_SCHEDULE, "día de la semana fuera de rango: ${dto.day}")
            ScheduleSlot(subject(dto.subject, STEP_SCHEDULE), dto.day, hours(dto.hours, STEP_SCHEDULE))
        }

    fun parseCalendar(raw: String): List<CalendarDay> =
        decode<List<DayDto>>(raw, STEP_CALENDAR).map { CalendarDay(date(it.date, STEP_CALENDAR), it.lectivo) }

    fun parseAbsences(raw: String): List<Absence> =
        decode<List<AbsenceDto>>(raw, STEP_ABSENCES).map {
            Absence(
                date = date(it.date, STEP_ABSENCES),
                subject = subject(it.subject, STEP_ABSENCES),
                hours = hours(it.hours, STEP_ABSENCES),
                justified = it.justified,
            )
        }

    fun normalizeSubject(name: String): String = name.trim().replace(whitespace, " ").uppercase(Locale.ROOT)

    private inline fun <reified T> decode(raw: String, step: String): T {
        val data = payload(raw, step)
        return try {
            json.decodeFromJsonElement<T>(data)
        } catch (e: IllegalArgumentException) {
            invalid(step, "formato inesperado")
        }
    }

    private fun payload(raw: String, step: String): JsonElement {
        val obj = try {
            json.parseToJsonElement(raw) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: invalid(step, "la respuesta no es un objeto JSON")

        val ok = (obj["ok"] as? JsonPrimitive)?.booleanOrNull ?: false
        if (!ok) {
            val error = (obj["error"] as? JsonPrimitive)?.contentOrNull ?: "error desconocido"
            val loginFailed = (obj["loginFailed"] as? JsonPrimitive)?.booleanOrNull == true
            throw ScrapeException(if (loginFailed) Reason.LOGIN_FAILED else Reason.PARSE_ERROR, "$step: $error")
        }
        return obj["data"] ?: JsonNull
    }

    private fun subject(value: String, step: String): String =
        normalizeSubject(value).ifEmpty { invalid(step, "asignatura vacía") }

    private fun hours(value: Double, step: String): Double =
        if (value > 0.0 && value <= 12.0) value else invalid(step, "duración no válida: $value")

    private fun date(value: String, step: String): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            invalid(step, "fecha no válida: $value")
        }

    private fun invalid(step: String, detail: String): Nothing = throw ScrapeException(Reason.PARSE_ERROR, "$step: $detail")

    private const val STEP_SCHEDULE = "horario"
    private const val STEP_CALENDAR = "calendario"
    private const val STEP_ABSENCES = "faltas"
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.scraper.*"`
Expected: PASS (12 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/faltasipasen/app/scraper app/src/test/java/com/faltasipasen/app/scraper
git commit -m "feat: define scraper contract and parse its JSON output" -m "$TRAILER"
```

---

### Task 5: Snapshot store (JSON file)

**Files:**
- Create: `app/src/main/java/com/faltasipasen/app/data/Snapshot.kt`
- Test: `app/src/test/java/com/faltasipasen/app/data/FileSnapshotStoreTest.kt`

**Interfaces:**
- Consumes: `ScheduleSlot`, `CalendarDay`, `Absence` (Task 2).
- Produces:
  - `@Serializable data class Snapshot(val schedule: List<ScheduleSlot>, val calendar: List<CalendarDay>, val absences: List<Absence>, val syncedAtMillis: Long)`
  - `interface SnapshotStore { val current: StateFlow<Snapshot?>; fun save(snapshot: Snapshot); fun clear() }`
  - `class FileSnapshotStore(file: File) : SnapshotStore`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/faltasipasen/app/data/FileSnapshotStoreTest.kt`:
```kotlin
package com.faltasipasen.app.data

import com.faltasipasen.app.domain.Absence
import com.faltasipasen.app.domain.CalendarDay
import com.faltasipasen.app.domain.ScheduleSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class FileSnapshotStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val snapshot = Snapshot(
        schedule = listOf(ScheduleSlot("REDES", 1, 1.0)),
        calendar = listOf(CalendarDay(LocalDate.of(2026, 9, 14), true)),
        absences = listOf(Absence(LocalDate.of(2026, 9, 14), "REDES", 1.0, false)),
        syncedAtMillis = 1_790_000_000_000,
    )

    private fun file() = File(tmp.root, "snapshot.json")

    @Test
    fun `empty store has no snapshot`() {
        assertNull(FileSnapshotStore(file()).current.value)
    }

    @Test
    fun `saved snapshot is visible immediately and after reopening`() {
        val store = FileSnapshotStore(file())
        store.save(snapshot)
        assertEquals(snapshot, store.current.value)
        assertEquals(snapshot, FileSnapshotStore(file()).current.value)
    }

    @Test
    fun `saving twice replaces the previous snapshot`() {
        val store = FileSnapshotStore(file())
        store.save(snapshot)
        val newer = snapshot.copy(absences = emptyList(), syncedAtMillis = 1_790_000_100_000)
        store.save(newer)
        assertEquals(newer, FileSnapshotStore(file()).current.value)
    }

    @Test
    fun `corrupted file is treated as no snapshot`() {
        file().writeText("{not json")
        assertNull(FileSnapshotStore(file()).current.value)
    }

    @Test
    fun `clear deletes the file and the current value`() {
        val store = FileSnapshotStore(file())
        store.save(snapshot)
        store.clear()
        assertNull(store.current.value)
        assertFalse(file().exists())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.data.*"`
Expected: compilation FAILS with unresolved `Snapshot`, `FileSnapshotStore`.

- [ ] **Step 3: Implement**

`app/src/main/java/com/faltasipasen/app/data/Snapshot.kt`:
```kotlin
package com.faltasipasen.app.data

import com.faltasipasen.app.domain.Absence
import com.faltasipasen.app.domain.CalendarDay
import com.faltasipasen.app.domain.ScheduleSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Snapshot(
    val schedule: List<ScheduleSlot>,
    val calendar: List<CalendarDay>,
    val absences: List<Absence>,
    val syncedAtMillis: Long,
)

interface SnapshotStore {
    val current: StateFlow<Snapshot?>
    fun save(snapshot: Snapshot)
    fun clear()
}

class FileSnapshotStore(private val file: File) : SnapshotStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(readFromDisk())
    override val current: StateFlow<Snapshot?> = state.asStateFlow()

    @Synchronized
    override fun save(snapshot: Snapshot) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(Snapshot.serializer(), snapshot))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "No se pudo guardar ${file.name}" }
        }
        state.value = snapshot
    }

    @Synchronized
    override fun clear() {
        file.delete()
        state.value = null
    }

    private fun readFromDisk(): Snapshot? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(Snapshot.serializer(), file.readText()) }.getOrNull()
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.data.*"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/faltasipasen/app/data app/src/test/java/com/faltasipasen/app/data
git commit -m "feat: persist the latest iPasen data as a JSON snapshot" -m "$TRAILER"
```

---

### Task 6: Settings store with encrypted credentials

**Files:**
- Create: `app/src/main/java/com/faltasipasen/app/data/SettingsStore.kt`, `data/KeystoreCipher.kt`
- Create (test helpers): `app/src/test/java/com/faltasipasen/app/testutil/InMemorySharedPreferences.kt`, `testutil/ReversingCipher.kt`
- Test: `app/src/test/java/com/faltasipasen/app/data/PrefsSettingsStoreTest.kt`

**Interfaces:**
- Consumes: `Credentials`, `CourseDates` (Task 2).
- Produces:
  - `interface StringCipher { fun encrypt(plain: String): String; fun decrypt(encoded: String): String? }`
  - `class KeystoreCipher(alias: String = "ipasen_credenciales") : StringCipher`
  - `interface SettingsStore` with: `val changes: StateFlow<Long>`, `fun credentials(): Credentials?`, `fun saveCredentials(credentials: Credentials)`, `fun clearAll()`, `fun thresholdPercent(): Double`, `fun setThresholdPercent(value: Double)`, `fun cutoffDate(): LocalDate`, `fun setCutoffDate(value: LocalDate)`, `fun syncIntervalHours(): Int`, `fun setSyncIntervalHours(value: Int)`, `fun activeAlertKeys(): Set<String>`, `fun setActiveAlertKeys(keys: Set<String>)`, `fun lastError(): String?`, `fun setLastError(message: String?)`
  - `class PrefsSettingsStore(prefs: SharedPreferences, cipher: StringCipher, today: () -> LocalDate) : SettingsStore` with `companion object { const val DEFAULT_THRESHOLD = 15.0; const val DEFAULT_INTERVAL_HOURS = 12 }`
  - Test helpers `InMemorySharedPreferences` (public `values: MutableMap<String, Any?>`) and `ReversingCipher` in package `com.faltasipasen.app.testutil`.

- [ ] **Step 1: Write the test helpers and the failing test**

`app/src/test/java/com/faltasipasen/app/testutil/InMemorySharedPreferences.kt`:
```kotlin
package com.faltasipasen.app.testutil

import android.content.SharedPreferences

@Suppress("UNCHECKED_CAST")
class InMemorySharedPreferences : SharedPreferences {
    val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as String? ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as Set<String>?)?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as Int? ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as Long? ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as Float? ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clearAll = false

        private fun put(key: String?, value: Any?): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }

        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values?.toSet())
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)

        override fun remove(key: String?): SharedPreferences.Editor {
            removed += key!!
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            if (clearAll) values.clear()
            removed.forEach { values.remove(it) }
            pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
```

`app/src/test/java/com/faltasipasen/app/testutil/ReversingCipher.kt`:
```kotlin
package com.faltasipasen.app.testutil

import com.faltasipasen.app.data.StringCipher

/** Cifrado falso y reversible para tests: nunca guarda el texto tal cual. */
class ReversingCipher : StringCipher {
    override fun encrypt(plain: String): String = "enc:" + plain.reversed()
    override fun decrypt(encoded: String): String? =
        if (encoded.startsWith("enc:")) encoded.removePrefix("enc:").reversed() else null
}
```

`app/src/test/java/com/faltasipasen/app/data/PrefsSettingsStoreTest.kt`:
```kotlin
package com.faltasipasen.app.data

import com.faltasipasen.app.domain.Credentials
import com.faltasipasen.app.testutil.InMemorySharedPreferences
import com.faltasipasen.app.testutil.ReversingCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrefsSettingsStoreTest {
    private val prefs = InMemorySharedPreferences()
    private val store = PrefsSettingsStore(prefs, ReversingCipher()) { LocalDate.of(2026, 9, 28) }

    @Test
    fun `no credentials by default`() {
        assertNull(store.credentials())
    }

    @Test
    fun `credentials round trip and are never stored in plain text`() {
        store.saveCredentials(Credentials("alumno", "s3cret"))
        assertEquals(Credentials("alumno", "s3cret"), store.credentials())
        assertFalse(prefs.values.values.any { it == "alumno" || it == "s3cret" })
    }

    @Test
    fun `undecryptable credentials count as missing`() {
        store.saveCredentials(Credentials("alumno", "s3cret"))
        prefs.values.keys.filter { (prefs.values[it] as? String)?.startsWith("enc:") == true }
            .forEach { prefs.values[it] = "basura" }
        assertNull(store.credentials())
    }

    @Test
    fun `defaults need no user input`() {
        assertEquals(15.0, store.thresholdPercent(), 0.0)
        assertEquals(LocalDate.of(2027, 2, 15), store.cutoffDate())
        assertEquals(12, store.syncIntervalHours())
        assertEquals(emptySet<String>(), store.activeAlertKeys())
        assertNull(store.lastError())
    }

    @Test
    fun `values are persisted`() {
        store.setThresholdPercent(20.5)
        store.setCutoffDate(LocalDate.of(2027, 2, 12))
        store.setSyncIntervalHours(6)
        store.setActiveAlertKeys(setOf("__total__"))
        store.setLastError("fallo")
        val reopened = PrefsSettingsStore(prefs, ReversingCipher()) { LocalDate.of(2026, 9, 28) }
        assertEquals(20.5, reopened.thresholdPercent(), 0.0)
        assertEquals(LocalDate.of(2027, 2, 12), reopened.cutoffDate())
        assertEquals(6, reopened.syncIntervalHours())
        assertEquals(setOf("__total__"), reopened.activeAlertKeys())
        assertEquals("fallo", reopened.lastError())
    }

    @Test
    fun `saving new credentials resets alert state and last error`() {
        store.setActiveAlertKeys(setOf("__total__"))
        store.setLastError("clave incorrecta")
        store.saveCredentials(Credentials("alumno", "nueva"))
        assertEquals(emptySet<String>(), store.activeAlertKeys())
        assertNull(store.lastError())
    }

    @Test
    fun `setting last error to null removes it`() {
        store.setLastError("fallo")
        store.setLastError(null)
        assertNull(store.lastError())
    }

    @Test
    fun `every write bumps the change counter`() {
        val before = store.changes.value
        store.setThresholdPercent(10.0)
        store.setLastError(null)
        assertEquals(before + 2, store.changes.value)
    }

    @Test
    fun `clearAll wipes everything`() {
        store.saveCredentials(Credentials("alumno", "s3cret"))
        store.setThresholdPercent(30.0)
        store.clearAll()
        assertNull(store.credentials())
        assertEquals(15.0, store.thresholdPercent(), 0.0)
        assertTrue(prefs.values.isEmpty())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.data.*"`
Expected: compilation FAILS with unresolved `StringCipher`, `PrefsSettingsStore`.

- [ ] **Step 3: Implement**

`app/src/main/java/com/faltasipasen/app/data/SettingsStore.kt`:
```kotlin
package com.faltasipasen.app.data

import android.content.SharedPreferences
import com.faltasipasen.app.domain.CourseDates
import com.faltasipasen.app.domain.Credentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

interface SettingsStore {
    /** Se incrementa en cada escritura; la UI lo observa para refrescarse. */
    val changes: StateFlow<Long>
    fun credentials(): Credentials?
    fun saveCredentials(credentials: Credentials)
    fun clearAll()
    fun thresholdPercent(): Double
    fun setThresholdPercent(value: Double)
    fun cutoffDate(): LocalDate
    fun setCutoffDate(value: LocalDate)
    fun syncIntervalHours(): Int
    fun setSyncIntervalHours(value: Int)
    fun activeAlertKeys(): Set<String>
    fun setActiveAlertKeys(keys: Set<String>)
    fun lastError(): String?
    fun setLastError(message: String?)
}

class PrefsSettingsStore(
    private val prefs: SharedPreferences,
    private val cipher: StringCipher,
    private val today: () -> LocalDate,
) : SettingsStore {
    private val version = MutableStateFlow(0L)
    override val changes: StateFlow<Long> = version.asStateFlow()

    override fun credentials(): Credentials? {
        val user = prefs.getString(KEY_USER, null)?.let(cipher::decrypt) ?: return null
        val pass = prefs.getString(KEY_PASS, null)?.let(cipher::decrypt) ?: return null
        return Credentials(user, pass)
    }

    override fun saveCredentials(credentials: Credentials) = write {
        putString(KEY_USER, cipher.encrypt(credentials.username))
        putString(KEY_PASS, cipher.encrypt(credentials.password))
        remove(KEY_ACTIVE_ALERTS)
        remove(KEY_LAST_ERROR)
    }

    override fun clearAll() = write { clear() }

    override fun thresholdPercent(): Double =
        prefs.getString(KEY_THRESHOLD, null)?.toDoubleOrNull() ?: DEFAULT_THRESHOLD

    override fun setThresholdPercent(value: Double) = write { putString(KEY_THRESHOLD, value.toString()) }

    override fun cutoffDate(): LocalDate =
        prefs.getString(KEY_CUTOFF, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: CourseDates.defaultCutoff(today())

    override fun setCutoffDate(value: LocalDate) = write { putString(KEY_CUTOFF, value.toString()) }

    override fun syncIntervalHours(): Int = prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL_HOURS)

    override fun setSyncIntervalHours(value: Int) = write { putInt(KEY_INTERVAL, value) }

    override fun activeAlertKeys(): Set<String> = prefs.getStringSet(KEY_ACTIVE_ALERTS, null)?.toSet() ?: emptySet()

    override fun setActiveAlertKeys(keys: Set<String>) = write { putStringSet(KEY_ACTIVE_ALERTS, keys.toSet()) }

    override fun lastError(): String? = prefs.getString(KEY_LAST_ERROR, null)

    override fun setLastError(message: String?) = write {
        if (message == null) remove(KEY_LAST_ERROR) else putString(KEY_LAST_ERROR, message)
    }

    private fun write(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        version.update { it + 1 }
    }

    companion object {
        const val DEFAULT_THRESHOLD = 15.0
        const val DEFAULT_INTERVAL_HOURS = 12
        private const val KEY_USER = "usuario_cifrado"
        private const val KEY_PASS = "clave_cifrada"
        private const val KEY_THRESHOLD = "umbral"
        private const val KEY_CUTOFF = "fecha_corte"
        private const val KEY_INTERVAL = "intervalo_horas"
        private const val KEY_ACTIVE_ALERTS = "avisos_activos"
        private const val KEY_LAST_ERROR = "ultimo_error"
    }
}
```

`app/src/main/java/com/faltasipasen/app/data/KeystoreCipher.kt`:
```kotlin
package com.faltasipasen.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface StringCipher {
    fun encrypt(plain: String): String
    /** Devuelve null si no se puede descifrar (p. ej. la clave del Keystore se invalidó). */
    fun decrypt(encoded: String): String?
}

class KeystoreCipher(private val alias: String = "ipasen_credenciales") : StringCipher {

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return b64(cipher.iv) + SEPARATOR + b64(encrypted)
    }

    override fun decrypt(encoded: String): String? = runCatching {
        val (iv, data) = encoded.split(SEPARATOR, limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, unb64(iv)))
        String(cipher.doFinal(unb64(data)), Charsets.UTF_8)
    }.getOrNull()

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val SEPARATOR = ":"
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.data.*"`
Expected: PASS (14 tests: 5 snapshot + 9 settings). Then `./gradlew assembleDebug` → `BUILD SUCCESSFUL` (KeystoreCipher is exercised on the device in Task 12).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/faltasipasen/app/data app/src/test/java/com/faltasipasen/app/data app/src/test/java/com/faltasipasen/app/testutil
git commit -m "feat: store settings with Keystore-encrypted iPasen credentials" -m "$TRAILER"
```

---

### Task 7: Sync orchestration

**Files:**
- Create: `app/src/main/java/com/faltasipasen/app/sync/SyncRunner.kt`
- Test: `app/src/test/java/com/faltasipasen/app/sync/SyncRunnerTest.kt`

**Interfaces:**
- Consumes: `IpasenScraper`, `ScrapeResult`, `ScrapeException` (Task 4); `SnapshotStore`, `Snapshot` (Task 5); `SettingsStore`, `PrefsSettingsStore` (Task 6); `AbsenceCalculator` (Task 2); `ThresholdEvaluator`, `ThresholdAlert` (Task 3); test helpers `InMemorySharedPreferences`, `ReversingCipher` (Task 6).
- Produces:
  - `interface AlertSink { fun showThresholdAlerts(thresholdPercent: Double, alerts: List<ThresholdAlert>); fun showSyncProblem(message: String) }`
  - `sealed interface SyncOutcome { data class Success(val stats: AbsenceStats, val newAlerts: List<ThresholdAlert>); data object MissingCredentials; data class Failed(val reason: ScrapeException.Reason, val message: String) }`
  - `class SyncRunner(scraper: IpasenScraper, snapshots: SnapshotStore, settings: SettingsStore, alerts: AlertSink, today: () -> LocalDate, nowMillis: () -> Long) { suspend fun run(): SyncOutcome }` — serialized with a `Mutex` so periodic and manual syncs never overlap.
  - `SyncRunner.userMessage(e: ScrapeException): String` (companion).

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/faltasipasen/app/sync/SyncRunnerTest.kt`:
```kotlin
package com.faltasipasen.app.sync

import com.faltasipasen.app.data.PrefsSettingsStore
import com.faltasipasen.app.data.Snapshot
import com.faltasipasen.app.data.SnapshotStore
import com.faltasipasen.app.domain.Absence
import com.faltasipasen.app.domain.CalendarDay
import com.faltasipasen.app.domain.Credentials
import com.faltasipasen.app.domain.ScheduleSlot
import com.faltasipasen.app.domain.ThresholdAlert
import com.faltasipasen.app.domain.ThresholdEvaluator
import com.faltasipasen.app.scraper.IpasenScraper
import com.faltasipasen.app.scraper.ScrapeException
import com.faltasipasen.app.scraper.ScrapeResult
import com.faltasipasen.app.testutil.InMemorySharedPreferences
import com.faltasipasen.app.testutil.ReversingCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SyncRunnerTest {
    private val today = LocalDate.of(2026, 9, 30)

    // 4 h lectivas de REDES en el curso (2 lunes x 2 h) y 2 h de falta = 50 %.
    private val result = ScrapeResult(
        schedule = listOf(ScheduleSlot("REDES", 1, 2.0)),
        calendar = listOf(CalendarDay(LocalDate.of(2026, 9, 14), true), CalendarDay(LocalDate.of(2026, 9, 21), true)),
        absences = listOf(Absence(LocalDate.of(2026, 9, 14), "REDES", 2.0, false)),
    )

    private class FakeScraper(var outcome: () -> ScrapeResult) : IpasenScraper {
        var calls = 0
        override suspend fun scrape(credentials: Credentials): ScrapeResult {
            calls++
            return outcome()
        }
    }

    private class FakeSnapshots : SnapshotStore {
        val state = MutableStateFlow<Snapshot?>(null)
        override val current: StateFlow<Snapshot?> = state
        override fun save(snapshot: Snapshot) { state.value = snapshot }
        override fun clear() { state.value = null }
    }

    private class RecordingAlerts : AlertSink {
        val thresholdCalls = mutableListOf<List<ThresholdAlert>>()
        val problems = mutableListOf<String>()
        override fun showThresholdAlerts(thresholdPercent: Double, alerts: List<ThresholdAlert>) { thresholdCalls += alerts }
        override fun showSyncProblem(message: String) { problems += message }
    }

    private val settings = PrefsSettingsStore(InMemorySharedPreferences(), ReversingCipher()) { today }
    private val scraper = FakeScraper { result }
    private val snapshots = FakeSnapshots()
    private val alerts = RecordingAlerts()
    private val runner = SyncRunner(scraper, snapshots, settings, alerts, { today }, { 1234L })

    private fun loggedIn() = settings.saveCredentials(Credentials("alumno", "clave"))

    @Test
    fun `without credentials nothing is scraped`() = runTest {
        assertSame(SyncOutcome.MissingCredentials, runner.run())
        assertEquals(0, scraper.calls)
    }

    @Test
    fun `successful sync saves the snapshot and alerts over the threshold`() = runTest {
        loggedIn()
        val outcome = runner.run() as SyncOutcome.Success
        assertEquals(50.0, outcome.stats.global.percent, 1e-9)
        assertEquals(Snapshot(result.schedule, result.calendar, result.absences, 1234L), snapshots.state.value)
        assertEquals(setOf(ThresholdEvaluator.GLOBAL_KEY, "REDES"), outcome.newAlerts.map { it.key }.toSet())
        assertEquals(1, alerts.thresholdCalls.size)
        assertEquals(setOf(ThresholdEvaluator.GLOBAL_KEY, "REDES"), settings.activeAlertKeys())
        assertNull(settings.lastError())
    }

    @Test
    fun `the same crossing is not notified twice`() = runTest {
        loggedIn()
        runner.run()
        val second = runner.run() as SyncOutcome.Success
        assertTrue(second.newAlerts.isEmpty())
        assertEquals(1, alerts.thresholdCalls.size)
    }

    @Test
    fun `below the threshold there is no alert`() = runTest {
        loggedIn()
        settings.setThresholdPercent(60.0)
        runner.run()
        assertTrue(alerts.thresholdCalls.isEmpty())
        assertEquals(emptySet<String>(), settings.activeAlertKeys())
    }

    @Test
    fun `rejected login notifies once and keeps the old snapshot`() = runTest {
        loggedIn()
        runner.run()
        val previous = snapshots.state.value
        scraper.outcome = { throw ScrapeException(ScrapeException.Reason.LOGIN_FAILED, "login: clave incorrecta") }
        val first = runner.run() as SyncOutcome.Failed
        runner.run()
        assertEquals(ScrapeException.Reason.LOGIN_FAILED, first.reason)
        assertEquals(1, alerts.problems.size)
        assertEquals(first.message, settings.lastError())
        assertSame(previous, snapshots.state.value)
    }

    @Test
    fun `network problems are recorded but not notified`() = runTest {
        loggedIn()
        scraper.outcome = { throw ScrapeException(ScrapeException.Reason.NETWORK, "sin red") }
        val outcome = runner.run() as SyncOutcome.Failed
        assertEquals(ScrapeException.Reason.NETWORK, outcome.reason)
        assertTrue(alerts.problems.isEmpty())
        assertEquals(outcome.message, settings.lastError())
    }

    @Test
    fun `an empty timetable is rejected instead of silently giving 0 percent`() = runTest {
        loggedIn()
        scraper.outcome = { result.copy(schedule = emptyList()) }
        val outcome = runner.run() as SyncOutcome.Failed
        assertEquals(ScrapeException.Reason.PARSE_ERROR, outcome.reason)
        assertNull(snapshots.state.value)
    }

    @Test
    fun `a calendar without lectivo days is rejected`() = runTest {
        loggedIn()
        scraper.outcome = { result.copy(calendar = result.calendar.map { it.copy(lectivo = false) }) }
        assertEquals(ScrapeException.Reason.PARSE_ERROR, (runner.run() as SyncOutcome.Failed).reason)
    }

    @Test
    fun `success clears a previous error`() = runTest {
        loggedIn()
        settings.setLastError("antiguo")
        runner.run()
        assertNull(settings.lastError())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.sync.*"`
Expected: compilation FAILS with unresolved `SyncRunner`, `AlertSink`, `SyncOutcome`.

- [ ] **Step 3: Implement**

`app/src/main/java/com/faltasipasen/app/sync/SyncRunner.kt`:
```kotlin
package com.faltasipasen.app.sync

import com.faltasipasen.app.data.SettingsStore
import com.faltasipasen.app.data.Snapshot
import com.faltasipasen.app.data.SnapshotStore
import com.faltasipasen.app.domain.AbsenceCalculator
import com.faltasipasen.app.domain.AbsenceStats
import com.faltasipasen.app.domain.ThresholdAlert
import com.faltasipasen.app.domain.ThresholdEvaluator
import com.faltasipasen.app.scraper.IpasenScraper
import com.faltasipasen.app.scraper.ScrapeException
import com.faltasipasen.app.scraper.ScrapeException.Reason
import com.faltasipasen.app.scraper.ScrapeResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

interface AlertSink {
    fun showThresholdAlerts(thresholdPercent: Double, alerts: List<ThresholdAlert>)
    fun showSyncProblem(message: String)
}

sealed interface SyncOutcome {
    data class Success(val stats: AbsenceStats, val newAlerts: List<ThresholdAlert>) : SyncOutcome
    data object MissingCredentials : SyncOutcome
    data class Failed(val reason: Reason, val message: String) : SyncOutcome
}

class SyncRunner(
    private val scraper: IpasenScraper,
    private val snapshots: SnapshotStore,
    private val settings: SettingsStore,
    private val alerts: AlertSink,
    private val today: () -> LocalDate,
    private val nowMillis: () -> Long,
) {
    private val mutex = Mutex()

    suspend fun run(): SyncOutcome = mutex.withLock {
        val credentials = settings.credentials() ?: return@withLock SyncOutcome.MissingCredentials

        val result = try {
            scraper.scrape(credentials).also(::validate)
        } catch (e: ScrapeException) {
            return@withLock fail(e)
        }

        val snapshot = Snapshot(result.schedule, result.calendar, result.absences, nowMillis())
        snapshots.save(snapshot)

        val stats = AbsenceCalculator.calculate(
            snapshot.schedule, snapshot.calendar, snapshot.absences, settings.cutoffDate(), today(),
        )
        val threshold = settings.thresholdPercent()
        val evaluation = ThresholdEvaluator.evaluate(stats, threshold, settings.activeAlertKeys())
        if (evaluation.newAlerts.isNotEmpty()) alerts.showThresholdAlerts(threshold, evaluation.newAlerts)
        settings.setActiveAlertKeys(evaluation.activeKeys)
        settings.setLastError(null)
        SyncOutcome.Success(stats, evaluation.newAlerts)
    }

    private fun validate(result: ScrapeResult) {
        if (result.schedule.isEmpty()) {
            throw ScrapeException(Reason.PARSE_ERROR, "horario: iPasen no devolvió ninguna clase")
        }
        if (result.calendar.none { it.lectivo }) {
            throw ScrapeException(Reason.PARSE_ERROR, "calendario: iPasen no devolvió días lectivos")
        }
    }

    private fun fail(e: ScrapeException): SyncOutcome.Failed {
        val message = userMessage(e)
        if (e.reason == Reason.LOGIN_FAILED && settings.lastError() != message) alerts.showSyncProblem(message)
        settings.setLastError(message)
        return SyncOutcome.Failed(e.reason, message)
    }

    companion object {
        fun userMessage(e: ScrapeException): String = when (e.reason) {
            Reason.LOGIN_FAILED -> "iPasen ha rechazado el usuario o la contraseña. Revísalos en Ajustes."
            Reason.NETWORK -> "No se pudo conectar con iPasen. Se volverá a intentar."
            Reason.TIMEOUT -> "iPasen tardó demasiado en responder. Se volverá a intentar."
            Reason.PARSE_ERROR -> "No se pudieron leer los datos de iPasen (${e.message}). Puede que hayan cambiado la web."
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (all tests so far; 9 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/faltasipasen/app/sync app/src/test/java/com/faltasipasen/app/sync
git commit -m "feat: orchestrate scrape, snapshot, calculation and alerts" -m "$TRAILER"
```

---

### Task 8: WebView scraping engine

The engine is site-agnostic: it knows *how* to drive a hidden WebView and run step scripts; *what* to run lives in `IpasenSite.kt` and the JS assets, which get their real content in Task 11. Until then the step scripts are stubs that fail with a clear message, and `IpasenSite.LOGIN_URL` is empty so the scraper reports "sin configurar".

How a step runs: Kotlin wraps `common.js` + the step script in `(function(){ const STEP="<id>"; <prelude> <common.js> <step.js> })();`, evaluates it, and waits for `AndroidBridge.onResult(STEP, json)`. If the page navigates while waiting (redirect chains), the script is injected again (max 3 times; never for `login`, which would resubmit the form).

Login is two steps: `login.js` fills the form, reports `ok` and clicks submit 300 ms later (the report must happen before the navigation destroys the JS context); `login_check.js` runs on the resulting page and reports `ok` or `loginFailed`.

**Files:**
- Create: `app/src/main/java/com/faltasipasen/app/scraper/IpasenSite.kt`, `scraper/WebViewScraper.kt`
- Create: `app/src/main/assets/scraper/common.js`, `login.js`, `login_check.js`, `schedule.js`, `calendar.js`, `absences.js`

**Interfaces:**
- Consumes: `IpasenScraper`, `ScrapeResult`, `ScrapeException`, `ScrapeParsers` (Task 4); `Credentials` (Task 2).
- Produces:
  - `data class ScrapeStep(val id: String, val url: String?, val asset: String)` with `companion object { const val LOGIN = "login"; const val LOGIN_CHECK = "loginCheck" }`
  - `object IpasenSite { val LOGIN_URL: String; val ALLOWED_HOST_SUFFIXES: List<String>; val SCHEDULE: ScrapeStep; val CALENDAR: ScrapeStep; val ABSENCES: ScrapeStep }`
  - `class WebViewScraper(context: Context) : IpasenScraper`
  - JS helpers available to every step script: `STEP`, `run(asyncFn)`, `waitFor(predicate, timeoutMs)`, `setNativeValue(input, value)`, `fetchJson(url, options)`, `toIsoDate(text)`, `sleep(ms)`; `CREDS = {user, pass}` only in `login.js`.

- [ ] **Step 1: Write the JS helpers and the stub step scripts**

`app/src/main/assets/scraper/common.js`:
```js
// Prelude for every step. The caller defines STEP (and CREDS for login).
// Contract: every step ends by calling report() exactly once through run().
function report(payload) {
  AndroidBridge.onResult(STEP, JSON.stringify(payload));
}

function run(fn) {
  Promise.resolve()
    .then(fn)
    .then(function (data) { report({ ok: true, data: data === undefined ? null : data }); })
    .catch(function (e) {
      var payload = { ok: false, error: String(e && e.message ? e.message : e) };
      if (e && e.loginFailed) payload.loginFailed = true;
      report(payload);
    });
}

function sleep(ms) {
  return new Promise(function (resolve) { setTimeout(resolve, ms); });
}

// Polls predicate() until it returns a truthy value (returned) or the timeout expires.
function waitFor(predicate, timeoutMs) {
  var started = Date.now();
  return new Promise(function (resolve, reject) {
    (function poll() {
      var value = null;
      try { value = predicate(); } catch (ignored) {}
      if (value) return resolve(value);
      if (Date.now() - started > timeoutMs) return reject(new Error('tiempo de espera agotado en ' + STEP));
      setTimeout(poll, 250);
    })();
  });
}

// Sets an input value so Angular/React/Vue bindings notice it.
function setNativeValue(input, value) {
  var setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(input), 'value').set;
  setter.call(input, value);
  input.dispatchEvent(new Event('input', { bubbles: true }));
  input.dispatchEvent(new Event('change', { bubbles: true }));
}

// Same-origin request with the logged-in session cookies.
async function fetchJson(url, options) {
  var response = await fetch(url, Object.assign({ credentials: 'include' }, options || {}));
  if (!response.ok) throw new Error('HTTP ' + response.status + ' en ' + url);
  return response.json();
}

// "5/9/2026" or "05-09-2026" -> "2026-09-05"
function toIsoDate(text) {
  var m = /^(\d{1,2})[\/-](\d{1,2})[\/-](\d{4})$/.exec(String(text).trim());
  if (!m) throw new Error('fecha no reconocida: ' + text);
  return m[3] + '-' + m[2].padStart(2, '0') + '-' + m[1].padStart(2, '0');
}
```

`app/src/main/assets/scraper/login.js`:
```js
// Real implementation written in Task 11 (fills the form with CREDS, reports ok, submits 300 ms later).
run(async function () { throw new Error('login pendiente de configurar'); });
```

`app/src/main/assets/scraper/login_check.js`:
```js
// Real implementation written in Task 11 (reports ok, or loginFailed if iPasen rejects the credentials).
run(async function () { throw new Error('comprobación de login pendiente de configurar'); });
```

`app/src/main/assets/scraper/schedule.js`:
```js
// Real implementation written in Task 11. Returns [{subject, day (1=lunes..7), hours}].
run(async function () { throw new Error('horario pendiente de configurar'); });
```

`app/src/main/assets/scraper/calendar.js`:
```js
// Real implementation written in Task 11. Returns [{date: "YYYY-MM-DD", lectivo}] from 1 Sept to at least 15 Feb.
run(async function () { throw new Error('calendario pendiente de configurar'); });
```

`app/src/main/assets/scraper/absences.js`:
```js
// Real implementation written in Task 11. Returns [{date, subject, hours, justified}] without "retrasos".
run(async function () { throw new Error('faltas pendiente de configurar'); });
```

- [ ] **Step 2: Write `IpasenSite.kt`**

`app/src/main/java/com/faltasipasen/app/scraper/IpasenSite.kt`:
```kotlin
package com.faltasipasen.app.scraper

/** Un paso del scraping: opcionalmente carga [url] y ejecuta el script [asset]. */
data class ScrapeStep(val id: String, val url: String?, val asset: String) {
    companion object {
        const val LOGIN = "login"
        const val LOGIN_CHECK = "loginCheck"
    }
}

/**
 * Todo lo que depende de la web real de iPasen. Los valores se obtienen en la
 * Tarea 11 inspeccionando iPasen y están documentados en docs/scraper-selectors.md.
 */
object IpasenSite {
    val LOGIN_URL: String = ""

    val ALLOWED_HOST_SUFFIXES: List<String> = listOf("juntadeandalucia.es")

    val SCHEDULE = ScrapeStep(id = "schedule", url = null, asset = "scraper/schedule.js")
    val CALENDAR = ScrapeStep(id = "calendar", url = null, asset = "scraper/calendar.js")
    val ABSENCES = ScrapeStep(id = "absences", url = null, asset = "scraper/absences.js")
}
```

- [ ] **Step 3: Write `WebViewScraper.kt`**

`app/src/main/java/com/faltasipasen/app/scraper/WebViewScraper.kt`:
```kotlin
package com.faltasipasen.app.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.faltasipasen.app.domain.Credentials
import com.faltasipasen.app.scraper.ScrapeException.Reason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

class WebViewScraper(private val context: Context) : IpasenScraper {

    override suspend fun scrape(credentials: Credentials): ScrapeResult {
        if (IpasenSite.LOGIN_URL.isBlank()) {
            throw ScrapeException(Reason.PARSE_ERROR, "scraper de iPasen sin configurar")
        }
        return withContext(Dispatchers.Main) {
            val session = Session(context.applicationContext)
            try {
                withTimeout(TOTAL_TIMEOUT_MS) { session.run(credentials) }
            } catch (e: TimeoutCancellationException) {
                throw ScrapeException(Reason.TIMEOUT, "la sincronización completa superó el tiempo máximo")
            } finally {
                session.destroy()
            }
        }
    }

    private sealed interface PageEvent {
        data class Finished(val url: String) : PageEvent
        data class Failed(val description: String) : PageEvent
    }

    private class Bridge(private val results: Channel<Pair<String, String>>) {
        @JavascriptInterface
        fun onResult(step: String, json: String) {
            results.trySend(step to json)
        }
    }

    private class Session(private val context: Context) {
        private val pageEvents = Channel<PageEvent>(Channel.UNLIMITED)
        private val results = Channel<Pair<String, String>>(Channel.UNLIMITED)
        private val webView: WebView = createWebView()

        @SuppressLint("SetJavaScriptEnabled")
        private fun createWebView(): WebView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            addJavascriptInterface(Bridge(results), BRIDGE_NAME)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val host = request.url.host ?: return true
                    return IpasenSite.ALLOWED_HOST_SUFFIXES.none { host == it || host.endsWith(".$it") }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    pageEvents.trySend(PageEvent.Finished(url))
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) pageEvents.trySend(PageEvent.Failed(error.description.toString()))
                }
            }
            // Sin ventana: tamaño de móvil para que la web maquete y ejecute con normalidad.
            measure(
                View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)
            resumeTimers()
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        }

        suspend fun run(credentials: Credentials): ScrapeResult {
            clearCookies()
            load(IpasenSite.LOGIN_URL)
            drainPageEvents()
            ScrapeParsers.checkOk(runStep(ScrapeStep.LOGIN, LOGIN_ASSET, credentialsJs(credentials)), "login")
            awaitPage(LOGIN_NAVIGATION_TIMEOUT_MS, required = false)
            ScrapeParsers.checkOk(runStep(ScrapeStep.LOGIN_CHECK, LOGIN_CHECK_ASSET), "login")

            val schedule = ScrapeParsers.parseSchedule(runDataStep(IpasenSite.SCHEDULE))
            val calendar = ScrapeParsers.parseCalendar(runDataStep(IpasenSite.CALENDAR))
            val absences = ScrapeParsers.parseAbsences(runDataStep(IpasenSite.ABSENCES))
            return ScrapeResult(schedule, calendar, absences)
        }

        fun destroy() {
            webView.stopLoading()
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.destroy()
            CookieManager.getInstance().removeAllCookies(null)
        }

        private suspend fun runDataStep(step: ScrapeStep): String {
            step.url?.let { load(it) }
            return runStep(step.id, step.asset)
        }

        private suspend fun load(url: String) {
            drainPageEvents()
            webView.loadUrl(url)
            awaitPage(PAGE_TIMEOUT_MS, required = true)
        }

        private suspend fun awaitPage(timeoutMs: Long, required: Boolean) {
            when (val event = withTimeoutOrNull(timeoutMs) { pageEvents.receive() }) {
                is PageEvent.Failed -> throw ScrapeException(Reason.NETWORK, event.description)
                is PageEvent.Finished -> Unit
                null -> if (required) throw ScrapeException(Reason.TIMEOUT, "la página de iPasen no terminó de cargar")
            }
        }

        private suspend fun runStep(step: String, asset: String, prelude: String = ""): String {
            while (results.tryReceive().isSuccess) Unit
            val script = buildScript(step, asset, prelude)
            webView.evaluateJavascript(script, null)
            var reinjections = 0
            val deadline = System.currentTimeMillis() + STEP_TIMEOUT_MS
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) throw ScrapeException(Reason.TIMEOUT, "el paso '$step' no respondió")
                val result = withTimeoutOrNull(minOf(remaining, POLL_MS)) { results.receive() }
                if (result != null && result.first == step) return result.second

                when (val event = pageEvents.tryReceive().getOrNull()) {
                    is PageEvent.Failed -> throw ScrapeException(Reason.NETWORK, event.description)
                    is PageEvent.Finished -> if (step != ScrapeStep.LOGIN && reinjections < MAX_REINJECTIONS) {
                        reinjections++
                        webView.evaluateJavascript(script, null)
                    }
                    null -> Unit
                }
            }
        }

        private fun buildScript(step: String, asset: String, prelude: String): String = buildString {
            append("(function(){\n")
            append("const STEP=").append(JSONObject.quote(step)).append(";\n")
            append(prelude).append('\n')
            append(readAsset(COMMON_ASSET)).append('\n')
            append(readAsset(asset)).append('\n')
            append("})();")
        }

        private fun credentialsJs(credentials: Credentials): String =
            "const CREDS={user:${JSONObject.quote(credentials.username)},pass:${JSONObject.quote(credentials.password)}};"

        private fun readAsset(path: String): String =
            context.assets.open(path).bufferedReader().use { it.readText() }

        private fun drainPageEvents() {
            while (pageEvents.tryReceive().isSuccess) Unit
        }

        private suspend fun clearCookies() = suspendCancellableCoroutine { cont ->
            CookieManager.getInstance().removeAllCookies { if (cont.isActive) cont.resume(Unit) }
        }
    }

    private companion object {
        const val BRIDGE_NAME = "AndroidBridge"
        const val COMMON_ASSET = "scraper/common.js"
        const val LOGIN_ASSET = "scraper/login.js"
        const val LOGIN_CHECK_ASSET = "scraper/login_check.js"
        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 2340
        const val PAGE_TIMEOUT_MS = 30_000L
        const val LOGIN_NAVIGATION_TIMEOUT_MS = 15_000L
        const val STEP_TIMEOUT_MS = 45_000L
        const val TOTAL_TIMEOUT_MS = 240_000L
        const val POLL_MS = 500L
        const val MAX_REINJECTIONS = 3
    }
}
```

- [ ] **Step 4: Build and run all tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all unit tests pass. (Behaviour against the real site is verified in Tasks 11–12.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/faltasipasen/app/scraper app/src/main/assets
git commit -m "feat: add hidden WebView engine that runs step scripts against iPasen" -m "$TRAILER"
```

---

### Task 9: Notifications, background worker and app wiring

**Files:**
- Create: `app/src/main/java/com/faltasipasen/app/notify/NotificationAlertSink.kt`, `sync/SyncWorker.kt`, `sync/SyncScheduler.kt`, `AppGraph.kt`, `FaltasApp.kt`
- Modify: `app/src/main/AndroidManifest.xml` (full replacement below)

**Interfaces:**
- Consumes: `AlertSink`, `SyncRunner`, `SyncOutcome` (Task 7); `AlertFormatter` (Task 3); `PrefsSettingsStore`, `KeystoreCipher`, `SettingsStore` (Task 6); `FileSnapshotStore`, `SnapshotStore` (Task 5); `WebViewScraper` (Task 8); `ScrapeException.Reason` (Task 4).
- Produces:
  - `class NotificationAlertSink(context: Context) : AlertSink` + `companion object { const val CHANNEL_ALERTS = "avisos_faltas"; const val CHANNEL_SYNC = "sincronizacion"; const val ID_FOREGROUND = 1003; fun ensureChannels(context: Context) }`
  - `class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker`
  - `object SyncScheduler { const val PERIODIC_WORK = "sync-periodica"; const val NOW_WORK = "sync-ahora"; fun schedulePeriodic(context: Context, hours: Int); fun syncNow(context: Context); fun cancelAll(context: Context) }`
  - `class AppGraph(context: Context) { val today: () -> LocalDate; val settings: SettingsStore; val snapshots: SnapshotStore; val syncRunner: SyncRunner }`
  - `class FaltasApp : Application() { lateinit var graph: AppGraph }`

- [ ] **Step 1: Write the notification sink**

`app/src/main/java/com/faltasipasen/app/notify/NotificationAlertSink.kt`:
```kotlin
package com.faltasipasen.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.faltasipasen.app.domain.AlertFormatter
import com.faltasipasen.app.domain.ThresholdAlert
import com.faltasipasen.app.sync.AlertSink
import com.faltasipasen.app.ui.MainActivity

class NotificationAlertSink(private val context: Context) : AlertSink {

    override fun showThresholdAlerts(thresholdPercent: Double, alerts: List<ThresholdAlert>) =
        post(ID_THRESHOLD, AlertFormatter.thresholdTitle(thresholdPercent), AlertFormatter.thresholdBody(alerts))

    override fun showSyncProblem(message: String) =
        post(ID_PROBLEM, "Problema al sincronizar con iPasen", message)

    @SuppressLint("MissingPermission")
    private fun post(id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val CHANNEL_ALERTS = "avisos_faltas"
        const val CHANNEL_SYNC = "sincronizacion"
        const val ID_FOREGROUND = 1003
        private const val ID_THRESHOLD = 1001
        private const val ID_PROBLEM = 1002

        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, "Avisos de faltas", NotificationManager.IMPORTANCE_HIGH),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_SYNC, "Sincronización con iPasen", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
```

- [ ] **Step 2: Write the worker and the scheduler**

`app/src/main/java/com/faltasipasen/app/sync/SyncWorker.kt`:
```kotlin
package com.faltasipasen.app.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.faltasipasen.app.FaltasApp
import com.faltasipasen.app.notify.NotificationAlertSink
import com.faltasipasen.app.scraper.ScrapeException.Reason
import kotlinx.coroutines.CancellationException

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val graph = (applicationContext as FaltasApp).graph
        val outcome = try {
            graph.syncRunner.run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            graph.settings.setLastError("Error inesperado al sincronizar: ${e.javaClass.simpleName}")
            return Result.failure()
        }
        return when (outcome) {
            is SyncOutcome.Success, SyncOutcome.MissingCredentials -> Result.success()
            is SyncOutcome.Failed -> when (outcome.reason) {
                Reason.NETWORK, Reason.TIMEOUT -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                Reason.LOGIN_FAILED, Reason.PARSE_ERROR -> Result.failure()
            }
        }
    }

    // Solo se usa si Android ejecuta la tarea urgente como servicio en primer plano (Android < 12).
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, NotificationAlertSink.CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Actualizando faltas desde iPasen…")
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NotificationAlertSink.ID_FOREGROUND, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NotificationAlertSink.ID_FOREGROUND, notification)
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
```

`app/src/main/java/com/faltasipasen/app/sync/SyncScheduler.kt`:
```kotlin
package com.faltasipasen.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    const val PERIODIC_WORK = "sync-periodica"
    const val NOW_WORK = "sync-ahora"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(context: Context, hours: Int) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelAll(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK)
        workManager.cancelUniqueWork(NOW_WORK)
    }
}
```

- [ ] **Step 3: Write the graph, the Application and the final manifest**

`app/src/main/java/com/faltasipasen/app/AppGraph.kt`:
```kotlin
package com.faltasipasen.app

import android.content.Context
import com.faltasipasen.app.data.FileSnapshotStore
import com.faltasipasen.app.data.KeystoreCipher
import com.faltasipasen.app.data.PrefsSettingsStore
import com.faltasipasen.app.data.SettingsStore
import com.faltasipasen.app.data.SnapshotStore
import com.faltasipasen.app.notify.NotificationAlertSink
import com.faltasipasen.app.scraper.WebViewScraper
import com.faltasipasen.app.sync.SyncRunner
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class AppGraph(context: Context) {
    private val app = context.applicationContext
    private val zone: ZoneId = ZoneId.of("Europe/Madrid")

    val today: () -> LocalDate = { LocalDate.now(zone) }

    val settings: SettingsStore = PrefsSettingsStore(
        app.getSharedPreferences("ajustes", Context.MODE_PRIVATE),
        KeystoreCipher(),
        today,
    )

    val snapshots: SnapshotStore = FileSnapshotStore(File(app.filesDir, "snapshot.json"))

    val syncRunner = SyncRunner(
        scraper = WebViewScraper(app),
        snapshots = snapshots,
        settings = settings,
        alerts = NotificationAlertSink(app),
        today = today,
        nowMillis = System::currentTimeMillis,
    )
}
```

`app/src/main/java/com/faltasipasen/app/FaltasApp.kt`:
```kotlin
package com.faltasipasen.app

import android.app.Application
import android.webkit.WebView
import com.faltasipasen.app.notify.NotificationAlertSink
import com.faltasipasen.app.sync.SyncScheduler

class FaltasApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        NotificationAlertSink.ensureChannels(this)
        // Permite inspeccionar el WebView oculto desde chrome://inspect en builds de depuración.
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        if (graph.settings.credentials() != null) {
            SyncScheduler.schedulePeriodic(this, graph.settings.syncIntervalHours())
        }
    }
}
```

`app/src/main/AndroidManifest.xml` (replace the whole file):
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <application
        android:name=".FaltasApp"
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name="androidx.work.impl.foreground.SystemForegroundService"
            android:foregroundServiceType="dataSync"
            tools:node="merge" />
    </application>
</manifest>
```

- [ ] **Step 4: Build and run all tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/faltasipasen/app app/src/main/AndroidManifest.xml
git commit -m "feat: run syncs in WorkManager and notify threshold crossings" -m "$TRAILER"
```

---

### Task 10: User interface

Three screens, no navigation library: **Setup** (shown while there are no credentials — the only screen the user must fill in), **Home** (percentages, margin, per-subject list, manual sync) and **Settings**. On setup the cut-off date is shown prefilled and visible so the user confirms it consciously; the threshold is prefilled with 15 %.

**Files:**
- Create: `app/src/main/java/com/faltasipasen/app/ui/InputValidation.kt`, `ui/MainViewModel.kt`, `ui/Components.kt`, `ui/SetupScreen.kt`, `ui/HomeScreen.kt`, `ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/faltasipasen/app/ui/MainActivity.kt` (full replacement)
- Test: `app/src/test/java/com/faltasipasen/app/ui/InputValidationTest.kt`

**Interfaces:**
- Consumes: `FaltasApp.graph` (Task 9); `SyncScheduler` (Task 9); `AbsenceCalculator`, `AbsenceStats`, `HoursStats`, `Credentials`, `AlertFormatter` (Tasks 2–3); `Snapshot` (Task 5).
- Produces:
  - `object InputValidation { val SYNC_INTERVALS: List<Int>; fun parseThreshold(text: String): Double?; fun credentialsValid(username: String, password: String): Boolean }`
  - `data class UiState(...)` and `class MainViewModel(application: Application) : AndroidViewModel` with `state: StateFlow<UiState>`, `completeSetup`, `saveSettings`, `syncNow`, `logout`.

- [ ] **Step 1: Write the failing validation test**

`app/src/test/java/com/faltasipasen/app/ui/InputValidationTest.kt`:
```kotlin
package com.faltasipasen.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidationTest {
    @Test
    fun `threshold accepts comma and dot decimals`() {
        assertEquals(15.5, InputValidation.parseThreshold("15,5")!!, 0.0)
        assertEquals(20.0, InputValidation.parseThreshold(" 20 ")!!, 0.0)
        assertEquals(100.0, InputValidation.parseThreshold("100")!!, 0.0)
    }

    @Test
    fun `threshold rejects out of range or garbage`() {
        assertNull(InputValidation.parseThreshold("0"))
        assertNull(InputValidation.parseThreshold("100,1"))
        assertNull(InputValidation.parseThreshold("-3"))
        assertNull(InputValidation.parseThreshold("quince"))
        assertNull(InputValidation.parseThreshold(""))
    }

    @Test
    fun `credentials need a username and a password`() {
        assertTrue(InputValidation.credentialsValid("alumno", "x"))
        assertFalse(InputValidation.credentialsValid("  ", "x"))
        assertFalse(InputValidation.credentialsValid("alumno", ""))
    }
}
```

Run: `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.ui.*"`
Expected: compilation FAILS with unresolved `InputValidation`.

- [ ] **Step 2: Implement validation and the view model**

`app/src/main/java/com/faltasipasen/app/ui/InputValidation.kt`:
```kotlin
package com.faltasipasen.app.ui

object InputValidation {
    val SYNC_INTERVALS: List<Int> = listOf(6, 12, 24)

    fun parseThreshold(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it <= 100.0 }

    fun credentialsValid(username: String, password: String): Boolean =
        username.isNotBlank() && password.isNotEmpty()
}
```

Run: `./gradlew testDebugUnitTest --tests "com.faltasipasen.app.ui.*"` → PASS (3 tests).

`app/src/main/java/com/faltasipasen/app/ui/MainViewModel.kt`:
```kotlin
package com.faltasipasen.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.faltasipasen.app.FaltasApp
import com.faltasipasen.app.data.Snapshot
import com.faltasipasen.app.domain.AbsenceCalculator
import com.faltasipasen.app.domain.AbsenceStats
import com.faltasipasen.app.domain.Credentials
import com.faltasipasen.app.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class UiState(
    val hasCredentials: Boolean,
    val username: String,
    val stats: AbsenceStats?,
    val thresholdPercent: Double,
    val cutoffDate: LocalDate,
    val syncIntervalHours: Int,
    val lastSyncMillis: Long?,
    val lastError: String?,
    val syncing: Boolean,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as FaltasApp).graph
    private val workManager = WorkManager.getInstance(application)

    private val syncing: Flow<Boolean> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(SyncScheduler.NOW_WORK),
        workManager.getWorkInfosForUniqueWorkFlow(SyncScheduler.PERIODIC_WORK),
    ) { now, periodic -> (now + periodic).any { it.state == WorkInfo.State.RUNNING } }

    val state: StateFlow<UiState> =
        combine(graph.snapshots.current, graph.settings.changes, syncing) { snapshot, _, running ->
            buildState(snapshot, running)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), buildState(graph.snapshots.current.value, false))

    private fun buildState(snapshot: Snapshot?, running: Boolean): UiState {
        val settings = graph.settings
        val credentials = settings.credentials()
        val stats = snapshot?.let {
            AbsenceCalculator.calculate(it.schedule, it.calendar, it.absences, settings.cutoffDate(), graph.today())
        }
        return UiState(
            hasCredentials = credentials != null,
            username = credentials?.username.orEmpty(),
            stats = stats,
            thresholdPercent = settings.thresholdPercent(),
            cutoffDate = settings.cutoffDate(),
            syncIntervalHours = settings.syncIntervalHours(),
            lastSyncMillis = snapshot?.syncedAtMillis,
            lastError = settings.lastError(),
            syncing = running,
        )
    }

    fun completeSetup(username: String, password: String, thresholdPercent: Double, cutoff: LocalDate) {
        val settings = graph.settings
        settings.setThresholdPercent(thresholdPercent)
        settings.setCutoffDate(cutoff)
        settings.saveCredentials(Credentials(username.trim(), password))
        SyncScheduler.schedulePeriodic(getApplication(), settings.syncIntervalHours())
        SyncScheduler.syncNow(getApplication())
    }

    fun saveSettings(thresholdPercent: Double, cutoff: LocalDate, intervalHours: Int, username: String, newPassword: String) {
        val settings = graph.settings
        settings.setThresholdPercent(thresholdPercent)
        settings.setCutoffDate(cutoff)
        settings.setSyncIntervalHours(intervalHours)
        SyncScheduler.schedulePeriodic(getApplication(), intervalHours)

        val current = settings.credentials() ?: return
        val newUsername = username.trim()
        if (newUsername != current.username || newPassword.isNotEmpty()) {
            settings.saveCredentials(Credentials(newUsername, newPassword.ifEmpty { current.password }))
            SyncScheduler.syncNow(getApplication())
        }
    }

    fun syncNow() = SyncScheduler.syncNow(getApplication())

    fun logout() {
        SyncScheduler.cancelAll(getApplication())
        graph.settings.clearAll()
        graph.snapshots.clear()
    }
}
```

- [ ] **Step 3: Write shared components**

`app/src/main/java/com/faltasipasen/app/ui/Components.kt`:
```kotlin
package com.faltasipasen.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish: Locale = Locale.forLanguageTag("es-ES")
private val madrid: ZoneId = ZoneId.of("Europe/Madrid")
private val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", spanish)
private val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", spanish)

fun formatNumber(value: Double): String = DecimalFormat("0.#", DecimalFormatSymbols(spanish)).format(value)

fun formatHours(value: Double): String = "${formatNumber(value)} h"

fun formatLongDate(date: LocalDate): String = date.format(longDate)

fun formatMillis(millis: Long): String = Instant.ofEpochMilli(millis).atZone(madrid).format(dateTime)

@Composable
fun PercentBar(percent: Double, thresholdPercent: Double, modifier: Modifier = Modifier) {
    val over = percent >= thresholdPercent
    LinearProgressIndicator(
        progress = { (percent / thresholdPercent).toFloat().coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth(),
        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
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

- [ ] **Step 4: Write the three screens**

`app/src/main/java/com/faltasipasen/app/ui/SetupScreen.kt`:
```kotlin
package com.faltasipasen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    initialThreshold: Double,
    initialCutoff: LocalDate,
    onSubmit: (username: String, password: String, thresholdPercent: Double, cutoff: LocalDate) -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var thresholdText by rememberSaveable { mutableStateOf(formatNumber(initialThreshold)) }
    var cutoff by rememberSaveable { mutableStateOf(initialCutoff) }
    val threshold = InputValidation.parseThreshold(thresholdText)

    Scaffold(topBar = { TopAppBar(title = { Text("Faltas iPasen") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Entra con tu usuario de iPasen. La app leerá tu horario, tu calendario y tus faltas " +
                    "automáticamente. Tus datos solo se guardan en este móvil, cifrados.",
                style = MaterialTheme.typography.bodyMedium,
            )
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
                style = MaterialTheme.typography.bodyMedium,
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
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Empezar") }
        }
    }
}
```

`app/src/main/java/com/faltasipasen/app/ui/HomeScreen.kt`:
```kotlin
package com.faltasipasen.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.faltasipasen.app.domain.AlertFormatter
import com.faltasipasen.app.domain.HoursStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: UiState, onSync: () -> Unit, onOpenSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Faltas iPasen") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Ajustes") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryCard(state) }
            item { SyncSection(state, onSync) }
            val subjects = state.stats?.subjects.orEmpty()
            if (subjects.isNotEmpty()) {
                item { Text("Por asignatura", style = MaterialTheme.typography.titleMedium) }
                items(subjects, key = { it.label }) { SubjectCard(it, state.thresholdPercent) }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: UiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val stats = state.stats
            if (stats == null) {
                Text("Aún no hay datos", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.syncing) "Leyendo tus datos de iPasen…" else "Pulsa «Sincronizar ahora» para leerlos de iPasen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }
            val global = stats.global
            val over = global.percent >= state.thresholdPercent
            Text("Faltas sobre el total del curso", style = MaterialTheme.typography.labelLarge)
            Text(
                AlertFormatter.percent(global.percent),
                style = MaterialTheme.typography.displayMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            PercentBar(global.percent, state.thresholdPercent)
            Text(
                "${formatHours(global.absentHours)} de falta de ${formatHours(global.courseHours)} lectivas " +
                    "hasta el ${formatLongDate(stats.cutoff)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (over) {
                    "Has superado el aviso del ${AlertFormatter.percent(state.thresholdPercent)}"
                } else {
                    "Margen: ${formatHours(global.marginHours(state.thresholdPercent))} más antes del " +
                        AlertFormatter.percent(state.thresholdPercent)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Injustificadas: ${AlertFormatter.percent(global.unjustifiedPercent)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SyncSection(state: UiState, onSync: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSync, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
            if (state.syncing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Sincronizando…")
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sincronizar ahora")
            }
        }
        state.lastSyncMillis?.let {
            Text("Última actualización: ${formatMillis(it)}", style = MaterialTheme.typography.bodySmall)
        }
        state.lastError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SubjectCard(stats: HoursStats, thresholdPercent: Double) {
    val over = stats.courseHours > 0.0 && stats.percent >= thresholdPercent
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stats.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    AlertFormatter.percent(stats.percent),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (stats.courseHours > 0.0) {
                PercentBar(stats.percent, thresholdPercent)
                Text(
                    "${formatHours(stats.absentHours)} de ${formatHours(stats.courseHours)} · " +
                        "margen ${formatHours(stats.marginHours(thresholdPercent))}",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "${formatHours(stats.absentHours)} de falta. No aparece en tu horario, así que no se puede calcular su %.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
```

`app/src/main/java/com/faltasipasen/app/ui/SettingsScreen.kt`:
```kotlin
package com.faltasipasen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onSave: (thresholdPercent: Double, cutoff: LocalDate, intervalHours: Int, username: String, newPassword: String) -> Unit,
    onLogout: () -> Unit,
) {
    var thresholdText by rememberSaveable { mutableStateOf(formatNumber(state.thresholdPercent)) }
    var cutoff by rememberSaveable { mutableStateOf(state.cutoffDate) }
    var interval by rememberSaveable { mutableIntStateOf(state.syncIntervalHours) }
    var username by rememberSaveable { mutableStateOf(state.username) }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmLogout by remember { mutableStateOf(false) }
    val threshold = InputValidation.parseThreshold(thresholdText)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = thresholdText,
                onValueChange = { thresholdText = it },
                label = { Text("Avisarme al llegar al (%)") },
                singleLine = true,
                isError = threshold == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            CutoffDateField(value = cutoff, onChange = { cutoff = it }, modifier = Modifier.fillMaxWidth())
            Text("Comprobar iPasen cada", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputValidation.SYNC_INTERVALS.forEach { hours ->
                    FilterChip(selected = interval == hours, onClick = { interval = hours }, label = { Text("$hours h") })
                }
            }
            HorizontalDivider()
            Text("Cuenta de iPasen", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario de iPasen") },
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
            Button(
                onClick = { onSave(threshold!!, cutoff, interval, username, newPassword) },
                enabled = threshold != null && username.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }
            OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar sesión y borrar datos")
            }
        }
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
```

- [ ] **Step 5: Replace `MainActivity.kt`**

`app/src/main/java/com/faltasipasen/app/ui/MainActivity.kt`:
```kotlin
package com.faltasipasen.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { AppRoot() } }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private enum class Screen { Home, Settings }

@Composable
private fun AppRoot(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    when {
        !state.hasCredentials -> SetupScreen(
            initialThreshold = state.thresholdPercent,
            initialCutoff = state.cutoffDate,
            onSubmit = { username, password, threshold, cutoff ->
                viewModel.completeSetup(username, password, threshold, cutoff)
                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                screen = Screen.Home
            },
        )

        screen == Screen.Settings -> {
            BackHandler { screen = Screen.Home }
            SettingsScreen(
                state = state,
                onBack = { screen = Screen.Home },
                onSave = { threshold, cutoff, interval, username, newPassword ->
                    viewModel.saveSettings(threshold, cutoff, interval, username, newPassword)
                    screen = Screen.Home
                },
                onLogout = {
                    viewModel.logout()
                    screen = Screen.Home
                },
            )
        }

        else -> HomeScreen(state = state, onSync = viewModel::syncNow, onOpenSettings = { screen = Screen.Settings })
    }
}
```

- [ ] **Step 6: Build and run all tests**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: See the UI running**

Use an emulator (`/c/Users/jesus/AppData/Local/Android/Sdk/emulator/emulator -list-avds`; start one with `emulator -avd <name> &`) or the user's phone with USB debugging on. Then:
```bash
ADB=/c/Users/jesus/AppData/Local/Android/Sdk/platform-tools/adb
./gradlew installDebug
$ADB shell am start -n com.faltasipasen.app/.ui.MainActivity
$ADB exec-out screencap -p > "$TEMP/setup.png"
```
Open the screenshot and check: title "Faltas iPasen", user and password fields, cut-off button showing "15 de febrero de 2027", threshold "15", "Empezar" disabled until both credentials are filled. Enter a FAKE user/password (never real ones here), tap Empezar: Home must appear, the button must show "Sincronizando…" and then the red message "No se pudieron leer los datos de iPasen (scraper de iPasen sin configurar)…" (expected until Task 11). Open Ajustes, change the interval to 6 h, save, reopen and confirm it persisted. "Cerrar sesión y borrar datos" → back to Setup. If no device is available, say so explicitly in the task report instead of claiming the UI works.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/faltasipasen/app/ui app/src/test/java/com/faltasipasen/app/ui
git commit -m "feat: add setup, home and settings screens" -m "$TRAILER"
```

---

### Task 11: Discover iPasen and write the real step scripts (needs the user)

This task cannot be done without the user: iPasen requires their real login. Rules: the **user** types their credentials in the browser window; the agent never asks for, reads, stores or screenshots the password, and does not copy personal data (names, DNI, real absences) into any committed file.

Tools: Chrome DevTools MCP (`mcp__plugin_ecc_chrome-devtools__*`, load them with ToolSearch `select:` first): `new_page`, `emulate`, `navigate_page`, `take_snapshot`, `evaluate_script`, `list_network_requests`, `get_network_request`, `wait_for`.

**Files:**
- Modify: `app/src/main/assets/scraper/login.js`, `login_check.js`, `schedule.js`, `calendar.js`, `absences.js`
- Modify: `app/src/main/java/com/faltasipasen/app/scraper/IpasenSite.kt` (values only)
- Create: `docs/scraper-selectors.md`

**Interfaces:**
- Consumes: JS helpers of `common.js` and the payload contract of Task 4; `ScrapeStep` / `IpasenSite` shape of Task 8.
- Produces: step scripts that satisfy the Task 4 contract against the real iPasen; filled `IpasenSite` (`LOGIN_URL`, `ALLOWED_HOST_SUFFIXES`, and `url` of each data step when the step needs its own page load).

- [ ] **Step 1: Open iPasen in mobile emulation and let the user log in**

1. `new_page` with the iPasen web entry point. Start at `https://www.juntadeandalucia.es/educacion/portals/web/ipasen` and follow its "Acceso a iPasen" link to the real login page (the user can point to the right one if it differs).
2. `emulate` a mid-range Android phone (viewport 412×915, mobile, touch) so the site serves what the WebView will get.
3. `take_snapshot` of the login form. Record: login page URL, CSS selectors for username input, password input and submit button, and whether there is an extra choice (e.g. profile "Alumno/Tutor" or centre) — if so, record its selector too (the login script must handle it).
4. Ask the user to log in themselves in that window. Before the correct password, ask them to try once with a wrong password so you can record the selector and text of the error message.
5. After a correct login, record a selector that only exists when logged in (e.g. main menu / user avatar) and every host visited (for `ALLOWED_HOST_SUFFIXES`).

- [ ] **Step 2: Find where each data set lives**

For timetable (horario), school calendar (calendario, días lectivos) and absences (faltas de asistencia), in this order:
1. Navigate to the section as the user would. Record the URL (or note that it is a single-page app route).
2. `list_network_requests` right after the section loads. If the data comes from a JSON/XHR endpoint, `get_network_request` it and record: method, URL pattern, required headers/body, response shape (field names only). **Prefer the endpoint**: the step script can call it with `fetchJson` from the logged-in page, which survives visual redesigns.
3. If there is no usable endpoint, `take_snapshot` and record the DOM selectors of rows/cells.
4. Check and record:
   - Timetable: how day of week and session duration appear (e.g. "08:15-09:15" → 1 h). One entry per session.
   - Calendar: which marker means lectivo / no lectivo, and how to reach every month from September to the FCT date (month navigation or date parameters). The script must return **every day** from 1 Sept to at least 15 Feb (future months included).
   - Absences: how full absences, justified/unjustified and "retrasos" are shown. Retrasos must be excluded. Record the duration per absence (session length; default 1 h).
   - Subject names: compare timetable names with absence names. If they differ (abbreviations/codes), record the mapping source so `absences.js` outputs timetable names.

- [ ] **Step 3: Write `docs/scraper-selectors.md`**

Structure (fill every section with what was recorded, no personal data):
```markdown
# iPasen: cómo lo lee la app (capturado el AAAA-MM-DD)

## Login
- URL: …
- Usuario / contraseña / botón: `…` / `…` / `…`
- Selección adicional (perfil/centro): … (o "ninguna")
- Indicador de sesión iniciada: `…`
- Error de credenciales: `…` (texto: "…")
- Hosts visitados: …

## Horario
- Acceso: URL/ruta …; fuente: endpoint `METHOD …` | DOM `…`
- Forma de los datos: …
- Duración de cada sesión: …

## Calendario
- … (incluye cómo se recorren los meses hasta febrero)

## Faltas
- … (cómo se distingue justificada / injustificada / retraso; mapeo de nombres si hace falta)

## Si la app deja de funcionar
Repetir esta captura y actualizar los scripts de app/src/main/assets/scraper/.
```

- [ ] **Step 4: Write the five step scripts**

Each script runs after `common.js`, calls `run(...)` exactly once, finishes in < 40 s, and returns the Task 4 payloads. Templates to adapt with the recorded selectors/endpoints (every `SEL_…`, `ENDPOINT_…`, `MAPEAR_…`, `CAMPO_…` token must be replaced):

`login.js`:
```js
run(async function () {
  var user = await waitFor(function () { return document.querySelector('SEL_USUARIO'); }, 20000);
  var pass = document.querySelector('SEL_CLAVE');
  var submit = document.querySelector('SEL_ENTRAR');
  if (!pass || !submit) throw new Error('formulario de login no encontrado');
  setNativeValue(user, CREDS.user);
  setNativeValue(pass, CREDS.pass);
  // Informar antes de enviar: la navegación destruye este contexto JS.
  setTimeout(function () { submit.click(); }, 300);
  return 'enviado';
});
```

`login_check.js`:
```js
run(async function () {
  var outcome = await waitFor(function () {
    if (document.querySelector('SEL_SESION_INICIADA')) return 'ok';
    var error = document.querySelector('SEL_ERROR_LOGIN');
    if (error && error.textContent.trim()) return 'error:' + error.textContent.trim();
    return null;
  }, 25000);
  if (outcome !== 'ok') {
    var e = new Error(outcome.slice('error:'.length));
    e.loginFailed = true;
    throw e;
  }
  return 'ok';
});
```

`schedule.js` (endpoint variant; parse the DOM only if there is no endpoint):
```js
run(async function () {
  var raw = await fetchJson('ENDPOINT_HORARIO');
  return raw.MAPEAR_SESIONES.map(function (s) {
    return { subject: s.CAMPO_ASIGNATURA, day: s.CAMPO_DIA_1_A_7, hours: s.CAMPO_DURACION_HORAS };
  });
});
```

`calendar.js`: build the list of every date from 1 Sept of the course to 28 Feb (calendar endpoint per month, or month-by-month navigation), mark `lectivo` with the recorded marker, return `[{date: 'YYYY-MM-DD', lectivo}]` (use `toIsoDate` for dd/mm/yyyy text).

`absences.js`: return only full-session absences as `[{date, subject, hours, justified}]`, excluding retrasos, with subject names equal to the timetable names.

- [ ] **Step 5: Verify each script in the logged-in desktop page**

For each step, run with `evaluate_script` (paste the literal contents of `common.js` and of the step file where indicated):
```js
() => new Promise(function (resolve) {
  window.AndroidBridge = { onResult: function (step, json) { resolve(JSON.parse(json)); } };
  const STEP = 'schedule';
  /* contenido de common.js */
  /* contenido de schedule.js */
})
```
Expected for each data step: `{ok: true, data: [...]}` with the contract shape. Show the user only aggregates (sessions per weekday, lectivo days per month, absences justified/unjustified) and ask them to confirm they match iPasen. Also run `login_check.js` while logged in → `{ok: true}`.

- [ ] **Step 6: Fill `IpasenSite.kt`**

Set `LOGIN_URL` to the recorded login URL, add every recorded host suffix to `ALLOWED_HOST_SUFFIXES`, and set each step's `url` to the page it must load first (keep `null` when the script only needs the logged-in session, e.g. endpoint variants run from the post-login page).

- [ ] **Step 7: Build, check for leftovers and commit**

```bash
./gradlew assembleDebug testDebugUnitTest
grep -nE "SEL_|ENDPOINT_|MAPEAR_|CAMPO_|pendiente de configurar" app/src/main/assets/scraper/*.js || echo "sin marcadores"
git add app/src/main/assets/scraper app/src/main/java/com/faltasipasen/app/scraper/IpasenSite.kt docs/scraper-selectors.md
git diff --cached | grep -niE "dni|[0-9]{8}[A-Z]" || echo "sin datos personales evidentes"
git commit -m "feat: read timetable, calendar and absences from the real iPasen site" -m "$TRAILER"
```
Expected: build OK, "sin marcadores"; review any line the personal-data grep prints before committing.

---

### Task 12: On-device end-to-end verification and README

**Files:**
- Create: `README.md`
- Modify: whatever the verification proves broken (fix, re-run the affected unit tests, commit separately with a `fix:` message).

- [ ] **Step 1: Install and do the real first sync**

```bash
ADB=/c/Users/jesus/AppData/Local/Android/Sdk/platform-tools/adb
./gradlew installDebug
$ADB shell am start -n com.faltasipasen.app/.ui.MainActivity
```
The user enters their real credentials on the device, checks the cut-off date, taps "Empezar" and allows notifications. If anything stalls, open `chrome://inspect/#devices` in desktop Chrome to watch the hidden WebView (debug builds only). Expected: Home shows total %, hours, margin and the subject list.

- [ ] **Step 2: Cross-check the numbers with the user**

Ask the user to compare, for two subjects, the absences in iPasen with the hours shown, and to sanity-check total teaching hours (weekly hours × lectivo weeks until the cut-off). Any mismatch goes back to the relevant step script (Task 11 procedure) or to `AbsenceCalculator` with a new failing unit test first.

- [ ] **Step 3: Verify the alert**

In Ajustes set the threshold just below the current total % and save; tap "Sincronizar ahora". Expected: one notification "Has superado el X % de faltas" listing Total and the subjects above it. Sync again → no second notification. Restore the user's threshold.

- [ ] **Step 4: Verify the background sync with the app closed**

Swipe the app away, then force the periodic job:
```bash
$ADB shell dumpsys jobscheduler | grep -A2 "com.faltasipasen.app" | head -20   # note the job id
$ADB shell cmd jobscheduler run -f com.faltasipasen.app <ID>
$ADB logcat -d | grep -iE "WM-|SyncWorker" | tail -20
```
Expected: the worker reports SUCCESS and, reopening the app, "Última actualización" shows the new time. If the WebView does not progress in the background (steps time out only when the app is closed), apply the spec's fallback: at the start of `SyncWorker.doWork()` add `try { setForeground(getForegroundInfo()) } catch (e: IllegalStateException) { }`, rebuild and repeat this step.

- [ ] **Step 5: Verify failure handling**

In Ajustes save a wrong password and sync. Expected: one notification "Problema al sincronizar con iPasen" and the red message on Home; the previous numbers stay visible. Restore the right password → the error disappears after the next sync.

- [ ] **Step 6: Write `README.md`**

```markdown
# Faltas iPasen

App Android personal que lee de iPasen tu horario, el calendario lectivo de tu centro y tus faltas,
calcula el % de faltas sobre las horas totales del curso hasta el inicio de la FCT y te avisa al
superar el umbral que elijas.

## Compilar e instalar
1. Android Studio instalado (usa su JDK): `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`
2. Móvil con depuración USB activada y conectado.
3. `./gradlew installDebug`

## Uso
Primera vez: usuario y contraseña de iPasen, comprobar la fecha de fin de clases y pulsar «Empezar».
La app sincroniza sola cada 12 h (configurable en Ajustes) y avisa una vez por cada cruce del umbral.

## Privacidad
Las credenciales se cifran con una clave del Android Keystore y no salen del móvil salvo hacia iPasen.
No hay servidores intermedios.

## Si deja de funcionar
iPasen no tiene API pública: la app lee su web. Si la Junta cambia la web, repite el procedimiento de
`docs/scraper-selectors.md` y actualiza `app/src/main/assets/scraper/`.

## Tests
`./gradlew testDebugUnitTest`
```

- [ ] **Step 7: Final run and commit**

```bash
./gradlew testDebugUnitTest assembleDebug
git add README.md
git commit -m "docs: add build, usage and maintenance guide" -m "$TRAILER"
```
Expected: all green. Report to the user what was verified on the device and anything that could not be verified.
