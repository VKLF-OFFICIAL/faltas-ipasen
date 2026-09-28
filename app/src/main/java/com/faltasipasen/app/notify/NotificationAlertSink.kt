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
import com.faltasipasen.app.R
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
            .setSmallIcon(R.drawable.ic_stat_faltas)
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
