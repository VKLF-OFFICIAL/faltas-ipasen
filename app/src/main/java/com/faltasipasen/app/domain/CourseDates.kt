package com.faltasipasen.app.domain

import java.time.LocalDate

object CourseDates {
    private const val COURSE_START_MONTH = 9

    /** 15 de febrero del curso en vigor (el curso empieza en septiembre). */
    fun defaultCutoff(today: LocalDate): LocalDate {
        val year = if (today.monthValue >= COURSE_START_MONTH) today.year + 1 else today.year
        return LocalDate.of(year, 2, 15)
    }

    fun courseStartFor(cutoff: LocalDate): LocalDate = LocalDate.of(cutoff.year - 1, COURSE_START_MONTH, 1)
}
