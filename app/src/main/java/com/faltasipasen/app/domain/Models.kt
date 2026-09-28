package com.faltasipasen.app.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Una sesión semanal del horario. dayOfWeek: 1 = lunes … 7 = domingo.
 * [subject] es la clave normalizada para cruzar horario y faltas; [name] y [abbreviation] son para mostrar.
 */
@Serializable
data class ScheduleSlot(
    val subject: String,
    val dayOfWeek: Int,
    val hours: Double,
    val abbreviation: String = "",
    val name: String = "",
)

@Serializable
data class CalendarDay(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val lectivo: Boolean,
)

@Serializable
data class Absence(
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val subject: String,
    val hours: Double,
    val justified: Boolean,
    val tramo: String = "",
    val name: String = "",
)

data class Credentials(val username: String, val password: String)

data class HoursStats(
    val label: String,
    val courseHours: Double,
    val absentHours: Double,
    val unjustifiedHours: Double,
    val abbreviation: String = "",
    val name: String = "",
) {
    val percent: Double get() = percentOf(absentHours)
    val unjustifiedPercent: Double get() = percentOf(unjustifiedHours)
    val justifiedHours: Double get() = absentHours - unjustifiedHours
    val shortLabel: String get() = abbreviation.ifEmpty { name.ifEmpty { label } }

    fun marginHours(thresholdPercent: Double): Double =
        (thresholdPercent / 100.0 * courseHours - absentHours).coerceAtLeast(0.0)

    private fun percentOf(hours: Double) = if (courseHours <= 0.0) 0.0 else hours / courseHours * 100.0
}

data class AbsenceStats(
    val global: HoursStats,
    val subjects: List<HoursStats>,
    val courseStart: LocalDate,
    val cutoff: LocalDate,
    val countedUntil: LocalDate,
    val lectivoDaysElapsed: Int = 0,
    val lectivoDaysTotal: Int = 0,
)
