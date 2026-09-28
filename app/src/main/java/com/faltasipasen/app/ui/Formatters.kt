package com.faltasipasen.app.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish: Locale = Locale.forLanguageTag("es-ES")
private val madrid: ZoneId = ZoneId.of("Europe/Madrid")
private val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", spanish)
private val dayHeader: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", spanish)
private val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", spanish)
private val dash = Regex("\\s*-\\s*")

fun formatNumber(value: Double): String = DecimalFormat("0.#", DecimalFormatSymbols(spanish)).format(value)

fun formatHours(value: Double): String = "${formatNumber(value)} h"

fun formatLongDate(date: LocalDate): String = date.format(longDate)

fun formatMillis(millis: Long): String = Instant.ofEpochMilli(millis).atZone(madrid).format(dateTime)

fun formatDayHeader(date: LocalDate): String = date.format(dayHeader).replaceFirstChar { it.titlecase(spanish) }

fun formatTramo(tramo: String): String = tramo.trim().replace(dash, " – ")

fun relativeTime(thenMillis: Long, nowMillis: Long): String {
    val minutes = (nowMillis - thenMillis).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "ahora mismo"
        minutes < 60 -> "hace $minutes min"
        minutes < 24 * 60 -> "hace ${minutes / 60} h"
        else -> {
            val days = minutes / (24 * 60)
            if (days == 1L) "hace 1 día" else "hace $days días"
        }
    }
}
