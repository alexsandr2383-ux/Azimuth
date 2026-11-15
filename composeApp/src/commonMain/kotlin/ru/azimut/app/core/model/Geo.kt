package ru.azimut.app.core.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val lat1 = a.lat * PI / 180.0
    val lat2 = b.lat * PI / 180.0
    val dLat = (b.lat - a.lat) * PI / 180.0
    val dLon = (b.lon - a.lon) * PI / 180.0
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
}

/**
 * Shortest distance from [point] to the polyline [line] in meters.
 * A local planar approximation is enough for hit-testing a tap against track lines.
 */
fun distanceToPolylineMeters(point: GeoPoint, line: List<GeoPoint>): Double {
    if (line.isEmpty()) return Double.MAX_VALUE
    if (line.size == 1) return distanceMeters(point, line.first())
    val metersPerDegree = PI * EARTH_RADIUS_M / 180.0
    val lonScale = cos(point.lat * PI / 180.0)
    val origin = line.first()
    val px = (point.lon - origin.lon) * metersPerDegree * lonScale
    val py = (point.lat - origin.lat) * metersPerDegree
    var best = Double.MAX_VALUE
    for (i in 1 until line.size) {
        val ax = (line[i - 1].lon - origin.lon) * metersPerDegree * lonScale
        val ay = (line[i - 1].lat - origin.lat) * metersPerDegree
        val bx = (line[i].lon - origin.lon) * metersPerDegree * lonScale
        val by = (line[i].lat - origin.lat) * metersPerDegree
        val vx = bx - ax
        val vy = by - ay
        val wx = px - ax
        val wy = py - ay
        val len2 = vx * vx + vy * vy
        val t = if (len2 == 0.0) 0.0 else ((wx * vx + wy * vy) / len2).coerceIn(0.0, 1.0)
        val dx = wx - t * vx
        val dy = wy - t * vy
        val d = sqrt(dx * dx + dy * dy)
        if (d < best) best = d
    }
    return best
}

fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double {
    val lat1 = a.lat * PI / 180.0
    val lat2 = b.lat * PI / 180.0
    val dLon = (b.lon - a.lon) * PI / 180.0
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return ((atan2(y, x) * 180.0 / PI) + 360.0) % 360.0
}

fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${meters.toInt()} м"
    else -> {
        val km = meters / 1000.0
        val rounded = (km * 10).toInt() / 10.0
        "$rounded км"
    }
}

fun formatSpeed(metersPerSecond: Float?): String {
    if (metersPerSecond == null || metersPerSecond < 0f) return "—"
    val kmh = metersPerSecond * 3.6f
    return "${(kmh * 10).toInt() / 10.0} км/ч"
}

fun formatCoord(lat: Double, lon: Double, dms: Boolean = false): String {
    if (!dms) return "${fixed(lat, 5)}, ${fixed(lon, 5)}"
    return "${formatDms(lat, true)} ${formatDms(lon, false)}"
}

/** Plain decimal notation (never "1.0E-4"), as GPX and other parsers expect. */
fun formatFixed(value: Double, decimals: Int): String = fixed(value, decimals)

private fun fixed(value: Double, decimals: Int): String {
    if (value.isNaN() || value.isInfinite()) return "—"
    val factor = 10.0.pow(decimals).toLong()
    val scaled = (value * factor).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val absScaled = abs(scaled)
    val whole = absScaled / factor
    val fraction = absScaled % factor
    return "$sign$whole.${fraction.toString().padStart(decimals, '0')}"
}

private fun formatDms(value: Double, isLat: Boolean): String {
    val abs = abs(value)
    val deg = abs.toInt()
    val minFull = (abs - deg) * 60
    val min = minFull.toInt()
    val sec = (minFull - min) * 60
    val hemi = if (isLat) (if (value >= 0) "N" else "S") else (if (value >= 0) "E" else "W")
    return "${deg}°${min}'${fixed(sec, 1)}\"$hemi"
}

/**
 * Parses "lat, lon" in decimal degrees or "55\u00B007'23.4"N 37\u00B027'18.6"E style DMS.
 * Returns (lat, lon) or null. Accepts comma/semicolon/space separators, comma
 * decimal marks; detects swapped lon/lat order when possible.
 */
fun parseCoordPair(text: String): Pair<Double, Double>? {
    val clean = text.trim()
    if (clean.isEmpty()) return null

    // DMS: two hemisphere-lettered components (Latin and Cyrillic С/Ю/В/З).
    val dmsRegex = Regex(
        "(\\d{1,3})[\\u00B0d\\s]+(?:(\\d{1,2})['\\u2032m\\s]+)?" +
            "(?:(\\d{1,2}(?:[.,]\\d+)?)[\"\\u2033\"\\s]+)?([NSEW\\u0421\\u042E\\u0412\\u0417nsew\\u0441\\u044E\\u0432\\u0437])",
        RegexOption.IGNORE_CASE,
    )
    val dmsMatches = dmsRegex.findAll(clean).toList()
    if (dmsMatches.size >= 2) {
        fun value(m: kotlin.text.MatchResult): Double {
            val deg = m.groupValues[1].toDouble()
            val min = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val sec = m.groupValues[3].replace(',', '.').toDoubleOrNull() ?: 0.0
            return deg + min / 60.0 + sec / 3600.0
        }
        fun hemisphere(m: kotlin.text.MatchResult): Char = when (m.groupValues[4].uppercase().first()) {
            'N' -> 'N'; 'S' -> 'S'; 'E' -> 'E'; 'W' -> 'W'
            '\u0421' -> 'N'; '\u042E' -> 'S'; '\u0412' -> 'E'; '\u0417' -> 'W'
            else -> '?'
        }
        val lat = dmsMatches.firstOrNull { hemisphere(it) == 'N' || hemisphere(it) == 'S' }
        val lon = dmsMatches.firstOrNull { hemisphere(it) == 'E' || hemisphere(it) == 'W' }
        if (lat != null && lon != null) {
            val latV = value(lat) * (if (hemisphere(lat) == 'S') -1 else 1)
            val lonV = value(lon) * (if (hemisphere(lon) == 'W') -1 else 1)
            if (latV in -90.0..90.0 && lonV in -180.0..180.0) return latV to lonV
        }
        return null
    }

    // Decimal pair: "55.123, 37.456", "55,5,37,6" (comma decimals), "55.123 37.456".
    val pairRegex = Regex(
        "^\\s*(-?\\d+(?:[.,]\\d+)?)\\s*[;,\\s]\\s*(-?\\d+(?:[.,]\\d+)?)\\s*$",
    )
    val pair = pairRegex.find(clean) ?: return null
    val first = pair.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val second = pair.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
    return when {
        first in -90.0..90.0 && second in -180.0..180.0 -> first to second
        second in -90.0..90.0 && first in -180.0..180.0 -> second to first // swapped lon/lat
        else -> null
    }
}

fun compassDirection(bearing: Double): String {
    val dirs = listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")
    val idx = (((bearing % 360) + 360) % 360 / 45.0).toInt() % 8
    return dirs[idx]
}
