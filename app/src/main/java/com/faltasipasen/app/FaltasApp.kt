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
