package ru.azimut.app.core.astro

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Small date/time helpers shared by sun-time code and sync. */
object TimeUtil {
    fun millis(date: LocalDate, time: LocalTime, timeZone: TimeZone): Long? = runCatching {
        localDateTime(date, time).toInstant(timeZone).toEpochMilliseconds()
    }.getOrNull()

    fun millisUtc(date: LocalDate, time: LocalTime): Long? = runCatching {
        localDateTime(date, time).toInstant(TimeZone.UTC).toEpochMilliseconds()
    }.getOrNull()

    fun localTime(millis: Long, timeZone: TimeZone): LocalTime =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).time
}

internal fun localDateTime(date: LocalDate, time: LocalTime): LocalDateTime = LocalDateTime(
    date.year, date.month, date.dayOfMonth,
    time.hour, time.minute, time.second, time.nanosecond,
)
