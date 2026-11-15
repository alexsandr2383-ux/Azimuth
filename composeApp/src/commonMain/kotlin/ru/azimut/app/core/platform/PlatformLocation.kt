package ru.azimut.app.core.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform location. Fixes are delivered to TrackRecorder.
 *
 * [start]/[stop] control track recording (background-capable on Android);
 * [startPreview]/[stopPreview] show the current position while the app is visible.
 */
expect object PlatformLocation {
    val isSimulated: Boolean

    /** Satellites used in the current fix; null where the platform has no data. */
    val satellites: StateFlow<Int?>

    fun start()
    fun stop()
    fun startPreview()
    fun stopPreview()

    /** Serial ports for an external NMEA receiver; null where the platform uses its own GPS. */
    fun externalPorts(): List<String>?

    /** Selects the position source: [SIMULATOR_SOURCE] or a serial port name. */
    fun configure(source: String, baud: Int)

    /** Applies recording power settings to the platform GPS update loop. */
    fun applySettings(recordingIntervalSec: Int, batterySaver: Boolean, backgroundRecording: Boolean)
}

const val SIMULATOR_SOURCE = "sim"
