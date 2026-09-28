package com.faltasipasen.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.faltasipasen.app.ui.theme.FaltasTheme
import com.faltasipasen.app.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val dark = state.themeMode.isDark(isSystemInDarkTheme())
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                )
            }
            FaltasTheme(state.themeMode) {
                Surface(color = MaterialTheme.colorScheme.background) { AppRoot(viewModel) }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Summary("Resumen", Icons.Rounded.Home),
    History("Historial", Icons.AutoMirrored.Rounded.List),
    Settings("Ajustes", Icons.Rounded.Settings),
}

@Composable
private fun AppRoot(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Summary) }
    var historyFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    if (!state.hasCredentials) {
        SetupScreen(
            initialThreshold = state.thresholdPercent,
            initialCutoff = state.cutoffDate,
            onSubmit = { username, password, threshold, cutoff ->
                viewModel.completeSetup(username, password, threshold, cutoff)
                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                tab = Tab.Summary
            },
        )
        return
    }

    BackHandler(enabled = tab != Tab.Summary) { tab = Tab.Summary }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { if (item == Tab.History) historyFilter = null; tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Crossfade(targetState = tab, animationSpec = tween(220), label = "tab", modifier = Modifier.padding(padding)) { current ->
            when (current) {
                Tab.Summary -> SummaryScreen(
                    state,
                    onSync = viewModel::syncNow,
                    onOpenSubject = { key -> historyFilter = key; tab = Tab.History },
                )
                Tab.History -> HistoryScreen(state.history, historyFilter, onFilterChange = { historyFilter = it })
                Tab.Settings -> SettingsScreen(
                    state = state,
                    onBack = { tab = Tab.Summary },
                    onSave = { threshold, cutoff, interval, username, newPassword ->
                        viewModel.saveSettings(threshold, cutoff, interval, username, newPassword)
                        tab = Tab.Summary
                    },
                    onLogout = {
                        viewModel.logout()
                        historyFilter = null
                        tab = Tab.Summary
                    },
                )
            }
        }
    }
}
