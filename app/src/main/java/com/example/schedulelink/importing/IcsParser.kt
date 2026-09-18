package com.example.schedulelink.importing

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ImportedEvent(
    val title: String,
    val memo: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime
)

/**
 * 最低限のiCalendar(RFC5545)パーサー。VEVENTのSUMMARY/DESCRIPTION/DTSTART/DTENDのみを読み取る。
 * 繰り返し予定(RRULE)は展開せず、最初の1件のみを取り込む簡易実装。
 */
object IcsParser {

    private val basicDateFormatter = DateTimeFormatter.BASIC_ISO_DATE
    private val basicDateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun parse(rawText: String): List<ImportedEvent> {
        val lines = unfold(rawText)
        val events = mutableListOf<ImportedEvent>()

        var inEvent = false
        var summary = ""
        var description = ""
        var dtStart: LocalDateTime? = null
        var dtEnd: LocalDateTime? = null
        var isAllDay = false

        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> {
                    inEvent = true
                    summary = ""
                    description = ""
                    dtStart = null
                    dtEnd = null
                    isAllDay = false
                }
                line == "END:VEVENT" -> {
                    val start = dtStart
                    if (inEvent && start != null) {
                        val end = dtEnd ?: start.plusHours(1)
                        events += ImportedEvent(
                            title = summary.ifBlank { "(タイトルなし)" },
                            memo = description,
                            date = start.toLocalDate(),
                            startTime = if (isAllDay) LocalTime.MIN else start.toLocalTime(),
                            endTime = if (isAllDay) LocalTime.of(23, 59) else end.toLocalTime()
                        )
                    }
                    inEvent = false
                }
                inEvent -> {
                    val separatorIndex = line.indexOf(':')
                    if (separatorIndex <= 0) continue
                    val rawKey = line.substring(0, separatorIndex)
                    val value = line.substring(separatorIndex + 1).trim()
                    when (rawKey.substringBefore(';')) {
                        "SUMMARY" -> summary = unescape(value)
                        "DESCRIPTION" -> description = unescape(value)
                        "DTSTART" -> {
                            isAllDay = rawKey.contains("VALUE=DATE") || value.length == 8
                            dtStart = runCatching { parseDateTime(value) }.getOrNull()
                        }
                        "DTEND" -> dtEnd = runCatching { parseDateTime(value) }.getOrNull()
                    }
                }
            }
        }
        return events
    }

    private fun unfold(rawText: String): List<String> {
        val rawLines = rawText.replace("\r\n", "\n").split("\n")
        val result = mutableListOf<String>()
        for (raw in rawLines) {
            val line = raw.trimEnd('\r')
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.size - 1] = result.last() + line.substring(1)
            } else if (line.isNotEmpty()) {
                result += line
            }
        }
        return result
    }

    private fun unescape(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")

    private fun parseDateTime(value: String): LocalDateTime {
        val clean = value.removeSuffix("Z")
        return if (clean.length <= 8) {
            LocalDate.parse(clean, basicDateFormatter).atStartOfDay()
        } else {
            val local = LocalDateTime.parse(clean, basicDateTimeFormatter)
            if (value.endsWith("Z")) {
                local.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            } else {
                local
            }
        }
    }
}
