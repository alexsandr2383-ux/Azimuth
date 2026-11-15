package ru.azimut.app.core.astro

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sun azimuth/elevation for a moment of time — the NOAA solar position algorithm
 * (fractional-year Fourier series, accurate to ~0.1° / ~1 min). Angles in degrees;
 * azimuth clockwise from north. Refraction near the horizon via the standard
 * Bennett polynomial; all branches share one /3600 so the value stays sane.
 */
object SolarPosition {
    data class Position(val azimuthDeg: Double, val elevationDeg: Double)

    fun at(timeMillis: Long, lat: Double, lon: Double): Position? {
        if (lat !in -89.0..89.0 || lon !in -180.0..180.0) return null
        val utc = Instant.fromEpochMilliseconds(timeMillis).toLocalDateTime(TimeZone.UTC)
        val dayOfYear = utc.date.dayOfYear - 1 +
            (utc.hour + utc.minute / 60.0 + utc.second / 3600.0 - 12.0) / 24.0
        val gamma = 2.0 * PI / 365.0 * dayOfYear

        // Equation of time, minutes.
        val eqtime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )

        // Declination, radians.
        val declRad = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) +
            0.00148 * sin(3 * gamma)

        // Local solar time in minutes; 4 minutes of solar time per longitude degree.
        val utcMinutes = utc.hour * 60.0 + utc.minute + utc.second / 60.0
        val solarMinutes = utcMinutes + 4.0 * lon + eqtime
        val minutesFromNoon = ((solarMinutes % 1_440.0) + 1_440.0) % 1_440.0 - 720.0
        val hourAngle = rad(minutesFromNoon / 4.0) // 15 deg/hour

        val latRad = rad(lat)
        val cosZenith = sin(latRad) * sin(declRad) + cos(latRad) * cos(declRad) * cos(hourAngle)
        val zenith = acos(cosZenith.coerceIn(-1.0, 1.0))
        val elevation = 90.0 - deg(zenith)

        // Atmospheric refraction (degrees), Bennett polynomial; clamped below -5 deg.
        val refract = when {
            elevation > 5.0 -> (58.1 / tan(rad(elevation)) -
                0.07 / tan(rad(elevation)).let { it * it * it }) / 3600.0
            elevation > -0.575 -> {
                val x = elevation + 0.575
                (1735.0 - 518.2 * x + 103.4 * x * x -
                    12.79 * x * x * x + 0.711 * x * x * x * x) / 3600.0
            }
            else -> (-20.774 / tan(rad(if (elevation > -5.0) elevation else -5.0))) / 3600.0
        }

        // Azimuth from north, clockwise. Morning (H<0) mirrors afternoon (H>0).
        val denom = cos(latRad) * sin(zenith)
        val azimuth = if (denom < 1e-9) {
            // Sun circling the zenith: hour-angle form keeps the sign stable.
            val y = sin(hourAngle)
            val x = cos(hourAngle) * sin(latRad) - tan(declRad) * cos(latRad)
            ((atan2(y, x) * 180.0 / PI) + 360.0) % 360.0
        } else {
            val cosAz = (sin(declRad) * cos(latRad) - cos(declRad) * sin(latRad) * cos(hourAngle)) /
                denom
            val az = acos(cosAz.coerceIn(-1.0, 1.0)) * 180.0 / PI
            if (hourAngle > 0) (360.0 - az) % 360.0 else az
        }

        return Position(azimuthDeg = azimuth, elevationDeg = elevation + refract)
    }

    private fun rad(degrees: Double): Double = degrees * PI / 180.0

    private fun deg(radians: Double): Double = radians * 180.0 / PI
}
