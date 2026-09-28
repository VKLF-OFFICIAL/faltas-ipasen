package com.faltasipasen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onSave: (thresholdPercent: Double, cutoff: LocalDate, intervalHours: Int, username: String, newPassword: String) -> Unit,
    onLogout: () -> Unit,
) {
    var thresholdText by rememberSaveable { mutableStateOf(formatNumber(state.thresholdPercent)) }
    var cutoff by rememberSaveable { mutableStateOf(state.cutoffDate) }
    var interval by rememberSaveable { mutableIntStateOf(state.syncIntervalHours) }
    var username by rememberSaveable { mutableStateOf(state.username) }
    var newPassword by remember { mutableStateOf("") }
    var confirmLogout by remember { mutableStateOf(false) }
    val threshold = InputValidation.parseThreshold(thresholdText)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = thresholdText,
                onValueChange = { thresholdText = it },
                label = { Text("Avisarme al llegar al (%)") },
                singleLine = true,
                isError = threshold == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            CutoffDateField(value = cutoff, onChange = { cutoff = it }, modifier = Modifier.fillMaxWidth())
            Text("Comprobar iPasen cada", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputValidation.SYNC_INTERVALS.forEach { hours ->
                    FilterChip(selected = interval == hours, onClick = { interval = hours }, label = { Text("$hours h") })
                }
            }
            HorizontalDivider()
            Text("Cuenta de iPasen", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario de iPasen") },
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
            Button(
                onClick = { onSave(threshold!!, cutoff, interval, username, newPassword) },
                enabled = threshold != null && username.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }
            OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar sesión y borrar datos")
            }
        }
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
