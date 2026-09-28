package com.faltasipasen.app.domain

import java.time.LocalDate

data class HistoryEntry(
    val date: LocalDate,
    val tramo: String,
    val subjectKey: String,
    val abbreviation: String,
    val name: String,
    val hours: Double,
    val justified: Boolean,
) {
    val label: String get() = abbreviation.ifEmpty { name.ifEmpty { subjectKey } }
}

data class HistoryDay(val date: LocalDate, val entries: List<HistoryEntry>)

data class HistoryFilterOption(val key: String, val label: String)

object HistoryBuilder {
    private val tramoStart = Regex("(\\d{1,2}):(\\d{2})")

    fun build(schedule: List<ScheduleSlot>, absences: List<Absence>, cutoff: LocalDate, today: LocalDate): List<HistoryDay> {
        val courseStart = CourseDates.courseStartFor(cutoff)
        val until = minOf(today, cutoff)
        val bySubject = schedule.groupBy { it.subject }
        return absences
            .filter { !it.date.isBefore(courseStart) && !it.date.isAfter(until) }
            .map { absence ->
                val slots = bySubject[absence.subject].orEmpty()
                HistoryEntry(
                    date = absence.date,
                    tramo = absence.tramo,
                    subjectKey = absence.subject,
                    abbreviation = slots.firstOrNull { it.abbreviation.isNotEmpty() }?.abbreviation.orEmpty(),
                    name = absence.name.ifEmpty { slots.firstOrNull { it.name.isNotEmpty() }?.name.orEmpty() },
                    hours = absence.hours,
                    justified = absence.justified,
                )
            }
            .groupBy { it.date }
            .map { (date, entries) -> HistoryDay(date, entries.sortedBy { startMinutes(it.tramo) }) }
            .sortedByDescending { it.date }
    }

    fun filter(days: List<HistoryDay>, subjectKey: String?): List<HistoryDay> {
        if (subjectKey == null) return days
        return days
            .map { day -> day.copy(entries = day.entries.filter { it.subjectKey == subjectKey }) }
            .filter { it.entries.isNotEmpty() }
    }

    fun filterOptions(days: List<HistoryDay>): List<HistoryFilterOption> =
        days.flatMap { it.entries }
            .distinctBy { it.subjectKey }
            .map { HistoryFilterOption(it.subjectKey, it.label) }
            .sortedBy { it.label }

    private fun startMinutes(tramo: String): Int {
        val match = tramoStart.find(tramo) ?: return Int.MAX_VALUE
        return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
    }
}
