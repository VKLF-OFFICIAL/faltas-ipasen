package com.faltasipasen.app.scraper

import com.faltasipasen.app.domain.Absence
import com.faltasipasen.app.domain.CalendarDay
import com.faltasipasen.app.domain.ScheduleSlot
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class ScrapeParsersTest {
    private fun reasonOf(block: () -> Unit): ScrapeException.Reason =
        assertFailsWith<ScrapeException> { block() }.reason

    @Test
    fun `parses schedule and normalizes subject names`() {
        val raw = """{"ok":true,"data":[{"subject":"  Redes   locales ","day":1,"hours":1.5},{"subject":"BD","day":3}]}"""
        assertEquals(
            listOf(
                ScheduleSlot("REDES LOCALES", 1, 1.5, name = "Redes locales"),
                ScheduleSlot("BD", 3, 1.0, name = "BD"),
            ),
            ScrapeParsers.parseSchedule(raw),
        )
    }

    @Test
    fun `parses schedule abbreviations`() {
        val raw = """{"ok":true,"data":[{"subject":"Administración de sistemas operativos","abbr":" ASO ","day":2,"hours":1}]}"""
        assertEquals(
            listOf(ScheduleSlot("ADMINISTRACIÓN DE SISTEMAS OPERATIVOS", 2, 1.0, "ASO", "Administración de sistemas operativos")),
            ScrapeParsers.parseSchedule(raw),
        )
    }

    @Test
    fun `rejects a day of week out of range`() {
        val raw = """{"ok":true,"data":[{"subject":"BD","day":8}]}"""
        assertEquals(ScrapeException.Reason.PARSE_ERROR, reasonOf { ScrapeParsers.parseSchedule(raw) })
    }

    @Test
    fun `rejects non positive durations`() {
        val raw = """{"ok":true,"data":[{"subject":"BD","day":2,"hours":0}]}"""
        assertEquals(ScrapeException.Reason.PARSE_ERROR, reasonOf { ScrapeParsers.parseSchedule(raw) })
    }

    @Test
    fun `parses calendar days`() {
        val raw = """{"ok":true,"data":[{"date":"2026-09-15","lectivo":true},{"date":"2026-12-24","lectivo":false}]}"""
        assertEquals(
            listOf(CalendarDay(LocalDate(2026, 9, 15), true), CalendarDay(LocalDate(2026, 12, 24), false)),
            ScrapeParsers.parseCalendar(raw),
        )
    }

    @Test
    fun `rejects invalid dates`() {
        val raw = """{"ok":true,"data":[{"date":"15/09/2026","lectivo":true}]}"""
        assertEquals(ScrapeException.Reason.PARSE_ERROR, reasonOf { ScrapeParsers.parseCalendar(raw) })
    }

    @Test
    fun `parses absences with defaults`() {
        val raw = """{"ok":true,"data":{"absences":[{"date":"2026-10-02","subject":"bd"},{"date":"2026-10-03","subject":"Redes","hours":2,"justified":true,"tramo":" 8:15 - 9:15 "}]}}"""
        assertEquals(
            listOf(
                Absence(LocalDate(2026, 10, 2), "BD", 1.0, false, name = "bd"),
                Absence(LocalDate(2026, 10, 3), "REDES", 2.0, true, tramo = "8:15 - 9:15", name = "Redes"),
            ),
            ScrapeParsers.parseAbsences(raw).absences,
        )
    }

    @Test
    fun `a full day absence counts every class of that weekday`() {
        val schedule = listOf(
            ScheduleSlot("REDES", 2, 1.0, "SRI", "Redes"),
            ScheduleSlot("BD", 2, 2.0, "BD", "Bases de datos"),
            ScheduleSlot("SO", 3, 1.0, "SO", "Sistemas"),
        )
        // 2026-10-13 is a Tuesday (dayOfWeek 2).
        val raw = """{"ok":true,"data":{"absences":[{"date":"2026-10-13","subject":"","fullDay":true,"justified":true,"tramo":"Día completo"}]}}"""
        assertEquals(
            listOf(
                Absence(LocalDate(2026, 10, 13), "REDES", 1.0, true, tramo = "Día completo", name = "Redes"),
                Absence(LocalDate(2026, 10, 13), "BD", 2.0, true, tramo = "Día completo", name = "Bases de datos"),
            ),
            ScrapeParsers.parseAbsences(raw, schedule).absences,
        )
    }

    @Test
    fun `a full day replaces that day's hourly rows so nothing counts twice`() {
        val schedule = listOf(ScheduleSlot("REDES", 2, 1.0, "SRI", "Redes"))
        val raw = """{"ok":true,"data":{"absences":[
            {"date":"2026-10-13","subject":"Redes","hours":1,"tramo":"8:15 - 9:15"},
            {"date":"2026-10-13","subject":"","fullDay":true,"justified":true,"tramo":"Día completo"},
            {"date":"2026-10-14","subject":"Redes","hours":1,"tramo":"8:15 - 9:15"}]}}"""
        assertEquals(
            listOf(
                Absence(LocalDate(2026, 10, 13), "REDES", 1.0, true, tramo = "Día completo", name = "Redes"),
                Absence(LocalDate(2026, 10, 14), "REDES", 1.0, false, tramo = "8:15 - 9:15", name = "Redes"),
            ),
            ScrapeParsers.parseAbsences(raw, schedule).absences,
        )
    }

    @Test
    fun `unreadable absence rows are set aside and the rest are kept`() {
        val raw = """{"ok":true,"data":{"absences":[
            {"date":"2026-10-02","subject":"Redes"},
            {"date":"2026-10-03","subject":"  "},
            {"date":"ayer","subject":"BD"},
            {"date":"2026-10-04","subject":"BD","hours":40}],
            "skipped":["05/10/2026 | Excursión | | Justificada: tramo horario no reconocido: Excursión"]}}"""
        val read = ScrapeParsers.parseAbsences(raw)
        assertEquals(listOf(Absence(LocalDate(2026, 10, 2), "REDES", 1.0, false, name = "Redes")), read.absences)
        assertEquals(4, read.skipped.size)
        assertTrue(read.skipped.first().contains("Excursión"))
        assertTrue(read.skipped.any { it.contains("ayer") })
    }

    @Test
    fun `script failure is a parse error with the script message`() {
        val raw = """{"ok":false,"error":"tabla no encontrada"}"""
        val e = assertFailsWith<ScrapeException> { ScrapeParsers.parseAbsences(raw) }
        assertEquals(ScrapeException.Reason.PARSE_ERROR, e.reason)
        assertTrue(e.message!!.contains("tabla no encontrada"))
    }

    @Test
    fun `rejected login is reported as login failure`() {
        val raw = """{"ok":false,"error":"Usuario o clave incorrectos","loginFailed":true}"""
        assertEquals(ScrapeException.Reason.LOGIN_FAILED, reasonOf { ScrapeParsers.checkOk(raw, "login") })
    }

    @Test
    fun `a page that never appears is a timeout, not a changed website`() {
        val raw = """{"ok":false,"error":"tiempo de espera agotado en schedule","timeout":true}"""
        assertEquals(ScrapeException.Reason.TIMEOUT, reasonOf { ScrapeParsers.parseSchedule(raw) })
    }

    @Test
    fun `successful step passes the ok check`() {
        ScrapeParsers.checkOk("""{"ok":true,"data":"ok"}""", "login")
    }

    @Test
    fun `non JSON output is a parse error`() {
        assertEquals(ScrapeException.Reason.PARSE_ERROR, reasonOf { ScrapeParsers.parseSchedule("undefined") })
    }

    @Test
    fun `wrong payload shape is a parse error`() {
        assertEquals(ScrapeException.Reason.PARSE_ERROR, reasonOf { ScrapeParsers.parseSchedule("""{"ok":true,"data":{"a":1}}""") })
    }

    @Test
    fun `empty subject is rejected`() {
        val raw = """{"ok":true,"data":[{"subject":"   ","day":1}]}"""
        assertEquals(ScrapeException.Reason.PARSE_ERROR, reasonOf { ScrapeParsers.parseSchedule(raw) })
    }

    @Test
    fun `a late row counts half its hours and keeps the late flag`() {
        val raw = """{"ok":true,"data":{"absences":[{"date":"2026-10-02","subject":"Redes","hours":1,"justified":false,"tramo":"8:15 - 9:15","late":true},{"date":"2026-10-03","subject":"Redes","hours":1.5,"late":true,"justified":true},{"date":"2026-10-04","subject":"Redes","hours":1}]}}"""
        val absences = ScrapeParsers.parseAbsences(raw).absences
        assertEquals(0.5, absences[0].hours, 1e-9)
        assertTrue(absences[0].late)
        assertEquals(0.75, absences[1].hours, 1e-9)
        assertEquals(1.0, absences[2].hours, 1e-9)
        assertEquals(false, absences[2].late)
    }

    @Test
    fun `a full day row is never late`() {
        val schedule = listOf(ScheduleSlot("REDES", 2, 1.0, "SRI", "Redes"))
        val raw = """{"ok":true,"data":{"absences":[{"date":"2026-10-06","subject":"","fullDay":true,"late":true,"justified":false}]}}"""
        val absences = ScrapeParsers.parseAbsences(raw, schedule).absences
        assertEquals(1.0, absences.single().hours, 1e-9)
        assertEquals(false, absences.single().late)
    }
}
