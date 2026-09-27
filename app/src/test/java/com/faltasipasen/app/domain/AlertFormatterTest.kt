package com.faltasipasen.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertFormatterTest {
    @Test
    fun `percent uses Spanish decimal comma and one decimal`() {
        assertEquals("16,2 %", AlertFormatter.percent(16.2))
        assertEquals("15,0 %", AlertFormatter.percent(15.0))
    }

    @Test
    fun `title mentions the threshold`() {
        assertEquals("Has superado el 15,0 % de faltas", AlertFormatter.thresholdTitle(15.0))
    }

    @Test
    fun `body lists every alert`() {
        val body = AlertFormatter.thresholdBody(
            listOf(ThresholdAlert("__total__", "Total", 16.2), ThresholdAlert("REDES", "REDES", 21.0)),
        )
        assertEquals("Total: 16,2 % · REDES: 21,0 %", body)
    }
}
