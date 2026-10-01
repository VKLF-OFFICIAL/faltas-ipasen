package com.faltasipasen.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.faltasipasen.app.domain.EducationLevel
import java.time.LocalDate

@Composable
fun WelcomeScreen(
    initialLevel: EducationLevel,
    suggestedCutoff: (EducationLevel) -> LocalDate,
    notices: List<String>,
    onSubmit: (username: String, password: String, thresholdPercent: Double, cutoff: LocalDate, level: EducationLevel) -> Unit,
) {
    var level by rememberSaveable { mutableStateOf(initialLevel) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var thresholdText by rememberSaveable { mutableStateOf(formatNumber(initialLevel.defaultThreshold)) }
    var cutoff by rememberSaveable { mutableStateOf(suggestedCutoff(initialLevel)) }
    val selectLevel: (EducationLevel) -> Unit = { new ->
        val (nextThreshold, nextCutoff) =
            InputValidation.followLevelDefaults(level, new, thresholdText, cutoff, suggestedCutoff)
        thresholdText = nextThreshold
        cutoff = nextCutoff
        level = new
    }

    val threshold = InputValidation.parseThreshold(thresholdText)

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        AppIcon(size = 80)
        Text("Faltas iPasen", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Tus faltas de iPasen, bajo control",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        notices.forEach { ErrorNote(it) }

        // ── Selector de etapa ──────────────────────────────────────────────
        Text(
            "¿En qué etapa estudias?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
        EducationLevelPicker(selected = level, onSelect = selectLevel)

        // ── Cuenta iPasen ─────────────────────────────────────────────────
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

        // ── Fecha de fin ──────────────────────────────────────────────────
        Text(
            level.cutoffHelpText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        CutoffDateField(
            value = cutoff,
            onChange = { cutoff = it },
            label = level.cutoffFieldLabel,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── Umbral ────────────────────────────────────────────────────────
        OutlinedTextField(
            value = thresholdText,
            onValueChange = { thresholdText = it },
            label = { Text("Avisarme al llegar al (%) — por defecto ${formatNumber(level.defaultThreshold)} %") },
            singleLine = true,
            isError = threshold == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { onSubmit(username, password, threshold!!, cutoff, level) },
            enabled = threshold != null && InputValidation.credentialsValid(username, password),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text("Empezar") }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                "Tu usuario y contraseña se guardan cifrados solo en este móvil.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Cuadrícula de chips para elegir la etapa educativa.
 *
 * - Fila 1: etapas generales (Primaria, ESO, Bachillerato).
 * - Fila 2: etapas FP (Grado Básico, Grado Medio, Grado Superior).
 * - Sub-pregunta: cuando se elige "Grado Básico" aparece una fila adicional
 *   preguntando si incluye FCT, discriminando entre [EducationLevel.FP_BASICO]
 *   y [EducationLevel.FP_BASICO_FCT].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EducationLevelPicker(selected: EducationLevel, onSelect: (EducationLevel) -> Unit) {
    val nonFp = listOf(EducationLevel.PRIMARIA, EducationLevel.ESO, EducationLevel.BACHILLERATO)
    // FP_BASICO_FCT no aparece como chip propio; se selecciona via sub-pregunta
    val fp = listOf(EducationLevel.FP_BASICO, EducationLevel.FP_MEDIO, EducationLevel.FP_SUPERIOR)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Fila 1: etapas generales. FlowRow: en pantallas estrechas los chips bajan de línea en
        // vez de quedar cortados fuera de la tarjeta.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            nonFp.forEach { lvl ->
                FilterChip(
                    selected = selected == lvl,
                    onClick = { onSelect(lvl) },
                    label = { Text(lvl.chipLabel) },
                )
            }
        }

        // Fila 2: FP — el chip de Grado Básico se activa para ambas variantes
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fp.forEach { lvl ->
                FilterChip(
                    selected = if (lvl == EducationLevel.FP_BASICO) selected.isFpBasico else selected == lvl,
                    onClick = {
                        // Al pulsar Grado Básico, seleccionar la variante sin FCT por defecto
                        onSelect(if (lvl == EducationLevel.FP_BASICO) EducationLevel.FP_BASICO else lvl)
                    },
                    label = { Text(lvl.chipLabel) },
                )
            }
        }

        // Sub-pregunta de FCT — solo visible cuando se ha elegido cualquier variante de Grado Básico
        if (selected.isFpBasico) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "¿Tu Grado Básico incluye prácticas en empresa (FCT)?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selected == EducationLevel.FP_BASICO,
                        onClick = { onSelect(EducationLevel.FP_BASICO) },
                        label = { Text("No") },
                    )
                    FilterChip(
                        selected = selected == EducationLevel.FP_BASICO_FCT,
                        onClick = { onSelect(EducationLevel.FP_BASICO_FCT) },
                        label = { Text("Sí") },
                    )
                }
                // Explicación contextual según la respuesta
                Text(
                    text = if (selected == EducationLevel.FP_BASICO_FCT) {
                        "Las faltas se contarán hasta el inicio de la FCT. La app te pedirá la fecha de comienzo."
                    } else {
                        "Las faltas se contarán hasta el final del curso (sin FCT)."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
