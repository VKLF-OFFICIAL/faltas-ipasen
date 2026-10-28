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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.ZoneId

class AppGraph(context: Context) {
    private val app = context.applicationContext
    private val zone: ZoneId = ZoneId.of("Europe/Madrid")

    val today: () -> LocalDate = { java.time.LocalDate.now(zone).toKotlinLocalDate() }

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
