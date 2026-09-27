package com.faltasipasen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    initialThreshold: Double,
    initialCutoff: LocalDate,
    onSubmit: (username: String, password: String, thresholdPercent: Double, cutoff: LocalDate) -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var thresholdText by rememberSaveable { mutableStateOf(formatNumber(initialThreshold)) }
    var cutoff by rememberSaveable { mutableStateOf(initialCutoff) }
    val threshold = InputValidation.parseThreshold(thresholdText)

    Scaffold(topBar = { TopAppBar(title = { Text("Faltas iPasen") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Entra con tu usuario de iPasen. La app leerá tu horario, tu calendario y tus faltas " +
                    "automáticamente. Tus datos solo se guardan en este móvil, cifrados.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario de iPasen") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Las faltas cuentan hasta el fin de las clases (inicio de la FCT). Comprueba la fecha:",
                style = MaterialTheme.typography.bodyMedium,
            )
            CutoffDateField(value = cutoff, onChange = { cutoff = it }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = thresholdText,
                onValueChange = { thresholdText = it },
                label = { Text("Avisarme al llegar al (%)") },
                singleLine = true,
                isError = threshold == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSubmit(username, password, threshold!!, cutoff) },
                enabled = threshold != null && InputValidation.credentialsValid(username, password),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Empezar") }
        }
    }
}
