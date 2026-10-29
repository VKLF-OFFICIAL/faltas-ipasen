package com.faltasipasen.app.domain

/** Asignaturas convalidadas: no cuentan ni en horas de curso ni en faltas. [convalidated] son claves normalizadas. */
object Convalidations {
    fun exclude(
        schedule: List<ScheduleSlot>,
        absences: List<Absence>,
        convalidated: Set<String>,
    ): Pair<List<ScheduleSlot>, List<Absence>> {
        if (convalidated.isEmpty()) return schedule to absences
        return schedule.filterNot { it.subject in convalidated } to absences.filterNot { it.subject in convalidated }
    }
}
