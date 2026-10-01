package com.faltasipasen.app.sync

import com.faltasipasen.app.data.PrefsSettingsStore
import com.faltasipasen.app.data.Snapshot
import com.faltasipasen.app.data.SnapshotStore
import com.faltasipasen.app.domain.Absence
import com.faltasipasen.app.domain.CalendarDay
import com.faltasipasen.app.domain.Credentials
import com.faltasipasen.app.domain.ScheduleSlot
import com.faltasipasen.app.domain.ThresholdAlert
import com.faltasipasen.app.scraper.IpasenScraper
import com.faltasipasen.app.scraper.ScrapeException
import com.faltasipasen.app.scraper.ScrapeResult
import com.faltasipasen.app.testutil.InMemorySharedPreferences
import com.faltasipasen.app.testutil.ReversingCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SyncRunnerEdgeCasesTest {
    private val today = LocalDate.of(2026, 9, 30)

    private val result = ScrapeResult(
        schedule = listOf(ScheduleSlot("REDES", 1, 2.0)),
        calendar = listOf(
            CalendarDay(LocalDate.of(2026, 9, 14), true),
            CalendarDay(LocalDate.of(2026, 9, 21), true),
        ),
        absences = listOf(Absence(LocalDate.of(2026, 9, 14), "REDES", 2.0, false)),
    )

    private class FakeScraper(var outcome: () -> ScrapeResult) : IpasenScraper {
        override suspend fun scrape(credentials: Credentials) = outcome()
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
    private val runner = SyncRunner(scraper, snapshots, settings, alerts, { today }, { 0L })

    private fun loggedIn() = settings.saveCredentials(Credentials("alumno", "clave"))

    @Test
    fun `timeout error is recorded and not shown as alert`() = runTest {
        loggedIn()
        scraper.outcome = { throw ScrapeException(ScrapeException.Reason.TIMEOUT, "timeout") }
        val outcome = runner.run() as SyncOutcome.Failed
        assertEquals(ScrapeException.Reason.TIMEOUT, outcome.reason)
        assertTrue(alerts.problems.isEmpty())
        assertNotNull(settings.lastError())
        assertTrue(settings.lastError()!!.isNotBlank())
    }

    @Test
    fun `parse error is recorded and not shown as alert`() = runTest {
        loggedIn()
        scraper.outcome = { throw ScrapeException(ScrapeException.Reason.PARSE_ERROR, "datos raros") }
        val outcome = runner.run() as SyncOutcome.Failed
        assertEquals(ScrapeException.Reason.PARSE_ERROR, outcome.reason)
        assertTrue(alerts.problems.isEmpty())
    }

    @Test
    fun `second login failure does not send a duplicate notification`() = runTest {
        loggedIn()
        scraper.outcome = { throw ScrapeException(ScrapeException.Reason.LOGIN_FAILED, "clave") }
        runner.run() // first failure: notification sent
        runner.run() // same message already stored: no second notification
        assertEquals(1, alerts.problems.size)
    }

    @Test
    fun `user message for each reason is non-blank`() {
        ScrapeException.Reason.entries.forEach { reason ->
            val msg = SyncRunner.userMessage(ScrapeException(reason, "detalle"))
            assertTrue("message for $reason is blank", msg.isNotBlank())
        }
    }

    @Test
    fun `successful sync after error clears the error`() = runTest {
        loggedIn()
        scraper.outcome = { throw ScrapeException(ScrapeException.Reason.NETWORK, "sin red") }
        runner.run()
        assertNotNull(settings.lastError())
        scraper.outcome = { result }
        runner.run()
        assertNull(settings.lastError())
    }

    @Test
    fun `snapshot is not overwritten on scrape failure`() = runTest {
        loggedIn()
        runner.run() // save initial snapshot
        val saved = snapshots.state.value
        scraper.outcome = { throw ScrapeException(ScrapeException.Reason.NETWORK, "sin red") }
        runner.run()
        assertSame(saved, snapshots.state.value)
    }
}
