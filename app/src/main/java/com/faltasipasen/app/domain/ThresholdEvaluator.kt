package com.faltasipasen.app.domain

data class ThresholdAlert(val key: String, val label: String, val percent: Double)

data class ThresholdEvaluation(val newAlerts: List<ThresholdAlert>, val activeKeys: Set<String>)

object ThresholdEvaluator {
    const val GLOBAL_KEY = "__total__"

    fun evaluate(stats: AbsenceStats, thresholdPercent: Double, previouslyActive: Set<String>): ThresholdEvaluation {
        val over = buildList {
            if (stats.global.courseHours > 0.0 && stats.global.percent >= thresholdPercent) {
                add(ThresholdAlert(GLOBAL_KEY, stats.global.label, stats.global.percent))
            }
            stats.subjects
                .filter { it.courseHours > 0.0 && it.percent >= thresholdPercent }
                .forEach { add(ThresholdAlert(it.label, it.label, it.percent)) }
        }
        return ThresholdEvaluation(
            newAlerts = over.filter { it.key !in previouslyActive },
            activeKeys = over.map { it.key }.toSet(),
        )
    }
}
