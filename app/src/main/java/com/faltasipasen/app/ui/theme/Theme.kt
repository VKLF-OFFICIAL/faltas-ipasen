package com.faltasipasen.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.faltasipasen.app.data.ThemeMode
import com.faltasipasen.app.domain.RiskLevel

@Immutable
data class RiskColors(val low: Color, val medium: Color, val high: Color) {
    fun of(level: RiskLevel): Color = when (level) {
        RiskLevel.LOW -> low
        RiskLevel.MEDIUM -> medium
        RiskLevel.HIGH -> high
    }
}

private val LightRisk = RiskColors(low = Color(0xFF1E8E5A), medium = Color(0xFFC77700), high = Color(0xFFD32F2F))
private val DarkRisk = RiskColors(low = Color(0xFF5FD39A), medium = Color(0xFFFFB547), high = Color(0xFFFF6B6B))

val LocalRiskColors = staticCompositionLocalOf { LightRisk }

fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
}

@Composable
fun FaltasTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val target = when {
        mode == ThemeMode.AMOLED -> AmoledColors
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalRiskColors provides if (dark) DarkRisk else LightRisk) {
        MaterialTheme(
            colorScheme = animated(target),
            typography = FaltasTypography,
            shapes = FaltasShapes,
            content = content,
        )
    }
}

@Composable
private fun animated(target: ColorScheme): ColorScheme {
    @Composable
    fun Color.anim(): Color = animateColorAsState(this, tween(300), label = "theme").value
    return target.copy(
        primary = target.primary.anim(),
        onPrimary = target.onPrimary.anim(),
        primaryContainer = target.primaryContainer.anim(),
        onPrimaryContainer = target.onPrimaryContainer.anim(),
        secondaryContainer = target.secondaryContainer.anim(),
        onSecondaryContainer = target.onSecondaryContainer.anim(),
        background = target.background.anim(),
        onBackground = target.onBackground.anim(),
        surface = target.surface.anim(),
        onSurface = target.onSurface.anim(),
        surfaceVariant = target.surfaceVariant.anim(),
        onSurfaceVariant = target.onSurfaceVariant.anim(),
        surfaceContainer = target.surfaceContainer.anim(),
        surfaceContainerHigh = target.surfaceContainerHigh.anim(),
        surfaceContainerHighest = target.surfaceContainerHighest.anim(),
        outline = target.outline.anim(),
        outlineVariant = target.outlineVariant.anim(),
    )
}
