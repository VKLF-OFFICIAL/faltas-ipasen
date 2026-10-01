package com.faltasipasen.app.domain

import com.faltasipasen.app.domain.EducationLevel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CourseDatesTest {
    @Test
    fun `default cutoff in autumn is 15 February of next year`() {
        assertEquals(LocalDate.of(2027, 2, 15), CourseDates.defaultCutoff(LocalDate.of(2026, 9, 28)))
    }

    @Test
    fun `default cutoff in January is 15 February of the same year`() {
        assertEquals(LocalDate.of(2027, 2, 15), CourseDates.defaultCutoff(LocalDate.of(2027, 1, 10)))
    }

    @Test
    fun `default cutoff in summer still belongs to the course that just ended`() {
        assertEquals(LocalDate.of(2027, 2, 15), CourseDates.defaultCutoff(LocalDate.of(2027, 7, 1)))
    }

    @Test
    fun `course starts on 1 September before the cutoff`() {
        assertEquals(LocalDate.of(2026, 9, 1), CourseDates.courseStartFor(LocalDate.of(2027, 2, 15)))
    }

    @Test
    fun `default cutoff for ESO is 22 June`() {
        assertEquals(LocalDate.of(2027, 6, 22), CourseDates.defaultCutoff(LocalDate.of(2026, 9, 28), EducationLevel.ESO))
    }

    @Test
    fun `default cutoff for Bachillerato is 31 May`() {
        assertEquals(LocalDate.of(2027, 5, 31), CourseDates.defaultCutoff(LocalDate.of(2026, 9, 28), EducationLevel.BACHILLERATO))
    }

    @Test
    fun `default cutoff for FP Medio is 15 February`() {
        assertEquals(LocalDate.of(2027, 2, 15), CourseDates.defaultCutoff(LocalDate.of(2026, 9, 28), EducationLevel.FP_MEDIO))
    }

    @Test
    fun `course start is always 1 September of the year before the cutoff`() {
        assertEquals(LocalDate.of(2026, 9, 1), CourseDates.courseStartFor(LocalDate.of(2027, 2, 15)))
        assertEquals(LocalDate.of(2026, 9, 1), CourseDates.courseStartFor(LocalDate.of(2027, 6, 22)))
        assertEquals(LocalDate.of(2025, 9, 1), CourseDates.courseStartFor(LocalDate.of(2026, 9, 21)))
    }
}
