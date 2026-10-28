package com.faltasipasen.app.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

object CourseDates {
    private const val COURSE_START_MONTH = 9

    /**
     * Devuelve la fecha de fin de curso por defecto para [level] dado el día de hoy.
     *
     * - Para etapas con FCT (Grado Medio/Superior) el corte es antes de las prácticas
     *   (por defecto el 15 de febrero del año en que acaba el primer año lectivo).
     * - Para el resto es el último día de clase estimado del año lectivo actual
     *   (normalmente finales de junio, o finales de mayo en Bachillerato).
     */
    fun defaultCutoff(today: LocalDate, level: EducationLevel = EducationLevel.FP_SUPERIOR): LocalDate {
        // Año académico actual: si estamos en septiembre o después, el curso acaba el año siguiente.
        val endYear = if (today.month.number >= COURSE_START_MONTH) today.year + 1 else today.year
        return LocalDate(endYear, level.defaultCourseEndMonth, level.defaultCourseEndDay)
    }

    /**
     * El inicio de curso siempre es el 1 de septiembre del año anterior al de [cutoff].
     * Ejemplo: cutoff 15-feb-2027 → inicio 1-sep-2026.
     *          cutoff 22-jun-2027 → inicio 1-sep-2026.
     *          cutoff 21-sep-2026 → inicio 1-sep-2025 (corte al final del curso 25-26).
     */
    fun courseStartFor(cutoff: LocalDate): LocalDate =
        LocalDate(cutoff.year - 1, COURSE_START_MONTH, 1)
}
