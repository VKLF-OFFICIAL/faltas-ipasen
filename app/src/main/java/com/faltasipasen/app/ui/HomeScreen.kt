package com.faltasipasen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.faltasipasen.app.domain.AlertFormatter
import com.faltasipasen.app.domain.HoursStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: UiState, onSync: () -> Unit, onOpenSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Faltas iPasen") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Ajustes") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryCard(state) }
            item { SyncSection(state, onSync) }
            val subjects = state.stats?.subjects.orEmpty()
            if (subjects.isNotEmpty()) {
                item { Text("Por asignatura", style = MaterialTheme.typography.titleMedium) }
                items(subjects, key = { it.label }) { SubjectCard(it, state.thresholdPercent) }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: UiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val stats = state.stats
            if (stats == null) {
                Text("Aún no hay datos", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.syncing) "Leyendo tus datos de iPasen…" else "Pulsa «Sincronizar ahora» para leerlos de iPasen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }
            val global = stats.global
            val over = global.percent >= state.thresholdPercent
            Text("Faltas sobre el total del curso", style = MaterialTheme.typography.labelLarge)
            Text(
                AlertFormatter.percent(global.percent),
                style = MaterialTheme.typography.displayMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            PercentBar(global.percent, state.thresholdPercent)
            Text(
                "${formatHours(global.absentHours)} de falta de ${formatHours(global.courseHours)} lectivas " +
                    "hasta el ${formatLongDate(stats.cutoff)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (over) {
                    "Has superado el aviso del ${AlertFormatter.percent(state.thresholdPercent)}"
                } else {
                    "Margen: ${formatHours(global.marginHours(state.thresholdPercent))} más antes del " +
                        AlertFormatter.percent(state.thresholdPercent)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Injustificadas: ${AlertFormatter.percent(global.unjustifiedPercent)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SyncSection(state: UiState, onSync: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSync, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
            if (state.syncing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Sincronizando…")
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sincronizar ahora")
            }
        }
        state.lastSyncMillis?.let {
            Text("Última actualización: ${formatMillis(it)}", style = MaterialTheme.typography.bodySmall)
        }
        state.lastError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SubjectCard(stats: HoursStats, thresholdPercent: Double) {
    val over = stats.courseHours > 0.0 && stats.percent >= thresholdPercent
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stats.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    AlertFormatter.percent(stats.percent),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (stats.courseHours > 0.0) {
                PercentBar(stats.percent, thresholdPercent)
                Text(
                    "${formatHours(stats.absentHours)} de ${formatHours(stats.courseHours)} · " +
                        "margen ${formatHours(stats.marginHours(thresholdPercent))}",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "${formatHours(stats.absentHours)} de falta. No aparece en tu horario, así que no se puede calcular su %.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
