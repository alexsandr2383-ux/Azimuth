package ru.azimut.app.core.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow

actual object PlatformBarometer : SensorEventListener {
    private var manager: SensorManager? = null
    private var sensor: Sensor? = null
    private var listening = false

    private val _altitude = MutableStateFlow<Double?>(null)
    actual val altitudeMeters: StateFlow<Double?> = _altitude

    actual val available: Boolean
        get() = sensor != null

    // Baro reads sea-level pressure minus current weather deviation; this offset,
    // refreshed from every GPS altitude, absorbs that drift (keeps the baro value
    // consistent with what the rest of the app reports).
    @Volatile private var gpsOffsetMeters = Double.NaN

    actual fun start() {
        val m = AndroidApp.context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val s = m.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return
        manager = m
        sensor = s
        if (!listening) {
            m.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
            listening = true
        }
    }

    actual fun stop() {
        manager?.unregisterListener(this)
        listening = false
        _altitude.value = null
    }

    /** Refresh the offset from the latest GPS altitude (called on each fix that has one). */
    fun calibrate(gpsAltitude: Double) {
        _altitude.value?.let { baro -> gpsOffsetMeters = gpsAltitude - baro }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PRESSURE) return
        val pressure = event.values[0].toDouble() // hPa
        val raw = 44330.0 * (1.0 - (pressure / 1013.25).pow(0.1903))
        val offset = if (gpsOffsetMeters.isNaN()) 0.0 else gpsOffsetMeters
        _altitude.value = raw + offset
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
