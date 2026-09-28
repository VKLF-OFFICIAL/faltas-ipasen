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
import java.time.format.DateTimeFormatter

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
    private val cutoffDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

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
        if (stats.lectivoDaysTotal == 0) {
            val message = "La fecha de fin de clases (${stats.cutoff.format(cutoffDateFormat)}) " +
                "no cae en el curso que muestra iPasen. Revísala en Ajustes."
            settings.setLastError(message)
            return@withLock SyncOutcome.Failed(Reason.PARSE_ERROR, message)
        }
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
