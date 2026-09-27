package com.faltasipasen.app.domain

import java.util.Locale

object AlertFormatter {
    private val spanish: Locale = Locale.forLanguageTag("es-ES")

    fun percent(value: Double): String = String.format(spanish, "%.1f %%", value)

    fun thresholdTitle(thresholdPercent: Double): String = "Has superado el ${percent(thresholdPercent)} de faltas"

    fun thresholdBody(alerts: List<ThresholdAlert>): String =
        alerts.joinToString(" · ") { "${it.label}: ${percent(it.percent)}" }
}
