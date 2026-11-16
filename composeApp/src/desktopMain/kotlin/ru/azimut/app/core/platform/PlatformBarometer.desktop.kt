package ru.azimut.app.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Desktop has no pressure sensor; altitude stays GPS/NMEA-only. */
actual object PlatformBarometer {
    actual val available: Boolean = false
    private val _altitude = MutableStateFlow<Double?>(null)
    actual val altitudeMeters: StateFlow<Double?> = _altitude
    actual fun start() = Unit
    actual fun stop() = Unit
}
