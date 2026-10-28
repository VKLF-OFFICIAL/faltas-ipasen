package com.faltasipasen.app.scraper

class ScrapeException(val reason: Reason, message: String) : Exception(message) {
    /** INTERNAL: fallo de la propia app o del móvil (WebView, disco…), no de iPasen. */
    enum class Reason { LOGIN_FAILED, NETWORK, TIMEOUT, PARSE_ERROR, INTERNAL }
}
