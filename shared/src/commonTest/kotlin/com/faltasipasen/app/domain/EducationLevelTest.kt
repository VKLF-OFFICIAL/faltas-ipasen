package com.faltasipasen.app.domain

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class EducationLevelTest {

    @Test
    fun `FP levels have Modulos as subject label`() {
        val fpLevels = listOf(
            EducationLevel.FP_BASICO,
            EducationLevel.FP_BASICO_FCT,
            EducationLevel.FP_MEDIO,
            EducationLevel.FP_SUPERIOR,
        )
        fpLevels.forEach { assertEquals("Módulos", it.subjectLabel) }
    }

    @Test
    fun `non-FP levels have Asignaturas as subject label`() {
        val nonFp = listOf(EducationLevel.PRIMARIA, EducationLevel.ESO, EducationLevel.BACHILLERATO)
        nonFp.forEach { assertEquals("Asignaturas", it.subjectLabel) }
    }

    @Test
    fun `levels with FCT show FCT cutoff label`() {
        val fctLevels = listOf(
            EducationLevel.FP_BASICO_FCT,
            EducationLevel.FP_MEDIO,
            EducationLevel.FP_SUPERIOR,
        )
        fctLevels.forEach { assertTrue(it.hasFct); assertEquals("Fin de clases (inicio FCT)", it.cutoffFieldLabel) }
    }

    @Test
    fun `levels without FCT show generic cutoff label`() {
        val noFct = listOf(
            EducationLevel.PRIMARIA,
            EducationLevel.ESO,
            EducationLevel.BACHILLERATO,
            EducationLevel.FP_BASICO,
        )
        noFct.forEach { assertFalse(it.hasFct); assertEquals("Fin del curso", it.cutoffFieldLabel) }
    }

    @Test
    fun `isFpBasico is true only for FP_BASICO variants`() {
        assertTrue(EducationLevel.FP_BASICO.isFpBasico)
        assertTrue(EducationLevel.FP_BASICO_FCT.isFpBasico)
        assertFalse(EducationLevel.ESO.isFpBasico)
        assertFalse(EducationLevel.FP_MEDIO.isFpBasico)
        assertFalse(EducationLevel.FP_SUPERIOR.isFpBasico)
    }

    @Test
    fun `FP_BASICO without FCT defaults to June cutoff`() {
        assertEquals(6, EducationLevel.FP_BASICO.defaultCourseEndMonth)
    }

    @Test
    fun `FP_BASICO_FCT defaults to February cutoff same as FP_MEDIO`() {
        assertEquals(EducationLevel.FP_MEDIO.defaultCourseEndMonth, EducationLevel.FP_BASICO_FCT.defaultCourseEndMonth)
        assertEquals(EducationLevel.FP_MEDIO.defaultCourseEndDay, EducationLevel.FP_BASICO_FCT.defaultCourseEndDay)
    }

    @Test
    fun `default thresholds are within valid range`() {
        EducationLevel.entries.forEach {
            assertTrue(it.defaultThreshold in 1.0..100.0, "${it.name} threshold out of range")
        }
    }
}
