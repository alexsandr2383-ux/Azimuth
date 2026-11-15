package ru.azimut.app.core.astro

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

enum class SunState { NORMAL, POLAR_DAY, POLAR_NIGHT }

data class Daylight(
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val state: SunState,
) {
    /** Length of the day in minutes; null during polar day/night. */
    val durationMinutes: Int?
        get() {
            val rise = sunrise ?: return null
            val set = sunset ?: return null
            return ((set.hour * 60 + set.minute) - (rise.hour * 60 + rise.minute) + 1_440) % 1_440
        }
}

/** NOAA solar calculator; no terrain horizon (DEM) correction yet. */
object SunTimes {
    private const val ZENITH = 90.833

    fun forDate(
        date: LocalDate,
        lat: Double,
        lon: Double,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Daylight? {
        if (lat !in -89.0..89.0 || lon !in -180.0..180.0) return null
        val cosH = cosHourAngle(date, lat)
        val state = when {
            cosH > 1.0 -> SunState.POLAR_NIGHT
            cosH < -1.0 -> SunState.POLAR_DAY
            else -> SunState.NORMAL
        }
        return Daylight(
            sunrise = event(date, lat, lon, timeZone, sunrise = true),
            sunset = event(date, lat, lon, timeZone, sunrise = false),
            state = state,
        )
    }

    private fun event(
        date: LocalDate,
        lat: Double,
        lon: Double,
        timeZone: TimeZone,
        sunrise: Boolean,
    ): LocalTime? {
        val day = date.dayOfYear.toDouble()
        val lngHour = lon / 15.0
        val t = day + ((if (sunrise) 6.0 else 18.0) - lngHour) / 24.0
        val meanAnomaly = 0.9856 * t - 3.289
        var trueLongitude = meanAnomaly +
            1.916 * sin(rad(meanAnomaly)) +
            0.020 * sin(rad(2 * meanAnomaly)) +
            282.634
        trueLongitude = normalize(trueLongitude)
        var rightAscension = deg(kotlin.math.atan(0.91764 * tan(rad(trueLongitude))))
        rightAscension = normalize(rightAscension)
        val lQuadrant = floor(trueLongitude / 90.0) * 90.0
        val raQuadrant = floor(rightAscension / 90.0) * 90.0
        rightAscension = (rightAscension + (lQuadrant - raQuadrant)) / 15.0
        val sinDec = 0.39782 * sin(rad(trueLongitude))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(rad(ZENITH)) - sinDec * sin(rad(lat))) / (cosDec * cos(rad(lat)))
        if (cosH > 1.0 || cosH < -1.0) return null
        val hourAngle = if (sunrise) {
            360.0 - deg(acos(cosH))
        } else {
            deg(acos(cosH))
        } / 15.0
        val utcHours = hourAngle + rightAscension - 0.06571 * t - 6.622 - lngHour
        val wrapped = ((utcHours % 24.0) + 24.0) % 24.0
        val minutes = (wrapped * 60.0 + 0.5).toInt().coerceIn(0, 1_439)
        return localTime(date, minutes, timeZone)
    }

    private fun cosHourAngle(date: LocalDate, lat: Double): Double {
        val day = date.dayOfYear.toDouble()
        val lngHour = 0.0
        val t = day + (12.0 - lngHour) / 24.0
        val meanAnomaly = 0.9856 * t - 3.289
        val trueLongitude = normalize(
            meanAnomaly + 1.916 * sin(rad(meanAnomaly)) +
                0.020 * sin(rad(2 * meanAnomaly)) + 282.634,
        )
        val sinDec = 0.39782 * sin(rad(trueLongitude))
        val cosDec = cos(asin(sinDec))
        return (cos(rad(ZENITH)) - sinDec * sin(rad(lat))) / (cosDec * cos(rad(lat)))
    }

    private fun localTime(date: LocalDate, minutesFromUtcMidnight: Int, timeZone: TimeZone): LocalTime {
        val instant: Instant = date.atStartOfDayIn(TimeZone.UTC)
            .plus(minutesFromUtcMidnight, DateTimeUnit.MINUTE)
        return instant.toLocalDateTime(timeZone).time
    }

    private fun normalize(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

    private fun rad(degrees: Double): Double = degrees * PI / 180.0

    private fun deg(radians: Double): Double = radians * 180.0 / PI
}
