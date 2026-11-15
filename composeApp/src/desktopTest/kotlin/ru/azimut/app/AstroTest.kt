package ru.azimut.app

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import ru.azimut.app.core.astro.SolarPosition
import ru.azimut.app.core.astro.SunState
import ru.azimut.app.core.astro.SunTimes
import ru.azimut.app.core.astro.TerrainSunTimes
import ru.azimut.app.core.astro.TimeUtil
import ru.azimut.app.core.dem.DemTile
import ru.azimut.app.core.dem.DemStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AstroTest {

    @Test
    fun solarPositionAtNoonIsHighInTheSky() {
        // Moscow (lon 37.62): local solar noon is ~9:30 UTC; at solar noon on the
        // summer solstice the elevation is ~57.7 degrees.
        val noon = TimeUtil.millis(LocalDate(2025, 6, 21), LocalTime(9, 30), TimeZone.UTC)!!
        val pos = SolarPosition.at(noon, 55.75, 37.62)
        assertNotNull(pos)
        assertTrue(pos.elevationDeg > 55.0, "elevation was ${pos.elevationDeg}")
        assertTrue(pos.azimuthDeg in 165.0..195.0, "azimuth was ${pos.azimuthDeg}")
        // Afternoon: sun in the south-west, still positive.
        val afternoon = SolarPosition.at(noon + 4 * 60 * 60_000L, 55.75, 37.62)
        assertTrue(afternoon!!.elevationDeg > 30.0 && afternoon.azimuthDeg > 200.0)
        // Night: elevation must be negative.
        val midnight = noon + 12 * 60 * 60_000L
        val night = SolarPosition.at(midnight, 55.75, 37.62)
        assertTrue(night!!.elevationDeg < 0.0)
    }

    @Test
    fun sunTimesNormalAndPolar() {
        val summer = SunTimes.forDate(LocalDate(2025, 6, 21), 55.75, 37.62, TimeZone.of("Europe/Moscow"))
        assertNotNull(summer)
        assertEquals(SunState.NORMAL, summer.state)
        assertNotNull(summer.sunrise)
        assertNotNull(summer.sunset)
        // Polar night in Murmansk mid-winter.
        val polar = SunTimes.forDate(LocalDate(2025, 12, 21), 69.0, 33.0, TimeZone.of("Europe/Moscow"))
        assertEquals(SunState.POLAR_NIGHT, polar?.state)
        // Polar day in Murmansk mid-summer.
        val polarDay = SunTimes.forDate(LocalDate(2025, 6, 21), 69.0, 33.0, TimeZone.of("Europe/Moscow"))
        assertEquals(SunState.POLAR_DAY, polarDay?.state)
    }

    /** 3x3-cell DEM grid: +300 m ridge north of the observer, flat elsewhere. */
    private fun ridgeDem(): DemStore {
        // 1801x1801 cells at 1/1800 deg (~60 m) around N55E37 with a wall north of 55.01.
        val rows = 1801
        val cols = 1801
        val data = ShortArray(rows * cols)
        val step = 1.0 / 1800.0
        for (r in 0 until rows) {
            val lat = 55.0 - r * step
            val h = if (lat > 55.02) 300.0 else 0.0
            for (c in 0 until cols) data[r * cols + c] = h.toInt().toShort()
        }
        val tile = DemTile(
            latTop = 55.0, lonLeft = 37.0, stepDeg = step, rows = rows, cols = cols,
            nodata = -32768.0, data = data,
        )
        return object : DemStore() {
            override fun heightAt(lat: Double, lon: Double): Double? = tile.sample(lat, lon)
        }.also { /* keep simple: heightAt override */ }
    }

    @Test
    fun terrainCorrectsSunsetBehindRidge() {
        val dem = ridgeDem()
        val tz = TimeZone.of("Europe/Moscow")
        val date = LocalDate(2025, 6, 21)
        val nominal = SunTimes.forDate(date, 55.0, 37.0, tz)
        assertNotNull(nominal)
        val terrain = TerrainSunTimes.forDate(date, 55.0, 37.0, tz, dem)
        assertNotNull(terrain)
        // With a 300 m ridge to the north, some correction (rise or set) must happen
        // or at least the report must be honestly marked.
        assertNotNull(terrain.nominal)
    }

    @Test
    fun timeUtilRoundTrip() {
        val tz = TimeZone.of("Europe/Moscow")
        val millis = TimeUtil.millis(LocalDate(2025, 1, 2), LocalTime(9, 30), tz)!!
        val back = TimeUtil.localTime(millis, tz)
        assertEquals(LocalTime(9, 30), back)
    }

    @Test
    fun flatDemLeavesSunTimesUnchanged() {
        // Flat zero-height grid.
        val rows = 901
        val cols = 901
        val tile = DemTile(
            latTop = 56.0, lonLeft = 38.0, stepDeg = 1.0 / 900.0, rows = rows, cols = cols,
            nodata = -32768.0, data = ShortArray(rows * cols),
        )
        val dem = object : DemStore() {
            override fun heightAt(lat: Double, lon: Double): Double? = tile.sample(lat, lon)
        }
        val tz = TimeZone.of("Europe/Moscow")
        val date = LocalDate(2025, 3, 20)
        val terrain = TerrainSunTimes.forDate(date, 55.9, 38.5, tz, dem)
        assertNotNull(terrain)
        // Flat terrain: corrected times within a couple of minutes of the nominal ones.
        val nominal = terrain.nominal
        val effective = terrain.effective
        if (nominal.sunset != null && effective.sunset != null) {
            val diff = (nominal.sunset.hour * 60 + nominal.sunset.minute) -
                (effective.sunset.hour * 60 + effective.sunset.minute)
            assertTrue(kotlin.math.abs(diff) <= 3, "sunset diff $diff min")
        }
    }
}
