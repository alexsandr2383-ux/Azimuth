package ru.azimut.app.core.fuel

import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.distanceMeters

/**
 * Fuel-range "ramps" along a planned route: how far the full tank carries the rider
 * and where the route line crosses the reserve and the dry-tank marks.
 */
data class FuelRangeResult(
    /** Distance the full tank covers (tank / consumption × 100 km); null when inputs invalid. */
    val rangeKm: Double?,
    /** Total planned route distance, km. */
    val routeKm: Double?,
    /** Route point where fuel reaches the reserve; null when reserve is 0 or the route fits. */
    val reserveCut: GeoPoint?,
    /** Distance from route start to [reserveCut], km. */
    val reserveCutKm: Double?,
    /** Route point where the tank runs dry; null when the route fits into the tank. */
    val emptyCut: GeoPoint?,
    /** Distance from route start to [emptyCut], km. */
    val emptyCutKm: Double?,
)

/**
 * Pure computation for the fuel ramps; callers run it on Dispatchers.Default.
 * Settings convention: tank/reserve of 0 (or empty) means the calculation is off.
 */
fun fuelRange(
    routePoints: List<GeoPoint>,
    tankLiters: Double,
    consumptionL100: Double,
    reserveLiters: Double,
): FuelRangeResult {
    if (tankLiters <= 0.0 || consumptionL100 <= 0.0 || routePoints.size < 2) {
        return FuelRangeResult(null, null, null, null, null, null)
    }
    var totalMeters = 0.0
    for (i in 1 until routePoints.size) {
        totalMeters += distanceMeters(routePoints[i - 1], routePoints[i])
    }
    val emptyMeters = tankLiters / consumptionL100 * 100.0 * 1000.0
    val reserveMeters = if (reserveLiters > 0.0) {
        ((tankLiters - reserveLiters).coerceAtLeast(0.0)) / consumptionL100 * 100.0 * 1000.0
    } else {
        -1.0
    }

    var acc = 0.0
    var reserveCut: GeoPoint? = null
    var reserveCutKm: Double? = null
    var emptyCut: GeoPoint? = null
    var emptyCutKm: Double? = null
    for (i in 1 until routePoints.size) {
        acc += distanceMeters(routePoints[i - 1], routePoints[i])
        if (reserveMeters >= 0.0 && reserveCut == null && acc >= reserveMeters) {
            reserveCut = routePoints[i]
            reserveCutKm = acc / 1000.0
        }
        if (emptyCut == null && acc >= emptyMeters) {
            emptyCut = routePoints[i]
            emptyCutKm = acc / 1000.0
        }
    }
    return FuelRangeResult(
        rangeKm = emptyMeters / 1000.0,
        routeKm = totalMeters / 1000.0,
        reserveCut = reserveCut,
        reserveCutKm = reserveCutKm,
        emptyCut = emptyCut,
        emptyCutKm = emptyCutKm,
    )
}
