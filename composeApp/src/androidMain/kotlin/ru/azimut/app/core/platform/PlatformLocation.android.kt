package ru.azimut.app.core.platform

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.azimut.app.core.location.LocationFix
import ru.azimut.app.core.location.TrackRecorder
import ru.azimut.app.core.location.TrackingService

actual object PlatformLocation {
    actual val isSimulated: Boolean = false

    private val satelliteFlow = MutableStateFlow<Int?>(null)
    actual val satellites: StateFlow<Int?> = satelliteFlow
    private var gnssCallback: GnssStatus.Callback? = null

    actual fun start() {
        val context = AndroidApp.context
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, TrackingService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    actual fun stop() {
        AndroidApp.context.stopService(Intent(AndroidApp.context, TrackingService::class.java))
    }

    /** Position for the visible map; while recording, TrackingService is the one that stores fixes. */
    private val previewListener = LocationListener { location: Location ->
        if (TrackRecorder.state.value.recording) return@LocationListener
        TrackRecorder.onPassiveFix(
            LocationFix(
                lat = location.latitude,
                lon = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                speed = if (location.hasSpeed()) location.speed else null,
                bearing = if (location.hasBearing()) location.bearing else null,
                timeMillis = if (location.time > 0) location.time else System.currentTimeMillis(),
            )
        )
    }
    private var previewing = false

    actual fun startPreview() {
        val context = AndroidApp.context
        if (previewing) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, previewIntervalMs, 0f, previewListener, Looper.getMainLooper())
            previewing = true
        }
        runCatching {
            if (gnssCallback == null) {
                val callback = object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(status: GnssStatus) {
                        var used = 0
                        for (i in 0 until status.satelliteCount) {
                            if (status.usedInFix(i)) used++
                        }
                        satelliteFlow.value = used
                    }
                }
                manager.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper()))
                gnssCallback = callback
            }
        }
    }

    actual fun externalPorts(): List<String>? = null

    actual fun configure(source: String, baud: Int) = Unit

    @Volatile internal var recordingIntervalMs: Long = 1_000L
    @Volatile internal var backgroundRecording: Boolean = true
    private var previewIntervalMs: Long = 1_000L

    actual fun applySettings(recordingIntervalSec: Int, batterySaver: Boolean, backgroundRecording: Boolean) {
        val interval = recordingIntervalSec.coerceIn(1, 3_600) * 1_000L
        recordingIntervalMs = if (batterySaver) maxOf(interval, 5_000L) else interval
        this.backgroundRecording = backgroundRecording
        previewIntervalMs = if (batterySaver) maxOf(recordingIntervalMs, 5_000L) else 1_000L
        if (previewing) {
            val manager = AndroidApp.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            runCatching {
                manager.removeUpdates(previewListener)
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    previewIntervalMs,
                    0f,
                    previewListener,
                    Looper.getMainLooper(),
                )
            }
        }
    }

    actual fun stopPreview() {
        if (!previewing && gnssCallback == null) return
        val manager = AndroidApp.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching { manager.removeUpdates(previewListener) }
        gnssCallback?.let { callback ->
            runCatching { manager.unregisterGnssStatusCallback(callback) }
        }
        gnssCallback = null
        satelliteFlow.value = null
        previewing = false
    }
}
