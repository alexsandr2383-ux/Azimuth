package ru.azimut.app.core.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Device heading from the magnetometer, in degrees from true north (declination applied).
 * [heading] is null while stopped or when the device has no compass.
 */
expect object PlatformHeading {
    val available: Boolean
    val heading: StateFlow<Float?>
    fun start(smoothing: Boolean)
    fun stop()
}
