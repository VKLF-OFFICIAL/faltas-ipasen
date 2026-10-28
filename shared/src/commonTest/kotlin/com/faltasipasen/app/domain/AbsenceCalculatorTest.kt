package com.faltasipasen.app.domain

import kotlin.test.assertEquals
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class AbsenceCalculatorTest {
    private val cutoff = LocalDate(2027, 2, 15)
    private val today = LocalDate(2026, 9, 30)

    // Lunes: REDES 2 sesiones de 1 h. Martes: BD 1 h.
    private val schedule = listOf(
        ScheduleSlot("REDES", 1, 1.0),
        ScheduleSlot("REDES", 1, 1.0),
        ScheduleSlot("BD", 2, 1.0),
    )

    private val calendar = listOf(
        CalendarDay(LocalDate(2026, 8, 31), true),   // lunes antes del inicio de curso: no cuenta
        CalendarDay(LocalDate(2026, 9, 14), true),   // lunes
        CalendarDay(LocalDate(2026, 9, 15), true),   // martes
        CalendarDay(LocalDate(2026, 9, 21), true),   // lunes
        CalendarDay(LocalDate(2026, 9, 22), false),  // martes no lectivo
        CalendarDay(LocalDate(2026, 10, 5), true),   // lunes futuro: cuenta para el total del curso
    )

    private val absences = listOf(
        Absence(LocalDate(2026, 9, 14), "REDES", 1.0, justified = false),
        Absence(LocalDate(2026, 9, 15), "BD", 1.0, justified = true),
        Absence(LocalDate(2026, 10, 5), "REDES", 1.0, justified = false), // posterior a hoy: aún no cuenta
    )

    private fun calc(
        cutoff: LocalDate = this.cutoff,
        today: LocalDate = this.today,
        absences: List<Absence> = this.absences,
    ) = AbsenceCalculator.calculate(schedule, calendar, absences, cutoff, today)

    private fun AbsenceStats.subject(label: String) = subjects.single { it.label == label }

    @Test
    fun `course hours include every lectivo day until the cutoff, also future ones`() {
        val stats = calc()
        assertEquals(6.0, stats.subject("REDES").courseHours, 1e-9)
        assertEquals(1.0, stats.subject("BD").courseHours, 1e-9)
        assertEquals(7.0, stats.global.courseHours, 1e-9)
    }

    @Test
    fun `absences are counted only until today`() {
        val stats = calc()
        assertEquals(1.0, stats.subject("REDES").absentHours, 1e-9)
        assertEquals(1.0, stats.subject("BD").absentHours, 1e-9)
        assertEquals(2.0, stats.global.absentHours, 1e-9)
        assertEquals(LocalDate(2026, 9, 30), stats.countedUntil)
    }

    @Test
    fun `percent is absent hours over total course hours`() {
        val stats = calc()
        assertEquals(100.0 / 6, stats.subject("REDES").percent, 1e-9)
        assertEquals(100.0, stats.subject("BD").percent, 1e-9)
        assertEquals(200.0 / 7, stats.global.percent, 1e-9)
    }

    @Test
    fun `unjustified hours are tracked separately`() {
        val stats = calc()
        assertEquals(1.0, stats.global.unjustifiedHours, 1e-9)
        assertEquals(100.0 / 7, stats.global.unjustifiedPercent, 1e-9)
        assertEquals(0.0, stats.subject("BD").unjustifiedHours, 1e-9)
    }

    @Test
    fun `days and absences after the cutoff are ignored`() {
        val stats = calc(cutoff = LocalDate(2026, 9, 21), today = LocalDate(2027, 1, 1))
        // Con corte en sept. 2026 el curso es 2025-26: el lunes 31/08/2026 sí cuenta.
        assertEquals(6.0, stats.subject("REDES").courseHours, 1e-9)
        assertEquals(1.0, stats.subject("REDES").absentHours, 1e-9)
        assertEquals(LocalDate(2026, 9, 21), stats.countedUntil)
        assertEquals(LocalDate(2025, 9, 1), stats.courseStart)
    }

    @Test
    fun `subject that only appears in absences has zero course hours and zero percent`() {
        val stats = calc(absences = absences + Absence(LocalDate(2026, 9, 14), "TUTORIA", 1.0, false))
        val tutoria = stats.subject("TUTORIA")
        assertEquals(0.0, tutoria.courseHours, 1e-9)
        assertEquals(1.0, tutoria.absentHours, 1e-9)
        assertEquals(0.0, tutoria.percent, 1e-9)
    }

    @Test
    fun `subjects are sorted by percent descending`() {
        assertEquals(listOf("BD", "REDES"), calc().subjects.map { it.label })
    }

    @Test
    fun `no data gives zero everywhere`() {
        val stats = AbsenceCalculator.calculate(emptyList(), emptyList(), emptyList(), cutoff, today)
        assertEquals(0.0, stats.global.courseHours, 1e-9)
        assertEquals(0.0, stats.global.percent, 1e-9)
        assertEquals(emptyList<HoursStats>(), stats.subjects)
    }

    @Test
    fun `margin is the hours left before reaching the threshold, never negative`() {
        assertEquals(5.0, HoursStats("X", 100.0, 10.0, 0.0).marginHours(15.0), 1e-9)
        assertEquals(0.0, HoursStats("X", 100.0, 20.0, 0.0).marginHours(15.0), 1e-9)
    }

    @Test
    fun `lectivo days are counted until today and until the cutoff`() {
        val stats = calc()
        assertEquals(3, stats.lectivoDaysElapsed) // 14/09, 15/09, 21/09
        assertEquals(4, stats.lectivoDaysTotal)   // + 05/10
    }

    @Test
    fun `subject stats carry abbreviation and readable name`() {
        val named = listOf(ScheduleSlot("REDES", 1, 2.0, "RED", "Redes"))
        val stats = AbsenceCalculator.calculate(named, calendar, absences, cutoff, today)
        val redes = stats.subjects.single { it.label == "REDES" }
        assertEquals("RED", redes.abbreviation)
        assertEquals("Redes", redes.name)
        assertEquals("RED", redes.shortLabel)
        assertEquals("BD", stats.subjects.single { it.label == "BD" }.shortLabel)
    }

    @Test
    fun `justified hours are absent minus unjustified`() {
        assertEquals(1.0, calc().global.justifiedHours, 1e-9)
    }
}
