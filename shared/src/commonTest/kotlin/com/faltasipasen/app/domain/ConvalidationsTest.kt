package com.faltasipasen.app.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ConvalidationsTest {
    private val schedule = listOf(
        ScheduleSlot("REDES", 1, 2.0, "RED", "Redes"),
        ScheduleSlot("BASES", 2, 1.0, "BD", "Bases"),
    )
    private val absences = listOf(
        Absence(LocalDate(2026, 9, 14), "REDES", 1.0, false),
        Absence(LocalDate(2026, 9, 15), "BASES", 1.0, true),
    )
    private val calendar = listOf(
        CalendarDay(LocalDate(2026, 9, 14), true),
        CalendarDay(LocalDate(2026, 9, 15), true),
        CalendarDay(LocalDate(2026, 9, 21), true),
    )
    private val cutoff = LocalDate(2027, 2, 15)
    private val today = LocalDate(2026, 9, 30)

    @Test
    fun emptySetIsIdentity() {
        val (s, a) = Convalidations.exclude(schedule, absences, emptySet())
        assertEquals(schedule, s)
        assertEquals(absences, a)
    }

    @Test
    fun excludedSubjectVanishesFromTotals() {
        val (s, a) = Convalidations.exclude(schedule, absences, setOf("REDES"))
        val stats = AbsenceCalculator.calculate(s, calendar, a, cutoff, today)
        assertEquals(listOf("BASES"), stats.subjects.map { it.label })
        assertEquals(1.0, stats.global.courseHours)
        assertEquals(1.0, stats.global.absentHours)
    }

    @Test
    fun otherSubjectsAreUnchanged() {
        val full = AbsenceCalculator.calculate(schedule, calendar, absences, cutoff, today)
        val (s, a) = Convalidations.exclude(schedule, absences, setOf("REDES"))
        val partial = AbsenceCalculator.calculate(s, calendar, a, cutoff, today)
        assertEquals(full.subjects.first { it.label == "BASES" }, partial.subjects.single())
        assertEquals(5.0, full.global.courseHours)
    }
}
