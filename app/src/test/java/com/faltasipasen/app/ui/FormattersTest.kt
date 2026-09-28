package com.faltasipasen.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormattersTest {
    private val now = 1_790_000_000_000L

    @Test
    fun `relative time`() {
        assertEquals("ahora mismo", relativeTime(now - 30_000, now))
        assertEquals("hace 5 min", relativeTime(now - 5 * 60_000, now))
        assertEquals("hace 2 h", relativeTime(now - 2 * 3_600_000 - 60_000, now))
        assertEquals("hace 1 día", relativeTime(now - 26 * 3_600_000L, now))
        assertEquals("hace 3 días", relativeTime(now - 3 * 24 * 3_600_000L, now))
        assertEquals("ahora mismo", relativeTime(now + 60_000, now))
    }

    @Test
    fun `day header is capitalised Spanish`() {
        assertEquals("Jueves, 24 de septiembre", formatDayHeader(LocalDate.of(2026, 9, 24)))
    }

    @Test
    fun `tramo uses an en dash`() {
        assertEquals("8:15 – 9:15", formatTramo(" 8:15 - 9:15 "))
    }

    @Test
    fun `hours drop a trailing zero decimal`() {
        assertEquals("112 h", formatHours(112.0))
        assertEquals("18,6 h", formatHours(18.6))
    }
}
