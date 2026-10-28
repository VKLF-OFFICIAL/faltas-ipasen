package com.faltasipasen.app.ui

import com.faltasipasen.app.domain.EducationLevel
import kotlinx.datetime.LocalDate

object InputValidation {
    val SYNC_INTERVALS: List<Int> = listOf(6, 12, 24)

    fun parseThreshold(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it <= 100.0 }

    fun credentialsValid(username: String, password: String): Boolean =
        username.isNotBlank() && password.isNotEmpty()

    /**
     * Al cambiar de etapa, el umbral y la fecha que seguían en la sugerencia de la etapa anterior
     * pasan a la de la nueva; los que el usuario ya había cambiado se respetan.
     */
    fun followLevelDefaults(
        from: EducationLevel,
        to: EducationLevel,
        thresholdText: String,
        cutoff: LocalDate,
        suggestedCutoff: (EducationLevel) -> LocalDate,
    ): Pair<String, LocalDate> {
        val threshold = if (parseThreshold(thresholdText) == from.defaultThreshold) formatNumber(to.defaultThreshold) else thresholdText
        val date = if (cutoff == suggestedCutoff(from)) suggestedCutoff(to) else cutoff
        return threshold to date
    }
}
