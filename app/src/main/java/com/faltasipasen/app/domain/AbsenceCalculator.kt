package com.faltasipasen.app.domain

import java.time.LocalDate

object AbsenceCalculator {
    const val GLOBAL_LABEL = "Total"

    fun calculate(
        schedule: List<ScheduleSlot>,
        calendar: List<CalendarDay>,
        absences: List<Absence>,
        cutoff: LocalDate,
        today: LocalDate,
    ): AbsenceStats {
        val courseStart = CourseDates.courseStartFor(cutoff)
        val countedUntil = minOf(today, cutoff)

        val courseDays = calendar
            .filter { it.lectivo && !it.date.isBefore(courseStart) && !it.date.isAfter(cutoff) }
            .map { it.date }
            .distinct()
        val counted = absences.filter { !it.date.isBefore(courseStart) && !it.date.isAfter(countedUntil) }

        val labels = (schedule.map { it.subject } + counted.map { it.subject }).distinct()
        val subjects = labels.map { subject ->
            val slots = schedule.filter { it.subject == subject }
            val courseHours = courseDays.sumOf { day ->
                slots.filter { it.dayOfWeek == day.dayOfWeek.value }.sumOf { it.hours }
            }
            val own = counted.filter { it.subject == subject }
            HoursStats(
                label = subject,
                courseHours = courseHours,
                absentHours = own.sumOf { it.hours },
                unjustifiedHours = own.filterNot { it.justified }.sumOf { it.hours },
                abbreviation = slots.firstOrNull { it.abbreviation.isNotEmpty() }?.abbreviation.orEmpty(),
                name = slots.firstOrNull { it.name.isNotEmpty() }?.name
                    ?: own.firstOrNull { it.name.isNotEmpty() }?.name.orEmpty(),
            )
        }.sortedWith(compareByDescending<HoursStats> { it.percent }.thenBy { it.label })

        val global = HoursStats(
            label = GLOBAL_LABEL,
            courseHours = subjects.sumOf { it.courseHours },
            absentHours = subjects.sumOf { it.absentHours },
            unjustifiedHours = subjects.sumOf { it.unjustifiedHours },
        )
        return AbsenceStats(
            global = global,
            subjects = subjects,
            courseStart = courseStart,
            cutoff = cutoff,
            countedUntil = countedUntil,
            lectivoDaysElapsed = courseDays.count { !it.isAfter(countedUntil) },
            lectivoDaysTotal = courseDays.size,
        )
    }
}
