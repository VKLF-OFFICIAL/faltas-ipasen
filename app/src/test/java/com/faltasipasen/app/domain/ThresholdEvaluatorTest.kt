package com.faltasipasen.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ThresholdEvaluatorTest {
    private fun stats(globalAbsent: Double, vararg subjects: HoursStats) = AbsenceStats(
        global = HoursStats("Total", 100.0, globalAbsent, 0.0),
        subjects = subjects.toList(),
        courseStart = LocalDate.of(2026, 9, 1),
        cutoff = LocalDate.of(2027, 2, 15),
        countedUntil = LocalDate.of(2026, 10, 1),
    )

    @Test
    fun `crossing the threshold for the first time creates an alert`() {
        val result = ThresholdEvaluator.evaluate(stats(16.0), 15.0, emptySet())
        assertEquals(listOf(ThresholdAlert(ThresholdEvaluator.GLOBAL_KEY, "Total", 16.0)), result.newAlerts)
        assertEquals(setOf(ThresholdEvaluator.GLOBAL_KEY), result.activeKeys)
    }

    @Test
    fun `reaching exactly the threshold counts`() {
        assertEquals(1, ThresholdEvaluator.evaluate(stats(15.0), 15.0, emptySet()).newAlerts.size)
    }

    @Test
    fun `an already notified crossing is not repeated`() {
        val result = ThresholdEvaluator.evaluate(stats(18.0), 15.0, setOf(ThresholdEvaluator.GLOBAL_KEY))
        assertEquals(emptyList<ThresholdAlert>(), result.newAlerts)
        assertEquals(setOf(ThresholdEvaluator.GLOBAL_KEY), result.activeKeys)
    }

    @Test
    fun `dropping below clears the key so a later crossing alerts again`() {
        val below = ThresholdEvaluator.evaluate(stats(10.0), 15.0, setOf(ThresholdEvaluator.GLOBAL_KEY))
        assertEquals(emptySet<String>(), below.activeKeys)
        val again = ThresholdEvaluator.evaluate(stats(16.0), 15.0, below.activeKeys)
        assertEquals(1, again.newAlerts.size)
    }

    @Test
    fun `subjects over the threshold alert with their own key`() {
        val redes = HoursStats("REDES", 50.0, 10.0, 0.0) // 20 %
        val bd = HoursStats("BD", 50.0, 1.0, 0.0)        // 2 %
        val result = ThresholdEvaluator.evaluate(stats(11.0, redes, bd), 15.0, emptySet())
        assertEquals(listOf(ThresholdAlert("REDES", "REDES", 20.0)), result.newAlerts)
    }

    @Test
    fun `subjects without course hours never alert`() {
        val ghost = HoursStats("TUTORIA", 0.0, 3.0, 3.0)
        assertEquals(emptyList<ThresholdAlert>(), ThresholdEvaluator.evaluate(stats(0.0, ghost), 15.0, emptySet()).newAlerts)
    }
}
