package com.faltasipasen.app.scraper

import com.faltasipasen.app.domain.Absence
import com.faltasipasen.app.domain.CalendarDay
import com.faltasipasen.app.domain.ScheduleSlot
import com.faltasipasen.app.scraper.ScrapeException.Reason
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

@Serializable
private data class SlotDto(val subject: String, val day: Int, val hours: Double = 1.0, val abbr: String = "")

@Serializable
private data class DayDto(val date: String, val lectivo: Boolean)

@Serializable
private data class AbsenceDto(
    val date: String,
    val subject: String,
    val hours: Double = 1.0,
    val justified: Boolean = false,
    val tramo: String = "",
    val fullDay: Boolean = false,
)

@Serializable
private data class AbsencesDto(val absences: List<AbsenceDto>, val skipped: List<String> = emptyList())

/** Absences read from iPasen, plus a description of each row that could not be read. */
data class AbsenceRead(val absences: List<Absence>, val skipped: List<String>)

object ScrapeParsers {
    private val json = Json { ignoreUnknownKeys = true }
    private val whitespace = Regex("\\s+")

    fun checkOk(raw: String, step: String) {
        payload(raw, step)
    }

    fun parseSchedule(raw: String): List<ScheduleSlot> =
        decode<List<SlotDto>>(raw, STEP_SCHEDULE).map { dto ->
            if (dto.day !in 1..7) invalid(STEP_SCHEDULE, "día de la semana fuera de rango: ${dto.day}")
            ScheduleSlot(subject(dto.subject, STEP_SCHEDULE), dto.day, hours(dto.hours, STEP_SCHEDULE), dto.abbr.trim(), displayName(dto.subject))
        }

    fun parseCalendar(raw: String): List<CalendarDay> =
        decode<List<DayDto>>(raw, STEP_CALENDAR).map { CalendarDay(date(it.date, STEP_CALENDAR), it.lectivo) }

    /**
     * One unreadable row must not cost the whole sync: it is set aside in [AbsenceRead.skipped] (as the
     * scraper does for rows it cannot read) and the rest are kept.
     * A "Día completo" row names no subject: it is an absence from every class of that weekday, and it
     * replaces any hourly rows of the same date so nothing counts twice.
     */
    fun parseAbsences(raw: String, schedule: List<ScheduleSlot> = emptyList()): AbsenceRead {
        val dto = decode<AbsencesDto>(raw, STEP_ABSENCES)
        val skipped = dto.skipped.toMutableList()
        val rows = dto.absences.mapNotNull { row ->
            try {
                row to date(row.date, STEP_ABSENCES)
            } catch (e: ScrapeException) {
                skipped += describe(row, e)
                null
            }
        }
        val fullDays = rows.filter { it.first.fullDay }.map { it.second }.toSet()
        val absences = rows.flatMap { (row, date) ->
            if (row.fullDay) {
                return@flatMap schedule.filter { it.dayOfWeek == date.dayOfWeek.value }.map { slot ->
                    Absence(date, slot.subject, slot.hours, row.justified, row.tramo.trim(), slot.name)
                }
            }
            if (date in fullDays) return@flatMap emptyList()
            try {
                listOf(
                    Absence(
                        date = date,
                        subject = subject(row.subject, STEP_ABSENCES),
                        hours = hours(row.hours, STEP_ABSENCES),
                        justified = row.justified,
                        tramo = row.tramo.trim(),
                        name = displayName(row.subject),
                    ),
                )
            } catch (e: ScrapeException) {
                skipped += describe(row, e)
                emptyList()
            }
        }
        return AbsenceRead(absences, skipped)
    }

    private fun describe(row: AbsenceDto, e: ScrapeException): String =
        listOf(row.date, row.tramo, row.subject).joinToString(" | ") { displayName(it) } +
            ": " + e.message.orEmpty().removePrefix("$STEP_ABSENCES: ")

    fun displayName(name: String): String = name.trim().replace(whitespace, " ")

    fun normalizeSubject(name: String): String = displayName(name).uppercase(Locale.ROOT)

    private inline fun <reified T> decode(raw: String, step: String): T {
        val data = payload(raw, step)
        return try {
            json.decodeFromJsonElement<T>(data)
        } catch (e: IllegalArgumentException) {
            invalid(step, "formato inesperado")
        }
    }

    private fun payload(raw: String, step: String): JsonElement {
        val obj = try {
            json.parseToJsonElement(raw) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: invalid(step, "la respuesta no es un objeto JSON")

        val ok = (obj["ok"] as? JsonPrimitive)?.booleanOrNull ?: false
        if (!ok) {
            val error = (obj["error"] as? JsonPrimitive)?.contentOrNull ?: "error desconocido"
            val reason = when {
                flag(obj, "loginFailed") -> Reason.LOGIN_FAILED
                flag(obj, "timeout") -> Reason.TIMEOUT
                else -> Reason.PARSE_ERROR
            }
            throw ScrapeException(reason, "$step: $error")
        }
        return obj["data"] ?: JsonNull
    }

    private fun flag(obj: JsonObject, name: String): Boolean = (obj[name] as? JsonPrimitive)?.booleanOrNull == true

    private fun subject(value: String, step: String): String =
        normalizeSubject(value).ifEmpty { invalid(step, "asignatura vacía") }

    private fun hours(value: Double, step: String): Double =
        if (value > 0.0 && value <= 12.0) value else invalid(step, "duración no válida: $value")

    private fun date(value: String, step: String): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            invalid(step, "fecha no válida: $value")
        }

    private fun invalid(step: String, detail: String): Nothing = throw ScrapeException(Reason.PARSE_ERROR, "$step: $detail")

    private const val STEP_SCHEDULE = "horario"
    private const val STEP_CALENDAR = "calendario"
    private const val STEP_ABSENCES = "faltas"
}
