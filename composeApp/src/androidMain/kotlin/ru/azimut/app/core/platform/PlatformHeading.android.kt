package ru.azimut.app.core.platform

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.azimut.app.core.location.TrackRecorder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

actual object PlatformHeading : SensorEventListener {
    private val sensorManager: SensorManager
        get() = AndroidApp.context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    actual val available: Boolean
        get() = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

    private val _heading = MutableStateFlow<Float?>(null)
    actual val heading: StateFlow<Float?> = _heading

    private var smoothing = true
    private var running = false
    private var smoothSin = 0.0
    private var smoothCos = 1.0
    private var declination = 0f
    private var declinationAt = 0L

    actual fun start(smoothing: Boolean) {
        this.smoothing = smoothing
        if (running) return
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
        running = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    actual fun stop() {
        if (!running) return
        sensorManager.unregisterListener(this)
        running = false
        _heading.value = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val rotation = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val remapped = FloatArray(9)
        // Keep "up" of the screen as the reference direction in landscape too.
        val (axisX, axisY) = when (displayRotation()) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(remapped, orientation)
        val magnetic = Math.toDegrees(orientation[0].toDouble())
        val trueHeading = Math.toRadians(magnetic + currentDeclination())

        // Low-pass on the unit vector so 359° → 1° does not swing the map around.
        val alpha = if (smoothing) 0.15 else 1.0
        smoothSin += alpha * (sin(trueHeading) - smoothSin)
        smoothCos += alpha * (cos(trueHeading) - smoothCos)
        val degrees = (Math.toDegrees(atan2(smoothSin, smoothCos)) + 360.0) % 360.0
        _heading.value = degrees.toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Magnetic declination for the last known position, refreshed every 10 minutes. */
    private fun currentDeclination(): Float {
        val now = System.currentTimeMillis()
        if (now - declinationAt > 10 * 60_000L) {
            val fix = TrackRecorder.state.value.lastFix
            if (fix != null) {
                declination = GeomagneticField(
                    fix.lat.toFloat(),
                    fix.lon.toFloat(),
                    (fix.altitude ?: 0.0).toFloat(),
                    now,
                ).declination
                declinationAt = now
            }
        }
        return declination
    }

    /** Works with the application context (Context.getDisplay() would throw there). */
    private fun displayRotation(): Int =
        (AndroidApp.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
}
