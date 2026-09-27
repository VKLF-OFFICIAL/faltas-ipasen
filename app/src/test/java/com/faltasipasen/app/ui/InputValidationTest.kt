package com.faltasipasen.app.ui

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

    @Test
    fun `credentials need a username and a password`() {
        assertTrue(InputValidation.credentialsValid("alumno", "x"))
        assertFalse(InputValidation.credentialsValid("  ", "x"))
        assertFalse(InputValidation.credentialsValid("alumno", ""))
    }
}
