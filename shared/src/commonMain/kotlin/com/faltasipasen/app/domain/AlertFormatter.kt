package com.faltasipasen.app.domain

import kotlin.math.abs
import kotlin.math.floor

object AlertFormatter {
    /** "16,2 %": coma decimal española y un decimal, redondeando la mitad hacia arriba como String.format. */
    fun percent(value: Double): String {
        val tenths = floor(abs(value) * 10 + 0.5).toLong()
        val sign = if (value < 0 && tenths != 0L) "-" else ""
        return "$sign${tenths / 10},${tenths % 10} %"
    }

    fun thresholdTitle(thresholdPercent: Double): String = "Has superado el ${percent(thresholdPercent)} de faltas"

    fun thresholdBody(alerts: List<ThresholdAlert>): String =
        alerts.joinToString(" · ") { "${it.label}: ${percent(it.percent)}" }
}
