package ru.azimut.app.core.astro

import ru.azimut.app.core.dem.DemStore
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

/**
 * Terrain horizon: for each azimuth, the max angle of sight to the terrain up to
 * [MAX_RANGE_M]. Angle > 0 means a ridge hides the true horizon in that direction.
 */
class HorizonProfile(
    val observerLat: Double,
    val observerLon: Double,
    val observerHeightM: Double,
    val stepDeg: Int,
) {
    private val angles = DoubleArray(360 / stepDeg)

    internal fun set(index: Int, value: Double) {
        if (index in angles.indices) angles[index] = value
    }

    /** Max terrain angle (deg) along [azimuthDeg], linearly interpolated between samples. */
    fun angleAt(azimuthDeg: Double): Double {
        val normalized = ((azimuthDeg % 360.0) + 360.0) % 360.0
        val exact = normalized / stepDeg
        val idx = exact.toInt().coerceIn(0, angles.size - 1)
        val next = (idx + 1) % angles.size
        val frac = exact - idx
        return angles[idx] * (1 - frac) + angles[next] * frac
    }

    companion object {
        const val MAX_RANGE_M = 30_000.0
        const val SAMPLE_STEP_M = 150.0

        /** Horizon dip below the horizontal from earth curvature at range d, degrees. */
        fun dipDegrees(distanceMeters: Double): Double =
            (distanceMeters / (2.0 * 6_371_000.0)) * 180.0 / PI
    }
}

/**
 * Builds the horizon profile; when [centerAzimuth] is given, only that +-30 deg sector
 * is computed (the rest is filled with -90 = unobstructed). Returns null when the DEM
 * does not cover the observer point, so callers can fall back honestly.
 */
fun buildHorizon(
    dem: DemStore,
    lat: Double,
    lon: Double,
    centerAzimuth: Double?,
    sectorHalfWidth: Double = 30.0,
): HorizonProfile? {
    val height0 = dem.heightAt(lat, lon) ?: return null
    val step = 2
    val profile = HorizonProfile(lat, lon, height0, step)
    var anyData = false
    for (i in 0 until 360 / step) {
        val azimuth = i * step
        val inSector = centerAzimuth == null ||
            angularDiff(azimuth.toDouble(), centerAzimuth) <= sectorHalfWidth
        var maxAngle = -90.0
        if (inSector) {
            var d = HorizonProfile.SAMPLE_STEP_M
            while (d <= HorizonProfile.MAX_RANGE_M) {
                val dLat = (d * cos(azimuth * PI / 180.0)) / 111_320.0
                val dLon = (d * sin(azimuth * PI / 180.0)) / (111_320.0 * cos(lat * PI / 180.0))
                val h = dem.heightAt(lat + dLat, lon + dLon)
                if (h != null) {
                    anyData = true
                    val angle = atan((h - height0) / d) * 180.0 / PI -
                        HorizonProfile.dipDegrees(d)
                    if (angle > maxAngle) maxAngle = angle
                }
                d += HorizonProfile.SAMPLE_STEP_M
            }
        }
        profile.set(i, maxAngle)
    }
    return if (anyData) profile else null
}

/** Smallest absolute difference between two azimuths, degrees. */
fun angularDiff(a: Double, b: Double): Double {
    val d = ((a - b) % 360.0 + 360.0) % 360.0
    return if (d > 180.0) 360.0 - d else d
}

/**
 * Sunrise/sunset corrected for the terrain horizon. Nominal values come from
 * [SunTimes]; with DEM coverage the correction scans minute by minute in a window
 * around the nominal event for the moment the sun really clears (rise) or is
 * blocked by (set) the ridge. Polar states pass through untouched.
 */
object TerrainSunTimes {
    data class TerrainDaylight(
        val nominal: Daylight,
        /** Corrected rise time; null when not computable (no DEM / polar). */
        val sunrise: kotlinx.datetime.LocalTime?,
        /** Corrected set time; null when not computable. */
        val sunset: kotlinx.datetime.LocalTime?,
        /** True when at least one of the events was adjusted using DEM data. */
        val terrainAdjusted: Boolean,
    ) {
        /** The daylight to plan around: corrected values where known, nominal otherwise. */
        val effective: Daylight
            get() = Daylight(
                sunrise = sunrise ?: nominal.sunrise,
                sunset = sunset ?: nominal.sunset,
                state = nominal.state,
            )
    }

    private const val SEARCH_WINDOW_MIN = 120

    fun forDate(
        date: kotlinx.datetime.LocalDate,
        lat: Double,
        lon: Double,
        timeZone: kotlinx.datetime.TimeZone,
        dem: DemStore,
    ): TerrainDaylight? {
        val nominal = SunTimes.forDate(date, lat, lon, timeZone) ?: return null
        if (nominal.state != SunState.NORMAL) {
            return TerrainDaylight(nominal, null, null, false)
        }

        val sunsetCorrection = nominal.sunset?.let { set ->
            correctEvent(date, lat, lon, set, timeZone, dem, sunrise = false)
        }
        val sunriseCorrection = nominal.sunrise?.let { rise ->
            correctEvent(date, lat, lon, rise, timeZone, dem, sunrise = true)
        }
        val adjusted = sunsetCorrection?.second == true || sunriseCorrection?.second == true
        return TerrainDaylight(
            nominal = nominal,
            sunrise = sunriseCorrection?.first,
            sunset = sunsetCorrection?.first,
            terrainAdjusted = adjusted,
        )
    }

    /**
     * Returns (corrected local time, adjusted) — adjusted=false means the horizon
     * profile had no effect within the search window (flat terrain or no DEM there).
     */
    private fun correctEvent(
        date: kotlinx.datetime.LocalDate,
        lat: Double,
        lon: Double,
        nominal: kotlinx.datetime.LocalTime,
        timeZone: kotlinx.datetime.TimeZone,
        dem: DemStore,
        sunrise: Boolean,
    ): Pair<kotlinx.datetime.LocalTime, Boolean>? {
        val nominalMillis = TimeUtil.millis(date, nominal, timeZone) ?: return null

        // Full-circle horizon: the sun azimuth sweeps far beyond a 30-degree sector
        // across the 4-hour window (e.g. 221..322 at lat 56 in spring), so a narrow
        // sector would leave the window edges with a -90 threshold and "always visible".
        val profile = buildHorizon(dem, lat, lon, null) ?: return null

        // Self-calibration: at the nominal event the sun sits exactly on the nominal
        // horizon (refraction + half-disc included). The offset pins that definition,
        // so flat terrain reproduces the nominal time and ridges only move it earlier
        // (sunset) or later (sunrise) — never the opposite.
        val nominalPos = SolarPosition.at(nominalMillis, lat, lon)
        val offset = nominalPos?.let { it.elevationDeg - profile.angleAt(it.azimuthDeg) } ?: 0.0
        val visibility = ArrayList<Pair<Long, Boolean>>()
        for (delta in -SEARCH_WINDOW_MIN..SEARCH_WINDOW_MIN) {
            val t = nominalMillis + delta * 60_000L
            val pos = SolarPosition.at(t, lat, lon) ?: continue
            val visible = pos.elevationDeg > profile.angleAt(pos.azimuthDeg) + offset
            visibility.add(t to visible)
        }
        if (visibility.isEmpty()) return null

        val correctedMillis: Long = if (sunrise) {
            // Sun climbs: the first minute it is actually visible.
            visibility.firstOrNull { it.second }?.first
        } else {
            // Sun sinks: the last minute still visible.
            visibility.lastOrNull { it.second }?.first
        } ?: return null

        val adjusted = correctedMillis != nominalMillis
        return TimeUtil.localTime(correctedMillis, timeZone) to adjusted
    }
}
