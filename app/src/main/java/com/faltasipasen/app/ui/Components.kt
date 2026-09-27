package com.faltasipasen.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish: Locale = Locale.forLanguageTag("es-ES")
private val madrid: ZoneId = ZoneId.of("Europe/Madrid")
private val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", spanish)
private val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", spanish)

fun formatNumber(value: Double): String = DecimalFormat("0.#", DecimalFormatSymbols(spanish)).format(value)

fun formatHours(value: Double): String = "${formatNumber(value)} h"

fun formatLongDate(date: LocalDate): String = date.format(longDate)

fun formatMillis(millis: Long): String = Instant.ofEpochMilli(millis).atZone(madrid).format(dateTime)

@Composable
fun PercentBar(percent: Double, thresholdPercent: Double, modifier: Modifier = Modifier) {
    val over = percent >= thresholdPercent
    LinearProgressIndicator(
        progress = { (percent / thresholdPercent).toFloat().coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth(),
        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CutoffDateField(value: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text("Fin de clases (inicio FCT): ${formatLongDate(value)}")
    }
    if (open) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
