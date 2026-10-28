package com.faltasipasen.app.scraper

import com.faltasipasen.app.domain.Absence
import com.faltasipasen.app.domain.CalendarDay
import com.faltasipasen.app.domain.Credentials
import com.faltasipasen.app.domain.ScheduleSlot

data class ScrapeResult(
    val schedule: List<ScheduleSlot>,
    val calendar: List<CalendarDay>,
    val absences: List<Absence>,
    /** Absence rows that could not be read; the sync goes on without them. */
    val skippedAbsences: List<String> = emptyList(),
)

interface IpasenScraper {
    suspend fun scrape(credentials: Credentials): ScrapeResult
}
