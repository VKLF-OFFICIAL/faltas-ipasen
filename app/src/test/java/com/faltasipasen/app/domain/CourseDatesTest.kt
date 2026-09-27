package com.faltasipasen.app.domain

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
}
