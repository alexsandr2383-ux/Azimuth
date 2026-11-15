package ru.azimut.app.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** PCs have no magnetometer: map rotation uses the GPS course only. */
actual object PlatformHeading {
    actual val available: Boolean = false
    actual val heading: StateFlow<Float?> = MutableStateFlow(null)
    actual fun start(smoothing: Boolean) = Unit
    actual fun stop() = Unit
}
