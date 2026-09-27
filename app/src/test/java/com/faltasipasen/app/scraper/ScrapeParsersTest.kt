package com.faltasipasen.app.scraper

import com.faltasipasen.app.domain.Absence
import com.faltasipasen.app.domain.CalendarDay
import com.faltasipasen.app.domain.ScheduleSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScrapeParsersTest {
    private fun reasonOf(block: () -> Unit): ScrapeException.Reason =
        assertThrows(ScrapeException::class.java) { block() }.reason

    @Test
    fun `parses schedule and normalizes subject names`() {
        val raw = """{"ok":true,"data":[{"subject":"  Redes   locales ","day":1,"hours":1.5},{"subject":"BD","day":3}]}"""
        assertEquals(
            listOf(ScheduleSlot("REDES LOCALES", 1, 1.5), ScheduleSlot("BD", 3, 1.0)),
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
            listOf(CalendarDay(LocalDate.of(2026, 9, 15), true), CalendarDay(LocalDate.of(2026, 12, 24), false)),
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
        val raw = """{"ok":true,"data":[{"date":"2026-10-02","subject":"bd"},{"date":"2026-10-03","subject":"Redes","hours":2,"justified":true}]}"""
        assertEquals(
            listOf(
                Absence(LocalDate.of(2026, 10, 2), "BD", 1.0, false),
                Absence(LocalDate.of(2026, 10, 3), "REDES", 2.0, true),
            ),
            ScrapeParsers.parseAbsences(raw),
        )
    }

    @Test
    fun `script failure is a parse error with the script message`() {
        val raw = """{"ok":false,"error":"tabla no encontrada"}"""
        val e = assertThrows(ScrapeException::class.java) { ScrapeParsers.parseAbsences(raw) }
        assertEquals(ScrapeException.Reason.PARSE_ERROR, e.reason)
        assertTrue(e.message!!.contains("tabla no encontrada"))
    }

    @Test
    fun `rejected login is reported as login failure`() {
        val raw = """{"ok":false,"error":"Usuario o clave incorrectos","loginFailed":true}"""
        assertEquals(ScrapeException.Reason.LOGIN_FAILED, reasonOf { ScrapeParsers.checkOk(raw, "login") })
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
}
