package com.faltasipasen.app.scraper

/** Un paso del scraping: opcionalmente carga [url] y ejecuta el script [asset]. */
data class ScrapeStep(val id: String, val url: String?, val asset: String) {
    companion object {
        const val LOGIN = "login"
        const val LOGIN_CHECK = "loginCheck"
    }
}

/**
 * Todo lo que depende de la web real de PASEN (la versión web de iPasen, en Séneca).
 * Documentado en docs/scraper-selectors.md. Séneca usa URLs de un solo uso, así que los pasos
 * no cargan URLs: cada script abre su sección con el menú de Séneca.
 */
object IpasenSite {
    val LOGIN_URL: String = "https://seneca.juntadeandalucia.es/seneca/jsp/portalpasen/"

    val ALLOWED_HOST_SUFFIXES: List<String> = listOf("juntadeandalucia.es")

    val SCHEDULE = ScrapeStep(id = "schedule", url = null, asset = "scraper/schedule.js")
    val CALENDAR = ScrapeStep(id = "calendar", url = null, asset = "scraper/calendar.js")
    val ABSENCES = ScrapeStep(id = "absences", url = null, asset = "scraper/absences.js")
}
