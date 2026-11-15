package ru.azimut.app.core.location

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Minimal NMEA 0183 reader for external GPS receivers.
 * RMC gives position/speed/course/time; GGA adds altitude and HDOP.
 * A fix is emitted for every valid RMC sentence.
 */
class NmeaParser {
    private var altitude: Double? = null
    private var hdop: Float? = null

    /** Returns a fix when [line] completes one, otherwise null. Invalid or foreign lines are ignored. */
    fun feed(line: String): LocationFix? {
        val sentence = checkedBody(line.trim()) ?: return null
        val fields = sentence.split(',')
        val type = fields[0].takeLast(3)
        return when (type) {
            "GGA" -> {
                parseGga(fields)
                null
            }
            "RMC" -> parseRmc(fields)
            else -> null
        }
    }

    private fun parseGga(f: List<String>) {
        val quality = f.getOrNull(6)?.toIntOrNull() ?: 0
        if (quality == 0) {
            altitude = null
            hdop = null
            return
        }
        hdop = f.getOrNull(8)?.toFloatOrNull()
        altitude = f.getOrNull(9)?.toDoubleOrNull()
    }

    private fun parseRmc(f: List<String>): LocationFix? {
        if (f.getOrNull(2) != "A") return null
        val lat = coordinate(f.getOrNull(3), f.getOrNull(4), degreeDigits = 2) ?: return null
        val lon = coordinate(f.getOrNull(5), f.getOrNull(6), degreeDigits = 3) ?: return null
        val speed = f.getOrNull(7)?.toFloatOrNull()?.let { it * KNOTS_TO_MPS }
        val course = f.getOrNull(8)?.toFloatOrNull()
        val time = timeMillis(f.getOrNull(1), f.getOrNull(9))
        return LocationFix(
            lat = lat,
            lon = lon,
            altitude = altitude,
            // Rough but useful: consumer receivers have ~5 m UERE.
            accuracy = hdop?.let { it * UERE_M },
            speed = speed,
            bearing = course,
            timeMillis = time ?: kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
        )
    }

    companion object {
        private const val KNOTS_TO_MPS = 0.514444f
        private const val UERE_M = 5f

        /** Strips `$` and `*hh`; null if the checksum is present and wrong. */
        fun checkedBody(line: String): String? {
            if (!line.startsWith("$")) return null
            val star = line.lastIndexOf('*')
            val body = if (star > 0) line.substring(1, star) else line.substring(1)
            if (star > 0) {
                val expected = line.substring(star + 1).take(2).toIntOrNull(16) ?: return null
                val actual = body.fold(0) { acc, c -> acc xor c.code }
                if (actual != expected) return null
            }
            return body
        }

        /** ddmm.mmmm / dddmm.mmmm + hemisphere → decimal degrees. */
        private fun coordinate(raw: String?, hemisphere: String?, degreeDigits: Int): Double? {
            if (raw.isNullOrBlank() || raw.length < degreeDigits + 2) return null
            val degrees = raw.substring(0, degreeDigits).toIntOrNull() ?: return null
            val minutes = raw.substring(degreeDigits).toDoubleOrNull() ?: return null
            val value = degrees + minutes / 60.0
            return when (hemisphere) {
                "N", "E" -> value
                "S", "W" -> -value
                else -> null
            }
        }

        /** hhmmss(.sss) + ddmmyy in UTC. */
        private fun timeMillis(time: String?, date: String?): Long? {
            if (time == null || date == null || time.length < 6 || date.length != 6) return null
            return runCatching {
                val seconds = time.substring(4).toDouble()
                val yy = date.substring(4, 6).toInt()
                val year = if (yy >= 80) 1900 + yy else 2000 + yy
                val dateTime = LocalDateTime(
                    LocalDate(year, date.substring(2, 4).toInt(), date.substring(0, 2).toInt()),
                    kotlinx.datetime.LocalTime(
                        time.substring(0, 2).toInt(),
                        time.substring(2, 4).toInt(),
                        seconds.toInt(),
                        ((seconds - seconds.toInt()) * 1_000_000_000).toInt(),
                    ),
                )
                dateTime.toInstant(TimeZone.UTC).toEpochMilliseconds()
            }.getOrNull()
        }
    }
}
