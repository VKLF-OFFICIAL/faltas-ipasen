package com.faltasipasen.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.faltasipasen.app.domain.HistoryBuilder
import com.faltasipasen.app.domain.HistoryDay
import com.faltasipasen.app.domain.HistoryEntry
import com.faltasipasen.app.ui.theme.LocalRiskColors
import com.faltasipasen.app.ui.theme.Tabular

@Composable
fun HistoryScreen(history: List<HistoryDay>, filter: String?, onFilterChange: (String?) -> Unit) {
    val options = remember(history) { HistoryBuilder.filterOptions(history) }
    val days = remember(history, filter) { HistoryBuilder.filter(history, filter) }
    val entries = days.flatMap { it.entries }
    val count = entries.size
    val subtitle = "${if (count == 1) "1 falta" else "$count faltas"} · ${formatHours(entries.sumOf { it.hours })}"

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 20.dp)) { ScreenHeader(title = "Historial", subtitle = subtitle) }
        if (options.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = filter == null, onClick = { onFilterChange(null) }, label = { Text("Todos") }) }
                items(options, key = { it.key }) { option ->
                    FilterChip(
                        selected = filter == option.key,
                        onClick = { onFilterChange(option.key) },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        if (days.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (filter == null) "Sin faltas registradas. ¡Sigue así!" else "Sin faltas en este módulo.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                days.forEach { day ->
                    item(key = "day-${day.date}") {
                        Text(
                            formatDayHeader(day.date),
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(day.entries, key = { "${it.date}-${it.tramo}-${it.subjectKey}" }) { HistoryRow(it) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    val risk = LocalRiskColors.current
    FlatCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (entry.tramo.isNotEmpty()) {
                    Text(
                        formatTramo(entry.tramo),
                        style = MaterialTheme.typography.labelMedium.merge(Tabular),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(entry.label, style = MaterialTheme.typography.titleSmall)
                if (entry.abbreviation.isNotEmpty() && entry.name.isNotEmpty()) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (entry.justified) StatusChip("Justificada", risk.low) else StatusChip("Injustificada", risk.high)
                Text(formatHours(entry.hours), style = MaterialTheme.typography.labelMedium.merge(Tabular))
            }
        }
    }
}
