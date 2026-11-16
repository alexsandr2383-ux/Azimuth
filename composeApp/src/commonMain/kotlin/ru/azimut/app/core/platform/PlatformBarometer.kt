package ru.azimut.app.core.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Barometric altimeter from the phone pressure sensor, metres above sea level by the
 * standard-atmosphere formula. Raw altitude drifts with weather; the Android actual
 * tracks an offset from GPS altitude whenever a fix carries one, so the reported
 * value stays consistent with the rest of the app.
 */
expect object PlatformBarometer {
    /** False when the device has no pressure sensor (then altitude stays GPS-only). */
    val available: Boolean
    /** Calibrated altitude in metres; null while the sensor is absent or stopped. */
    val altitudeMeters: StateFlow<Double?>
    fun start()
    fun stop()
}
