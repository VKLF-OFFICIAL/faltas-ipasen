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

    @Test
    fun `a cutoff outside the course iPasen shows fails without evaluating thresholds but keeps the snapshot`() = runTest {
        loggedIn()
        settings.setCutoffDate(LocalDate.of(2026, 2, 15))
        val outcome = runner.run() as SyncOutcome.Failed
        assertEquals(ScrapeException.Reason.PARSE_ERROR, outcome.reason)
        assertTrue(outcome.message.contains("Revísala en Ajustes"))
        assertTrue(alerts.thresholdCalls.isEmpty())
        assertEquals(Snapshot(result.schedule, result.calendar, result.absences, 1234L), snapshots.state.value)
        assertEquals(outcome.message, settings.lastError())
    }
}
