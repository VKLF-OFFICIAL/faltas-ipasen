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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

private enum class Screen { Home, Settings }

@Composable
private fun AppRoot(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    when {
        !state.hasCredentials -> SetupScreen(
            initialThreshold = state.thresholdPercent,
            initialCutoff = state.cutoffDate,
            onSubmit = { username, password, threshold, cutoff ->
                viewModel.completeSetup(username, password, threshold, cutoff)
                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                screen = Screen.Home
            },
        )

        screen == Screen.Settings -> {
            BackHandler { screen = Screen.Home }
            SettingsScreen(
                state = state,
                onBack = { screen = Screen.Home },
                onSave = { threshold, cutoff, interval, username, newPassword ->
                    viewModel.saveSettings(threshold, cutoff, interval, username, newPassword)
                    screen = Screen.Home
                },
                onLogout = {
                    viewModel.logout()
                    screen = Screen.Home
                },
            )
        }

        else -> HomeScreen(state = state, onSync = viewModel::syncNow, onOpenSettings = { screen = Screen.Settings })
    }
}
