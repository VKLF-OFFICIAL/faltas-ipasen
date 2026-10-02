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
import kotlinx.coroutines.CancellationException
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
    /** Las credenciales cambiaron durante la descarga; el resultado se descarta. */
    data object Superseded : SyncOutcome
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
        try {
            val credentials = settings.credentials() ?: return@withLock SyncOutcome.MissingCredentials
            // Reintentar con una contraseña ya rechazada puede bloquear la cuenta en Séneca.
            if (settings.credentialsRejected()) {
                return@withLock SyncOutcome.Failed(Reason.LOGIN_FAILED, settings.lastError() ?: REJECTED_MESSAGE)
            }

            val result = try {
                scraper.scrape(credentials).also(::validate)
            } catch (e: ScrapeException) {
                return@withLock fail(e)
            }

            // Se cerró sesión o se cambiaron las credenciales mientras se descargaba: los datos ya no valen.
            if (settings.credentials() != credentials) return@withLock SyncOutcome.Superseded

            val snapshot = Snapshot(result.schedule, result.calendar, result.absences, nowMillis())
            snapshots.save(snapshot)

            val stats = AbsenceCalculator.calculate(
                snapshot.schedule, snapshot.calendar, snapshot.absences, settings.cutoffDate(), today(),
            )
            if (stats.lectivoDaysTotal == 0) {
                // El resumen ya lo avisa en vivo con la fecha actual; guardarlo aquí dejaría un aviso
                // obsoleto en pantalla después de corregir la fecha en Ajustes.
                settings.setLastError(null)
                val message = "La fecha de fin de curso (${stats.cutoff.format(cutoffDateFormat)}) " +
                    "no cae en el curso que muestra iPasen. Revísala en Ajustes."
                return@withLock SyncOutcome.Failed(Reason.PARSE_ERROR, message)
            }
            val threshold = settings.thresholdPercent()
            val evaluation = ThresholdEvaluator.evaluate(stats, threshold, settings.activeAlertKeys())
            if (evaluation.newAlerts.isNotEmpty()) alerts.showThresholdAlerts(threshold, evaluation.newAlerts)
            settings.setActiveAlertKeys(evaluation.activeKeys)
            settings.setLastError(null)
            return@withLock SyncOutcome.Success(stats, evaluation.newAlerts)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            return@withLock fail(ScrapeException(Reason.INTERNAL, detail))
        }
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
        if (e.reason == Reason.LOGIN_FAILED) {
            if (settings.lastError() != message) alerts.showSyncProblem(message)
            settings.setCredentialsRejected(true)
        }
        settings.setLastError(message)
        return SyncOutcome.Failed(e.reason, message)
    }

    companion object {
        private const val REJECTED_MESSAGE =
            "iPasen ha rechazado el usuario o la contraseña. Revísalos en Ajustes y guárdalos de nuevo."

        fun userMessage(e: ScrapeException): String = when (e.reason) {
            Reason.LOGIN_FAILED -> {
                // Séneca explica el motivo (clave incorrecta, usuario bloqueado…): se muestra tal cual.
                val detail = e.message?.removePrefix("login:")?.trim().orEmpty()
                if (detail.isEmpty()) REJECTED_MESSAGE else "$REJECTED_MESSAGE (Séneca: «$detail»)"
            }
            Reason.NETWORK -> "No se pudo conectar con iPasen (${e.message}). Se volverá a intentar."
            Reason.TIMEOUT -> "iPasen tardó demasiado en responder (${e.message}). Se volverá a intentar."
            Reason.PARSE_ERROR -> "No se pudieron leer los datos de iPasen (${e.message}). Puede que hayan cambiado la web."
            Reason.INTERNAL -> "Error interno de la app (${e.message}). Se volverá a intentar."
        }
    }
}
