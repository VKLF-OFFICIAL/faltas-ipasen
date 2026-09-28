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
import com.faltasipasen.app.domain.Credentials
import com.faltasipasen.app.domain.HistoryBuilder
import com.faltasipasen.app.domain.HistoryDay
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
    val history: List<HistoryDay>,
    val thresholdPercent: Double,
    val cutoffDate: LocalDate,
    val syncIntervalHours: Int,
    val lastSyncMillis: Long?,
    val lastError: String?,
    val syncing: Boolean,
    val themeMode: ThemeMode,
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
            syncing = running,
            themeMode = settings.themeMode(),
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

    fun setThemeMode(mode: ThemeMode) = graph.settings.setThemeMode(mode)

    fun logout() {
        SyncScheduler.cancelAll(getApplication())
        graph.settings.clearAll()
        graph.snapshots.clear()
    }
}
