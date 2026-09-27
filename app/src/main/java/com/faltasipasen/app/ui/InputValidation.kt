package com.faltasipasen.app.ui

object InputValidation {
    val SYNC_INTERVALS: List<Int> = listOf(6, 12, 24)

    fun parseThreshold(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it <= 100.0 }

    fun credentialsValid(username: String, password: String): Boolean =
        username.isNotBlank() && password.isNotEmpty()
}
