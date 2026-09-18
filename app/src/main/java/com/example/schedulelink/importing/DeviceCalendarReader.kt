package com.example.schedulelink.importing

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

data class DeviceCalendar(val id: Long, val displayName: String, val accountName: String)

/**
 * 端末に同期済みのカレンダー(Googleカレンダーアプリなど)から予定を読み取る。
 * 繰り返し予定も展開されるInstancesテーブルを使う。
 */
object DeviceCalendarReader {

    fun readCalendars(contentResolver: ContentResolver): List<DeviceCalendar> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        val result = mutableListOf<DeviceCalendar>()
        contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                result += DeviceCalendar(
                    id = cursor.getLong(0),
                    displayName = cursor.getString(1) ?: "(名称未設定のカレンダー)",
                    accountName = cursor.getString(2).orEmpty()
                )
            }
        }
        return result
    }

    fun readEvents(
        contentResolver: ContentResolver,
        calendarIds: Set<Long>,
        rangeStart: LocalDate,
        rangeEndExclusive: LocalDate
    ): List<ImportedEvent> {
        if (calendarIds.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        val beginMillis = rangeStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = rangeEndExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, beginMillis)
        ContentUris.appendId(uriBuilder, endMillis)

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID
        )
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${calendarIds.joinToString(",") { "?" }})"
        val args = calendarIds.map { it.toString() }.toTypedArray()

        val result = mutableListOf<ImportedEvent>()
        contentResolver.query(
            uriBuilder.build(),
            projection,
            selection,
            args,
            "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(0) ?: "(タイトルなし)"
                val description = cursor.getString(1).orEmpty()
                val beginMillisRow = cursor.getLong(2)
                val endMillisRow = cursor.getLong(3)
                val isAllDay = cursor.getInt(4) != 0

                result += if (isAllDay) {
                    val date = Instant.ofEpochMilli(beginMillisRow).atZone(ZoneOffset.UTC).toLocalDate()
                    ImportedEvent(title, description, date, LocalTime.MIN, LocalTime.of(23, 59))
                } else {
                    val start = Instant.ofEpochMilli(beginMillisRow).atZone(zone).toLocalDateTime()
                    val end = Instant.ofEpochMilli(endMillisRow).atZone(zone).toLocalDateTime()
                    ImportedEvent(title, description, start.toLocalDate(), start.toLocalTime(), end.toLocalTime())
                }
            }
        }
        return result
    }
}
