package com.faltasipasen.app.domain

import kotlin.test.assertEquals
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

class HistoryBuilderTest {
    private val cutoff = LocalDate(2027, 2, 15)
    private val today = LocalDate(2026, 10, 1)
    private val schedule = listOf(
        ScheduleSlot("ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", 1, 1.0, "ASO", "Administración de sistemas operativos"),
        ScheduleSlot("SERVICIOS DE RED E INTERNET", 2, 1.0, "SRI", "Servicios de red e Internet"),
    )
    private val absences = listOf(
        Absence(LocalDate(2026, 9, 24), "SERVICIOS DE RED E INTERNET", 1.0, true, "9:15 - 10:15", "Servicios de red e Internet"),
        Absence(LocalDate(2026, 9, 24), "ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", 1.0, false, "8:15 - 9:15", "Administración de sistemas operativos"),
        Absence(LocalDate(2026, 9, 29), "ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", 1.0, false, "11:45 - 12:45", ""),
        Absence(LocalDate(2026, 10, 5), "ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", 1.0, false, "8:15 - 9:15", ""), // después de hoy
        Absence(LocalDate(2026, 8, 31), "ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", 1.0, false, "8:15 - 9:15", ""), // antes del curso
        Absence(LocalDate(2026, 9, 25), "TUTORÍA", 1.0, false, "", "Tutoría"),
    )

    private val days = HistoryBuilder.build(schedule, absences, cutoff, today)

    @Test
    fun `days are newest first and only inside the counted period`() {
        assertEquals(
            listOf(LocalDate(2026, 9, 29), LocalDate(2026, 9, 25), LocalDate(2026, 9, 24)),
            days.map { it.date },
        )
    }

    @Test
    fun `entries of a day are ordered by tramo start time`() {
        assertEquals(listOf("8:15 - 9:15", "9:15 - 10:15"), days.last().entries.map { it.tramo })
    }

    @Test
    fun `abbreviation comes from the timetable and the name falls back to it`() {
        val late = days.first().entries.single()
        assertEquals("ASO", late.abbreviation)
        assertEquals("Administración de sistemas operativos", late.name)
        val tutoria = days[1].entries.single()
        assertEquals("", tutoria.abbreviation)
        assertEquals("Tutoría", tutoria.name)
    }

    @Test
    fun `filter keeps only one subject and drops empty days`() {
        val filtered = HistoryBuilder.filter(days, "SERVICIOS DE RED E INTERNET")
        assertEquals(listOf(LocalDate(2026, 9, 24)), filtered.map { it.date })
        assertEquals(1, filtered.single().entries.size)
        assertEquals(days, HistoryBuilder.filter(days, null))
    }

    @Test
    fun `filter options list each subject once, sorted by label`() {
        assertEquals(
            listOf(
                HistoryFilterOption("ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", "ASO"),
                HistoryFilterOption("SERVICIOS DE RED E INTERNET", "SRI"),
                HistoryFilterOption("TUTORÍA", "Tutoría"),
            ),
            HistoryBuilder.filterOptions(days),
        )
    }

    @Test
    fun `two absences of the same subject on the same day with no tramo are both kept`() {
        val duplicateDay = LocalDate(2026, 9, 24)
        val duplicateSchedule = listOf(
            ScheduleSlot("SERVICIOS DE RED E INTERNET", duplicateDay.dayOfWeek.isoDayNumber, 1.0, "SRI", "Servicios de red e Internet"),
        )
        val duplicateAbsences = listOf(
            Absence(duplicateDay, "SERVICIOS DE RED E INTERNET", 1.0, false, "", ""),
            Absence(duplicateDay, "SERVICIOS DE RED E INTERNET", 1.0, false, "", ""),
        )
        val result = HistoryBuilder.build(duplicateSchedule, duplicateAbsences, cutoff, today)
        assertEquals(1, result.size)
        assertEquals(2, result.single().entries.size)
    }
}
