package ru.azimut.app.core.dem

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ru.azimut.app.core.astro.SunState
import ru.azimut.app.core.astro.TerrainSunTimes

/**
 * DEM-driven warnings during an Explorer Mode (Susanin) episode. Each warning fires
 * once while its condition holds; it can fire again only after the condition clears
 * (episode semantics). A ford warning is intentionally NOT implemented from DEM:
 * height grids carry no water-crossing attribute, so there is nothing honest to add
 * on top of the router's fordCount, which is surfaced elsewhere.
 */
class SusaninWatch(private val dem: DemStore) {
    enum class Kind { SUNSET_SOON, DEAD_END_VALLEY }

    data class Warning(val kind: Kind, val messageRu: String, val messageEn: String)

    private val active = mutableSetOf<Kind>()
    var lastWarning: Warning? = null
        private set

    fun reset() {
        active.clear()
        lastWarning = null
    }

    /**
     * Feed the current fix; returns a warning when a new condition appears.
     * [movingCourseDeg] null = stationary, course checks are skipped.
     */
    fun check(
        lat: Double,
        lon: Double,
        timeZone: TimeZone,
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
        movingCourseDeg: Double?,
    ): Warning? {
        val sunsetWarning = checkSunset(lat, lon, timeZone, nowMillis)
        if (sunsetWarning != null) return sunsetWarning
        return if (movingCourseDeg != null) {
            checkDeadEnd(lat, lon, movingCourseDeg)
        } else {
            null
        }
    }

    private fun checkSunset(lat: Double, lon: Double, timeZone: TimeZone, nowMillis: Long): Warning? {
        val date = kotlinx.datetime.Instant.fromEpochMilliseconds(nowMillis)
            .toLocalDateTime(timeZone).date
        val daylight = runCatching {
            TerrainSunTimes.forDate(date, lat, lon, timeZone, dem)
        }.getOrNull() ?: return null
        if (daylight.nominal.state != SunState.NORMAL) return clear(Kind.SUNSET_SOON)
        val sunset = daylight.effective.sunset ?: return null
        val sunsetMillis = ru.azimut.app.core.astro.TimeUtil.millis(
            date,
            kotlinx.datetime.LocalTime(sunset.hour, sunset.minute, 0),
            timeZone,
        ) ?: return null
        val remainingMin = (sunsetMillis - nowMillis) / 60_000.0
        if (remainingMin in 0.0..SUNSET_WARN_MIN) {
            if (Kind.SUNSET_SOON in active) return null
            active.add(Kind.SUNSET_SOON)
            val mins = remainingMin.toInt().coerceAtLeast(0)
            val noteRu = if (daylight.terrainAdjusted) " (с учётом рельефа)" else " (без учёта рельефа — нет DEM)"
            val noteEn = if (daylight.terrainAdjusted) " (terrain-aware)" else " (no DEM: horizon ignored)"
            return Warning(
                Kind.SUNSET_SOON,
                "Закат через $mins мин$noteRu. Планируй лагерь или выход к дороге.",
                "Sunset in $mins min$noteEn. Plan camp or head for a road.",
            ).also { lastWarning = it }
        }
        return clear(Kind.SUNSET_SOON)
    }

    /**
     * Dead-end valley: the terrain ahead rises steeply in every direction of the
     * movement sector (±40°) and never comes back down within the look range.
     */
    private fun checkDeadEnd(lat: Double, lon: Double, courseDeg: Double): Warning? {
        val height0 = dem.heightAt(lat, lon) ?: return clear(Kind.DEAD_END_VALLEY)
        val escapedDirs = ArrayList<Int>()
        for (delta in -40..40 step 8) {
            val azimuth = courseDeg + delta
            var uphill = 0.0
            var downAhead = false
            var d = 200.0
            while (d <= VALLEY_RANGE_M) {
                val dLat = (d * kotlin.math.cos(azimuth * kotlin.math.PI / 180.0)) / 111_320.0
                val dLon = (d * kotlin.math.sin(azimuth * kotlin.math.PI / 180.0)) /
                    (111_320.0 * kotlin.math.cos(lat * kotlin.math.PI / 180.0))
                val h = dem.heightAt(lat + dLat, lon + dLon)
                if (h != null) {
                    if (h < height0 + VALLEY_DOWN_M && d > 800) downAhead = true
                    if (h - height0 > uphill) uphill = h - height0
                }
                d += 200.0
            }
            // A direction is "open" when the terrain eventually descends again.
            if (downAhead) escapedDirs.add(delta)
        }
        return if (escapedDirs.isEmpty()) {
            if (Kind.DEAD_END_VALLEY in active) return null
            active.add(Kind.DEAD_END_VALLEY)
            Warning(
                Kind.DEAD_END_VALLEY,
                "Впереди подъём во всех направлениях — похоже на тупиковую долину.",
                "Terrain rises ahead in every direction — looks like a dead-end valley.",
            ).also { lastWarning = it }
        } else {
            clear(Kind.DEAD_END_VALLEY)
        }
    }

    private fun clear(kind: Kind): Warning? {
        active.remove(kind)
        return null
    }

    companion object {
        private const val SUNSET_WARN_MIN = 45.0
        private const val VALLEY_RANGE_M = 2_500.0
        private const val VALLEY_DOWN_M = 40.0
    }
}
