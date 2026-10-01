package com.faltasipasen.app.ui

import com.faltasipasen.app.domain.CourseDates
import com.faltasipasen.app.domain.EducationLevel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidationTest {
    @Test
    fun `threshold accepts comma and dot decimals`() {
        assertEquals(15.5, InputValidation.parseThreshold("15,5")!!, 0.0)
        assertEquals(20.0, InputValidation.parseThreshold(" 20 ")!!, 0.0)
        assertEquals(100.0, InputValidation.parseThreshold("100")!!, 0.0)
    }

    @Test
    fun `threshold rejects out of range or garbage`() {
        assertNull(InputValidation.parseThreshold("0"))
        assertNull(InputValidation.parseThreshold("100,1"))
        assertNull(InputValidation.parseThreshold("-3"))
        assertNull(InputValidation.parseThreshold("quince"))
        assertNull(InputValidation.parseThreshold(""))
    }

    private val suggested: (EducationLevel) -> LocalDate = { CourseDates.defaultCutoff(LocalDate.of(2026, 10, 1), it) }

    @Test
    fun `untouched suggestions follow the new level`() {
        val (threshold, cutoff) = InputValidation.followLevelDefaults(
            EducationLevel.FP_SUPERIOR, EducationLevel.ESO, "15", LocalDate.of(2027, 2, 15), suggested,
        )
        assertEquals("20", threshold)
        assertEquals(LocalDate.of(2027, 6, 22), cutoff)
    }

    @Test
    fun `values the user changed are kept when the level changes`() {
        val (threshold, cutoff) = InputValidation.followLevelDefaults(
            EducationLevel.FP_SUPERIOR, EducationLevel.ESO, "12,5", LocalDate.of(2027, 3, 1), suggested,
        )
        assertEquals("12,5", threshold)
        assertEquals(LocalDate.of(2027, 3, 1), cutoff)
    }

    @Test
    fun `credentials need a username and a password`() {
        assertTrue(InputValidation.credentialsValid("alumno", "x"))
        assertFalse(InputValidation.credentialsValid("  ", "x"))
        assertFalse(InputValidation.credentialsValid("alumno", ""))
    }
}
