package com.faltasipasen.app.scraper

import com.faltasipasen.app.domain.Absence
import com.faltasipasen.app.domain.CalendarDay
import com.faltasipasen.app.domain.Credentials
import com.faltasipasen.app.domain.ScheduleSlot

data class ScrapeResult(
    val schedule: List<ScheduleSlot>,
    val calendar: List<CalendarDay>,
    val absences: List<Absence>,
)

interface IpasenScraper {
    suspend fun scrape(credentials: Credentials): ScrapeResult
}

class ScrapeException(val reason: Reason, message: String) : Exception(message) {
    /** INTERNAL: fallo de la propia app o del móvil (WebView, disco…), no de iPasen. */
    enum class Reason { LOGIN_FAILED, NETWORK, TIMEOUT, PARSE_ERROR, INTERNAL }
}
