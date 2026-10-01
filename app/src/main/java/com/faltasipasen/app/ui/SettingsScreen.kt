package com.faltasipasen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.faltasipasen.app.BuildConfig
import com.faltasipasen.app.data.ThemeMode
import com.faltasipasen.app.domain.EducationLevel
import com.faltasipasen.app.ui.theme.LocalRiskColors
import kotlinx.coroutines.delay
import java.time.LocalDate

private val themeLabels = mapOf(
    ThemeMode.SYSTEM to "Sistema",
    ThemeMode.LIGHT to "Claro",
    ThemeMode.DARK to "Oscuro",
    ThemeMode.AMOLED to "AMOLED",
)

@Composable
fun SettingsScreen(
    state: UiState,
    onThemeChange: (ThemeMode) -> Unit,
    suggestedCutoff: (EducationLevel) -> LocalDate,
    /** Devuelve false si no se pudo guardar la contraseña (el motivo queda en [UiState.lastError]). */
    onSave: (thresholdPercent: Double, cutoff: LocalDate, intervalHours: Int, username: String, newPassword: String, level: EducationLevel) -> Boolean,
    onLogout: () -> Unit,
) {
    var level by rememberSaveable { mutableStateOf(state.educationLevel) }
    var thresholdText by rememberSaveable { mutableStateOf(formatNumber(state.thresholdPercent)) }
    var cutoff by rememberSaveable { mutableStateOf(state.cutoffDate) }
    var interval by rememberSaveable { mutableIntStateOf(state.syncIntervalHours) }
    var username by rememberSaveable { mutableStateOf(state.username) }
    var newPassword by remember { mutableStateOf("") }
    var confirmLogout by remember { mutableStateOf(false) }
    var savedAt by remember { mutableLongStateOf(0L) }
    var saveFailed by remember { mutableStateOf(false) }
    val threshold = InputValidation.parseThreshold(thresholdText)

    LaunchedEffect(savedAt) {
        if (savedAt != 0L) {
            delay(2_500)
            savedAt = 0L
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = "Ajustes", subtitle = null)

        // ── Apariencia ────────────────────────────────────────────────────
        SettingsSection("Apariencia") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.themeMode == mode,
                        onClick = { onThemeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        label = { Text(themeLabels.getValue(mode), maxLines = 1) },
                    )
                }
            }
        }

        // ── Etapa educativa ───────────────────────────────────────────────
        SettingsSection("Etapa educativa") {
            Text(
                "Al cambiar la etapa, el umbral y la fecha de fin pasan a los de la nueva etapa " +
                    "(salvo si ya los habías cambiado tú).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EducationLevelPicker(selected = level, onSelect = { new ->
                val (nextThreshold, nextCutoff) =
                    InputValidation.followLevelDefaults(level, new, thresholdText, cutoff, suggestedCutoff)
                thresholdText = nextThreshold
                cutoff = nextCutoff
                level = new
            })
        }

        // ── Avisos ────────────────────────────────────────────────────────
        SettingsSection("Avisos") {
            OutlinedTextField(
                value = thresholdText,
                onValueChange = { thresholdText = it },
                label = { Text("Avisarme al llegar al (%)") },
                singleLine = true,
                isError = threshold == null,
                supportingText = { if (threshold == null) Text("Escribe un número entre 1 y 100") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            CutoffDateField(
                value = cutoff,
                onChange = { cutoff = it },
                label = level.cutoffFieldLabel,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Sincronización ────────────────────────────────────────────────
        SettingsSection("Sincronización") {
            Text("Comprobar iPasen cada", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputValidation.SYNC_INTERVALS.forEach { hours ->
                    FilterChip(selected = interval == hours, onClick = { interval = hours }, label = { Text("$hours h") })
                }
            }
        }

        // ── Cuenta ────────────────────────────────────────────────────────
        SettingsSection("Cuenta de iPasen") {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Nueva contraseña (vacío = no cambiar)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = {
                saveFailed = !onSave(threshold!!, cutoff, interval, username, newPassword, level)
                if (!saveFailed) {
                    newPassword = ""
                    savedAt = System.currentTimeMillis()
                }
            },
            enabled = threshold != null && username.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (savedAt != 0L) "Cambios guardados" else "Guardar cambios") }
        if (saveFailed) state.lastError?.let { ErrorNote(it) }

        OutlinedButton(
            onClick = { confirmLogout = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalRiskColors.current.high),
        ) { Text("Cerrar sesión y borrar datos") }

        Text(
            "Versión ${BuildConfig.VERSION_NAME} · Datos solo en este móvil",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("¿Borrar todo?") },
            text = { Text("Se borrarán de este móvil tu usuario, tu contraseña y los datos descargados de iPasen.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        FlatCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}
