package com.faltasipasen.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.faltasipasen.app.FaltasApp
import com.faltasipasen.app.data.Snapshot
import com.faltasipasen.app.data.ThemeMode
import com.faltasipasen.app.domain.AbsenceCalculator
import com.faltasipasen.app.domain.AbsenceStats
import com.faltasipasen.app.domain.CourseDates
import com.faltasipasen.app.domain.Credentials
import com.faltasipasen.app.domain.EducationLevel
import com.faltasipasen.app.domain.HistoryBuilder
import com.faltasipasen.app.domain.HistoryDay
import com.faltasipasen.app.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class UiState(
    val hasCredentials: Boolean,
    val username: String,
    val stats: AbsenceStats?,
    val history: List<HistoryDay>,
    val thresholdPercent: Double,
    val cutoffDate: LocalDate,
    val syncIntervalHours: Int,
    val lastSyncMillis: Long?,
    val lastError: String?,
    val skippedAbsences: List<String>,
    val syncing: Boolean,
    val pendingSync: Boolean,
    val themeMode: ThemeMode,
    val educationLevel: EducationLevel,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as FaltasApp).graph
    private val workManager = WorkManager.getInstance(application)

    private data class WorkStatus(val running: Boolean, val pending: Boolean)

    private val workStatus: Flow<WorkStatus> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(SyncScheduler.NOW_WORK),
        workManager.getWorkInfosForUniqueWorkFlow(SyncScheduler.PERIODIC_WORK),
    ) { now, periodic ->
        WorkStatus(
            running = (now + periodic).any { it.state == WorkInfo.State.RUNNING },
            // Sincronización pedida a mano que espera conexión o su reintento.
            pending = now.any { it.state == WorkInfo.State.ENQUEUED },
        )
    }

    // El cálculo (y descifrar las credenciales con el Keystore) va fuera del hilo principal.
    val state: StateFlow<UiState> =
        combine(graph.snapshots.current, graph.settings.changes, workStatus) { snapshot, _, work ->
            buildState(snapshot, work)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), buildState(graph.snapshots.current.value, IDLE))

    private fun buildState(snapshot: Snapshot?, work: WorkStatus): UiState {
        val settings = graph.settings
        val credentials = settings.credentials()
        val stats = snapshot?.let {
            AbsenceCalculator.calculate(it.schedule, it.calendar, it.absences, settings.cutoffDate(), graph.today())
        }
        val history = snapshot?.let {
            HistoryBuilder.build(it.schedule, it.absences, settings.cutoffDate(), graph.today())
        }.orEmpty()
        return UiState(
            hasCredentials = credentials != null,
            username = credentials?.username.orEmpty(),
            stats = stats,
            history = history,
            thresholdPercent = settings.thresholdPercent(),
            cutoffDate = settings.cutoffDate(),
            syncIntervalHours = settings.syncIntervalHours(),
            lastSyncMillis = snapshot?.syncedAtMillis,
            lastError = settings.lastError(),
            skippedAbsences = snapshot?.skippedAbsences.orEmpty(),
            syncing = work.running,
            pendingSync = work.pending && !work.running,
            themeMode = settings.themeMode(),
            educationLevel = settings.educationLevel(),
        )
    }

    /** Fecha de fin sugerida para [level] en el curso actual. */
    fun defaultCutoff(level: EducationLevel): LocalDate = CourseDates.defaultCutoff(graph.today(), level)

    fun completeSetup(
        username: String,
        password: String,
        thresholdPercent: Double,
        cutoff: LocalDate,
        level: EducationLevel,
    ): Boolean {
        val settings = graph.settings
        settings.setEducationLevel(level)
        settings.setThresholdPercent(thresholdPercent)
        settings.setCutoffDate(cutoff)
        if (!saveCredentials(Credentials(username.trim(), password))) return false
        SyncScheduler.schedulePeriodic(getApplication(), settings.syncIntervalHours())
        SyncScheduler.syncNow(getApplication())
        return true
    }

    /** El Keystore puede fallar en algunos móviles; mejor un aviso que cerrar la app. */
    private fun saveCredentials(credentials: Credentials): Boolean =
        try {
            graph.settings.saveCredentials(credentials)
            true
        } catch (e: Exception) {
            graph.settings.setLastError("$KEYSTORE_ERROR (${e.message ?: e.javaClass.simpleName}). Inténtalo de nuevo.")
            false
        }

    fun saveSettings(
        thresholdPercent: Double,
        cutoff: LocalDate,
        intervalHours: Int,
        username: String,
        newPassword: String,
        level: EducationLevel,
    ): Boolean {
        val settings = graph.settings
        settings.setEducationLevel(level)
        settings.setThresholdPercent(thresholdPercent)
        settings.setCutoffDate(cutoff)
        settings.setSyncIntervalHours(intervalHours)
        SyncScheduler.schedulePeriodic(getApplication(), intervalHours)

        val current = settings.credentials() ?: return true
        val newUsername = username.trim()
        if (newUsername != current.username || newPassword.isNotEmpty()) {
            if (!saveCredentials(Credentials(newUsername, newPassword.ifEmpty { current.password }))) return false
            SyncScheduler.syncNow(getApplication())
        }
        return true
    }

    fun syncNow() = SyncScheduler.syncNow(getApplication())

    fun setThemeMode(mode: ThemeMode) = graph.settings.setThemeMode(mode)

    fun logout() {
        SyncScheduler.cancelAll(getApplication())
        graph.settings.clearAll()
        graph.snapshots.clear()
    }

    companion object {
        const val KEYSTORE_ERROR = "No se pudo guardar la contraseña de forma segura en este móvil"
        private val IDLE = WorkStatus(running = false, pending = false)
    }
}
