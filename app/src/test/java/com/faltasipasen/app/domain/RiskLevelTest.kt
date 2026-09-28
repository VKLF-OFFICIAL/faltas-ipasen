package com.faltasipasen.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RiskLevelTest {
    @Test
    fun `below half of the threshold is low`() {
        assertEquals(RiskLevel.LOW, RiskLevel.of(0.4, 20.0))
        assertEquals(RiskLevel.LOW, RiskLevel.of(9.99, 20.0))
    }

    @Test
    fun `from half of the threshold up to it is medium`() {
        assertEquals(RiskLevel.MEDIUM, RiskLevel.of(10.0, 20.0))
        assertEquals(RiskLevel.MEDIUM, RiskLevel.of(19.9, 20.0))
    }

    @Test
    fun `reaching the threshold is high`() {
        assertEquals(RiskLevel.HIGH, RiskLevel.of(20.0, 20.0))
        assertEquals(RiskLevel.HIGH, RiskLevel.of(35.0, 20.0))
    }
}
