package com.faltasipasen.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.faltasipasen.app.R
import com.faltasipasen.app.domain.AbsenceStats
import com.faltasipasen.app.domain.AlertFormatter
import com.faltasipasen.app.domain.HoursStats
import com.faltasipasen.app.domain.RiskLevel
import com.faltasipasen.app.ui.theme.LocalRiskColors
import com.faltasipasen.app.ui.theme.Tabular
import java.time.LocalDate
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(state: UiState, onSync: () -> Unit, onOpenSubject: (String) -> Unit) {
    PullToRefreshBox(isRefreshing = state.syncing, onRefresh = onSync, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryHeader(state, onSync) }
            state.lastError?.let { error -> item { ErrorNote(error) } }
            val stats = state.stats
            if (stats == null) {
                item { EmptySummary(state.syncing, onSync) }
            } else if (stats.lectivoDaysTotal == 0) {
                item { CutoffMismatchCard(stats.cutoff) }
            } else {
                item { MainPercentCard(stats.global, state.thresholdPercent) }
                item { BreakdownRow(stats.global) }
                item { CourseProgressCard(stats) }
                item { SectionTitle("Módulos") }
                items(stats.subjects, key = { it.label }) { subject ->
                    SubjectRow(subject, state.thresholdPercent, onClick = { onOpenSubject(subject.label) })
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(state: UiState, onSync: () -> Unit) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    val subtitle = state.lastSyncMillis?.let { "Actualizado ${relativeTime(it, now)}" } ?: "Sin sincronizar todavía"
    ScreenHeader(title = "Resumen", subtitle = subtitle) {
        IconButton(onClick = onSync, enabled = !state.syncing) {
            SyncIcon(state.syncing)
        }
    }
}

@Composable
private fun SyncIcon(syncing: Boolean) {
    if (syncing) {
        val rotation by rememberInfiniteTransition(label = "sync").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
            label = "rotation",
        )
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = "Sincronizar ahora",
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    } else {
        Icon(Icons.Rounded.Refresh, contentDescription = "Sincronizar ahora")
    }
}

@Composable
private fun MainPercentCard(global: HoursStats, threshold: Double) {
    val risk = RiskLevel.of(global.percent, threshold)
    val color = LocalRiskColors.current.of(risk)
    val animated by animateFloatAsState(global.percent.toFloat(), tween(700), label = "percent")
    FlatCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                AlertFormatter.percent(animated.toDouble()),
                style = MaterialTheme.typography.displayLarge.merge(Tabular),
                color = color,
            )
            Text("de faltas del curso", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ThinProgress((global.percent / threshold).toFloat(), color, height = 6.dp)
            Row(Modifier.fillMaxWidth()) {
                Text("0 %", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    "Umbral ${AlertFormatter.percent(threshold)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (risk == RiskLevel.HIGH) {
                    "Has superado tu umbral del ${AlertFormatter.percent(threshold)}"
                } else {
                    "Te quedan ${formatHours(global.marginHours(threshold))} de margen"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (risk == RiskLevel.HIGH) color else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BreakdownRow(global: HoursStats) {
    val risk = LocalRiskColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("Injustificadas", formatHours(global.unjustifiedHours), risk.high, Modifier.weight(1f))
        StatCard("Justificadas", formatHours(global.justifiedHours), risk.low, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, dot: Color, modifier: Modifier) {
    FlatCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleLarge.merge(Tabular))
        }
    }
}

@Composable
private fun CourseProgressCard(stats: AbsenceStats) {
    val total = stats.lectivoDaysTotal
    val elapsed = stats.lectivoDaysElapsed
    val fraction = if (total == 0) 0.0 else elapsed.toDouble() / total
    FlatCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Curso transcurrido", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Llevas $elapsed de $total días lectivos (${AlertFormatter.percent(fraction * 100)})",
                style = MaterialTheme.typography.bodyMedium.merge(Tabular),
            )
            ThinProgress(fraction.toFloat(), MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CutoffMismatchCard(cutoff: LocalDate) {
    FlatCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Revisa la fecha de fin de clases", style = MaterialTheme.typography.titleMedium)
            Text(
                "La fecha elegida (${formatLongDate(cutoff)}) no coincide con el curso del calendario de iPasen. " +
                    "Cámbiala en Ajustes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubjectRow(subject: HoursStats, threshold: Double, onClick: () -> Unit) {
    val hasSchedule = subject.courseHours > 0.0
    val color = if (hasSchedule) {
        LocalRiskColors.current.of(RiskLevel.of(subject.percent, threshold))
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    FlatCard(onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(subject.shortLabel, style = MaterialTheme.typography.titleMedium)
                    if (subject.abbreviation.isNotEmpty() && subject.name.isNotEmpty()) {
                        Text(
                            subject.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(AlertFormatter.percent(subject.percent), style = MaterialTheme.typography.titleMedium.merge(Tabular), color = color)
            }
            if (hasSchedule) {
                ThinProgress((subject.percent / threshold).toFloat(), color)
                Text(
                    "${formatHours(subject.absentHours)} de ${formatHours(subject.courseHours)} · margen ${formatHours(subject.marginHours(threshold))}",
                    style = MaterialTheme.typography.labelMedium.merge(Tabular),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "No está en tu horario · ${formatHours(subject.absentHours)} de falta",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorNote(message: String) {
    val color = LocalRiskColors.current.high
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f)).padding(14.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun EmptySummary(syncing: Boolean, onSync: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(size = 72)
        Text(
            if (syncing) "Sincronizando con iPasen…" else "Aún no hay datos de iPasen",
            style = MaterialTheme.typography.titleMedium,
        )
        if (!syncing) Button(onClick = onSync) { Text("Sincronizar") }
    }
}

/** Icono de la app dibujado con Compose (painterResource no admite iconos adaptativos). */
@Composable
fun AppIcon(size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.28f).dp)).background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size((size * 1.5f).dp))
    }
}
