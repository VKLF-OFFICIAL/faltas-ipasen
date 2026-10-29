@file:OptIn(ExperimentalJsExport::class)

package com.faltasipasen.app.web

import com.faltasipasen.app.domain.AbsenceCalculator
import com.faltasipasen.app.domain.AlertFormatter
import com.faltasipasen.app.domain.Convalidations
import com.faltasipasen.app.domain.CourseDates
import com.faltasipasen.app.domain.EducationLevel
import com.faltasipasen.app.domain.HistoryBuilder
import com.faltasipasen.app.domain.HoursStats
import com.faltasipasen.app.domain.RiskLevel
import com.faltasipasen.app.scraper.ScrapeException
import com.faltasipasen.app.scraper.ScrapeParsers
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Puerta de entrada de la web al mismo cálculo que el APK. Entradas y salidas en JSON. */
@JsExport
class FaltasWeb private constructor() {
    companion object {
        @JsStatic
        fun niveles(todayIso: String): String {
        val today = LocalDate.parse(todayIso)
        return buildJsonArray {
            EducationLevel.entries.forEach { level ->
                addJsonObject {
                    put("id", level.name)
                    put("label", level.label)
                    put("threshold", level.defaultThreshold)
                    put("cutoff", CourseDates.defaultCutoff(today, level).toString())
                    put("cutoffLabel", level.cutoffFieldLabel)
                    put("subjectLabel", level.subjectLabel)
                }
            }
        }.toString()
    }

        @JsStatic
        fun calcular(
        scheduleRaw: String,
        calendarRaw: String,
        absencesRaw: String,
        cutoffIso: String,
        todayIso: String,
        thresholdPercent: Double,
        convalidatedJson: String,
    ): String {
        try {
            val cutoff = LocalDate.parse(cutoffIso)
            val today = LocalDate.parse(todayIso)
            val fullSchedule = ScrapeParsers.parseSchedule(scheduleRaw)
            val calendar = ScrapeParsers.parseCalendar(calendarRaw)
            val read = ScrapeParsers.parseAbsences(absencesRaw, fullSchedule)
            val keys = parseKeys(convalidatedJson)
            val (schedule, absences) = Convalidations.exclude(fullSchedule, read.absences, keys)
            val result = AbsenceCalculator.calculate(schedule, calendar, absences, cutoff, today)
            val history = HistoryBuilder.build(schedule, absences, cutoff, today)
            return buildJsonObject {
                put("global", statsJson(result.global, thresholdPercent))
                putJsonArray("subjects") { result.subjects.forEach { add(statsJson(it, thresholdPercent)) } }
                put("courseStart", result.courseStart.toString())
                put("cutoff", result.cutoff.toString())
                put("countedUntil", result.countedUntil.toString())
                put("lectivoDaysElapsed", result.lectivoDaysElapsed)
                put("lectivoDaysTotal", result.lectivoDaysTotal)
                putJsonArray("convalidated") {
                    keys.forEach { key ->
                        val known = fullSchedule.any { it.subject == key } || read.absences.any { it.subject == key }
                        if (known) addJsonObject {
                            put("label", key)
                            put("abbreviation", fullSchedule.firstOrNull { it.subject == key && it.abbreviation.isNotEmpty() }?.abbreviation.orEmpty())
                            put("name", fullSchedule.firstOrNull { it.subject == key && it.name.isNotEmpty() }?.name
                                ?: read.absences.firstOrNull { it.subject == key && it.name.isNotEmpty() }?.name.orEmpty())
                        }
                    }
                }
                putJsonArray("skipped") { read.skipped.forEach { add(it) } }
                putJsonArray("history") {
                    history.forEach { day ->
                        addJsonObject {
                            put("date", day.date.toString())
                            putJsonArray("entries") {
                                day.entries.forEach { e ->
                                    addJsonObject {
                                        put("tramo", e.tramo)
                                        put("subjectKey", e.subjectKey)
                                        put("label", e.label)
                                        put("name", e.name)
                                        put("hours", e.hours)
                                        put("justified", e.justified)
                                    }
                                }
                            }
                        }
                    }
                }
            }.toString()
        } catch (e: ScrapeException) {
            throw Error(e.message ?: "error al leer iPasen")
        } catch (e: IllegalArgumentException) {
            throw Error("fecha no válida: ${e.message}")
        }
    }

        private fun parseKeys(json: String): Set<String> {
            val arr = try { Json.parseToJsonElement(json) as? JsonArray } catch (e: Exception) { null }
                ?: throw IllegalArgumentException("convalidadas no válidas")
            return arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toCollection(LinkedHashSet())
        }

        private fun statsJson(s: HoursStats, threshold: Double): JsonObject = buildJsonObject {
        put("label", s.label)
        put("abbreviation", s.abbreviation)
        put("name", s.name)
        put("shortLabel", s.shortLabel)
        put("courseHours", s.courseHours)
        put("absentHours", s.absentHours)
        put("unjustifiedHours", s.unjustifiedHours)
        put("justifiedHours", s.justifiedHours)
        put("percent", s.percent)
        put("percentText", AlertFormatter.percent(s.percent))
        put("marginHours", s.marginHours(threshold))
        put("risk", RiskLevel.of(s.percent, threshold).name)
        }
    }
}
