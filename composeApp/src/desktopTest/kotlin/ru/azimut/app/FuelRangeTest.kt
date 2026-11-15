package ru.azimut.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ru.azimut.app.core.fuel.fuelRange
import ru.azimut.app.core.model.GeoPoint

class FuelRangeTest {

    /** East–west line at 47°N; 0.01° of longitude ≈ 0.759 km. */
    private fun geoLine(steps: Int): List<GeoPoint> =
        (0..steps).map { GeoPoint(47.0, 41.0 + it * 0.01) }

    @Test
    fun rangeAndCutsOnLongRoute() {
        // Tank 16 l, 5.0 l/100 km → 320 km; reserve 2 l → usable 14 l → 280 km.
        val r = fuelRange(geoLine(530), 16.0, 5.0, 2.0)
        assertEquals(320.0, r.rangeKm!!, 0.5)
        assertTrue(r.routeKm!! > 400.0, "route=${r.routeKm}")
        assertNotNull(r.reserveCut)
        assertTrue(r.reserveCutKm!! in 279.0..281.5, "reserve=${r.reserveCutKm}")
        assertNotNull(r.emptyCut)
        assertTrue(r.emptyCutKm!! in 319.0..321.5, "empty=${r.emptyCutKm}")
    }

    @Test
    fun routeFitsIntoTank() {
        val r = fuelRange(geoLine(100), 16.0, 5.0, 2.0)
        assertNull(r.reserveCut)
        assertNull(r.emptyCut)
        assertEquals(320.0, r.rangeKm!!, 0.5)
    }

    @Test
    fun zeroReserveOnlyDryTank() {
        val r = fuelRange(geoLine(530), 16.0, 5.0, 0.0)
        assertNull(r.reserveCut)
        assertNull(r.reserveCutKm)
        assertNotNull(r.emptyCut)
    }

    @Test
    fun invalidInputsOff() {
        val r = fuelRange(geoLine(10), 0.0, 5.0, 2.0)
        assertNull(r.rangeKm)
        assertNull(r.routeKm)
    }
}
