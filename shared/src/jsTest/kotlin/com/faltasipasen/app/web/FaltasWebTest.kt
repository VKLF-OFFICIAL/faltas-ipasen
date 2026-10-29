package com.faltasipasen.app.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FaltasWebTest {
    private val schedule = """{"ok":true,"data":[{"subject":"Redes","day":1,"hours":2,"abbr":"RED"},{"subject":"Bases","day":2,"hours":1,"abbr":"BD"}]}"""
    private val calendar = """{"ok":true,"data":[{"date":"2026-09-14","lectivo":true},{"date":"2026-09-15","lectivo":true},{"date":"2026-09-21","lectivo":true}]}"""
    private val absences = """{"ok":true,"data":{"absences":[{"date":"2026-09-14","subject":"Redes","hours":1,"justified":false,"tramo":"8:15 - 9:15"}],"skipped":["x | y: fecha no reconocida"]}}"""

    @Test
    fun calcularReturnsTheSameFiguresAsTheApk() {
        val out = JSON.parse<dynamic>(FaltasWeb.calcular(schedule, calendar, absences, "2027-02-15", "2026-09-30", 15.0, "[]"))
        // Redes: lunes 14 y 21 → 4 h de curso, 1 h de falta = 25 %.
        assertEquals("REDES", out.subjects[0].label)
        assertEquals(4.0, out.subjects[0].courseHours)
        assertEquals(25.0, out.subjects[0].percent)
        assertEquals("25,0 %", out.subjects[0].percentText)
        assertEquals("HIGH", out.subjects[0].risk)
        assertEquals("RED", out.subjects[0].abbreviation)
        assertEquals(5.0, out.global.courseHours)
        assertEquals(1, out.skipped.length)
        assertEquals("2026-09-14", out.history[0].date)
        assertEquals("RED", out.history[0].entries[0].label)
    }

    @Test
    fun convalidatedSubjectIsExcludedAndListed() {
        val out = JSON.parse<dynamic>(FaltasWeb.calcular(schedule, calendar, absences, "2027-02-15", "2026-09-30", 15.0, """["REDES"]"""))
        assertEquals(1, out.subjects.length)
        assertEquals("BASES", out.subjects[0].label)
        assertEquals(1.0, out.global.courseHours)
        assertEquals(0.0, out.global.absentHours)
        assertEquals(0, out.history.length)
        assertEquals(1, out.convalidated.length)
        assertEquals("REDES", out.convalidated[0].label)
        assertEquals("RED", out.convalidated[0].abbreviation)
    }

    @Test
    fun nivelesGivesTheApkDefaults() {
        val levels = JSON.parse<Array<dynamic>>(FaltasWeb.niveles("2026-10-28"))
        val superior = levels.first { it.id == "FP_SUPERIOR" }
        assertEquals(15.0, superior.threshold)
        assertEquals("2027-02-15", superior.cutoff)
        assertTrue(levels.any { it.id == "ESO" && it.cutoff == "2027-06-22" })
    }

    @Test
    fun parserErrorsBecomeJsErrors() {
        val e = assertFailsWith<Throwable> {
            FaltasWeb.calcular("""{"ok":true,"data":[{"subject":"","day":1}]}""", calendar, absences, "2027-02-15", "2026-09-30", 15.0, "[]")
        }
        assertTrue(e.message!!.contains("asignatura vacía"))
    }
}
