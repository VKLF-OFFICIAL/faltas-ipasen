package com.faltasipasen.app.domain

enum class RiskLevel {
    LOW, MEDIUM, HIGH;

    companion object {
        fun of(percent: Double, thresholdPercent: Double): RiskLevel = when {
            percent >= thresholdPercent -> HIGH
            percent >= thresholdPercent / 2 -> MEDIUM
            else -> LOW
        }
    }
}
