package com.faltasipasen.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val LightColors = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E3FF),
    onPrimaryContainer = Color(0xFF0A1570),
    secondary = Color(0xFF3949AB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E3FF),
    onSecondaryContainer = Color(0xFF0A1570),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF16181F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16181F),
    surfaceVariant = Color(0xFFEEF0F7),
    onSurfaceVariant = Color(0xFF5A5F6E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4F9),
    surfaceContainer = Color(0xFFEEF0F7),
    surfaceContainerHigh = Color(0xFFE8EAF2),
    surfaceContainerHighest = Color(0xFFE2E5EE),
    outline = Color(0xFFD7DAE5),
    outlineVariant = Color(0xFFE6E8F0),
    error = Color(0xFFD32F2F),
    onError = Color(0xFFFFFFFF),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C3FF),
    onPrimary = Color(0xFF0E1D78),
    primaryContainer = Color(0xFF2C3A8F),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFB8C3FF),
    onSecondary = Color(0xFF0E1D78),
    secondaryContainer = Color(0xFF2C3A8F),
    onSecondaryContainer = Color(0xFFDDE1FF),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E4EA),
    surface = Color(0xFF1A1C22),
    onSurface = Color(0xFFE3E4EA),
    surfaceVariant = Color(0xFF23262E),
    onSurfaceVariant = Color(0xFFA7ABB8),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF16181D),
    surfaceContainer = Color(0xFF1E2027),
    surfaceContainerHigh = Color(0xFF23262E),
    surfaceContainerHighest = Color(0xFF2A2D36),
    outline = Color(0xFF3A3E49),
    outlineVariant = Color(0xFF2C2F38),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0A0A),
)

val AmoledColors = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF0B0C0F),
    surfaceVariant = Color(0xFF16181D),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF07080A),
    surfaceContainer = Color(0xFF0F1013),
    surfaceContainerHigh = Color(0xFF16181D),
    surfaceContainerHighest = Color(0xFF1D1F25),
    outlineVariant = Color(0xFF1F2127),
)
