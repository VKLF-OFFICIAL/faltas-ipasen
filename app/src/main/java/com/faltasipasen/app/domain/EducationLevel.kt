package com.faltasipasen.app.domain

/**
 * Etapa educativa del alumno.
 *
 * [label]          Nombre legible completo (para ajustes, logs, etc.).
 * [chipLabel]      Etiqueta del chip en el selector (agrupa variantes de Grado Básico).
 * [hasFct]         Indica si la etapa tiene FCT (prácticas en empresa).
 * [subjectLabel]   Término local para las materias ("Módulos" en FP, "Asignaturas" en el resto).
 * [defaultThreshold] Umbral de falta sugerido al arrancar (el usuario puede cambiarlo).
 * [defaultCourseEndMonth] Mes habitual en que terminan las clases.
 * [defaultCourseEndDay]   Día habitual de fin de clases.
 */
enum class EducationLevel(
    val label: String,
    val chipLabel: String,
    val hasFct: Boolean,
    val subjectLabel: String,
    val defaultThreshold: Double,
    val defaultCourseEndMonth: Int,
    val defaultCourseEndDay: Int,
) {
    PRIMARIA(
        label = "Primaria",
        chipLabel = "Primaria",
        hasFct = false,
        subjectLabel = "Asignaturas",
        defaultThreshold = 20.0,
        defaultCourseEndMonth = 6,
        defaultCourseEndDay = 22,
    ),
    ESO(
        label = "ESO",
        chipLabel = "ESO",
        hasFct = false,
        subjectLabel = "Asignaturas",
        defaultThreshold = 20.0,
        defaultCourseEndMonth = 6,
        defaultCourseEndDay = 22,
    ),
    BACHILLERATO(
        label = "Bachillerato",
        chipLabel = "Bachillerato",
        hasFct = false,
        subjectLabel = "Asignaturas",
        defaultThreshold = 15.0,
        defaultCourseEndMonth = 5,
        defaultCourseEndDay = 31,
    ),
    /** Grado Básico sin prácticas en empresa. */
    FP_BASICO(
        label = "Grado Básico",
        chipLabel = "Grado Básico",
        hasFct = false,
        subjectLabel = "Módulos",
        defaultThreshold = 15.0,
        defaultCourseEndMonth = 6,
        defaultCourseEndDay = 22,
    ),
    /** Grado Básico con FCT (prácticas en empresa). */
    FP_BASICO_FCT(
        label = "Grado Básico (con FCT)",
        chipLabel = "Grado Básico",
        hasFct = true,
        subjectLabel = "Módulos",
        defaultThreshold = 15.0,
        defaultCourseEndMonth = 2,
        defaultCourseEndDay = 15,
    ),
    FP_MEDIO(
        label = "Grado Medio",
        chipLabel = "Grado Medio",
        hasFct = true,
        subjectLabel = "Módulos",
        defaultThreshold = 15.0,
        defaultCourseEndMonth = 2,
        defaultCourseEndDay = 15,
    ),
    FP_SUPERIOR(
        label = "Grado Superior",
        chipLabel = "Grado Superior",
        hasFct = true,
        subjectLabel = "Módulos",
        defaultThreshold = 15.0,
        defaultCourseEndMonth = 2,
        defaultCourseEndDay = 15,
    );

    /** True si el nivel es cualquier variante de Grado Básico. */
    val isFpBasico: Boolean get() = this == FP_BASICO || this == FP_BASICO_FCT

    /** Texto descriptivo del campo "fecha fin" adaptado a la etapa. */
    val cutoffFieldLabel: String get() = if (hasFct) "Fin de clases (inicio FCT)" else "Fin del curso"

    /** Texto de ayuda para el campo de fecha fin. */
    val cutoffHelpText: String get() = when {
        hasFct -> "Las faltas cuentan hasta el inicio de la FCT (prácticas). Comprueba la fecha:"
        else   -> "Las faltas cuentan hasta el último día de clase. Comprueba la fecha:"
    }
}
