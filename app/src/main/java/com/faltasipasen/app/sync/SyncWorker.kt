package com.faltasipasen.app.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.faltasipasen.app.FaltasApp
import com.faltasipasen.app.R
import com.faltasipasen.app.notify.NotificationAlertSink
import com.faltasipasen.app.scraper.ScrapeException.Reason
import kotlinx.coroutines.CancellationException

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val graph = (applicationContext as FaltasApp).graph
        val outcome = graph.syncRunner.run()
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
            .setSmallIcon(R.drawable.ic_stat_faltas)
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
