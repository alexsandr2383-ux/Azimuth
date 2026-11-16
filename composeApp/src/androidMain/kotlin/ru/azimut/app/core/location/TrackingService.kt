package ru.azimut.app.core.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ru.azimut.app.MainActivity
import ru.azimut.app.R
import ru.azimut.app.core.model.AppSettings
import ru.azimut.app.core.platform.AndroidApp
import ru.azimut.app.core.platform.PlatformLocation
import ru.azimut.app.core.storage.SettingsStore
import ru.azimut.app.core.storage.TrackStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TrackingService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // The service can be restarted (START_STICKY) in a fresh process without MainActivity.
        AndroidApp.context = applicationContext
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val settings = runCatching { SettingsStore().load() }.getOrDefault(AppSettings())
        TrackRecorder.configure(settings.recordingIntervalSec, settings.batterySaver, settings.skipStops)
        PlatformLocation.applySettings(
            settings.recordingIntervalSec,
            settings.batterySaver,
            settings.backgroundRecording,
        )
        // Barometric altimeter: fallback height source for fixes without GPS altitude.
        ru.azimut.app.core.platform.PlatformBarometer.start()
        createChannel()
        startForegroundCompat()
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // Stay foregrounded until the save finishes: stopping the service first
                // would leave the deferred write unprotected against process kill.
                TrackRecorder.stopAndSaveAsync { stopSelf() }
            }
        }
    }
    private var screenReceiverRegistered = false

    /** With background recording off, locking the screen ends the recording instead of continuing. */
    private fun refreshScreenReceiver() {
        val wanted = !PlatformLocation.backgroundRecording
        if (wanted == screenReceiverRegistered) return
        if (wanted) {
            runCatching {
                ContextCompat.registerReceiver(
                    this,
                    screenOffReceiver,
                    IntentFilter(Intent.ACTION_SCREEN_OFF),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                screenReceiverRegistered = true
            }
        } else {
            runCatching { unregisterReceiver(screenOffReceiver) }
            screenReceiverRegistered = false
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Azimuth tracking",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "GPS track recording"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Азимут")
        .setContentText("Запись GPS-трека")
        .setSmallIcon(R.drawable.ic_stat_azimut)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .build()

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startUpdates() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        // GPS only: network fixes (100 m+ error) would put zigzags into the recorded track.
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                PlatformLocation.recordingIntervalMs,
                0f,
                this,
            )
        } catch (_: Exception) {
        }
        try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?.let { TrackRecorder.onPassiveFix(it.toFix()) }
        } catch (_: Exception) {
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onLocationChanged(location: Location) {
        TrackRecorder.onFix(location.toFix())
    }

    private fun Location.toFix() = LocationFix(
        lat = latitude,
        lon = longitude,
        // GPS altitude first; when this fix has none, fall back to the calibrated
        // barometer (the sensor also refreshes its offset from any GPS altitude here).
        altitude = if (hasAltitude()) {
            ru.azimut.app.core.platform.PlatformBarometer.calibrate(altitude)
            altitude
        } else {
            ru.azimut.app.core.platform.PlatformBarometer.altitudeMeters.value
        },
        accuracy = if (hasAccuracy()) accuracy else null,
        speed = if (hasSpeed()) speed else null,
        bearing = if (hasBearing()) bearing else null,
        timeMillis = if (time > 0) time else System.currentTimeMillis(),
    )

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!TrackRecorder.state.value.recording) {
            // Restarted after the process was killed: continue the interrupted recording.
            // The autosave GPX can be megabytes — parse it off the main thread.
            serviceScope.launch {
                val autosave = runCatching { TrackStore().loadAutosave() }.getOrNull()
                if (autosave == null || !TrackRecorder.restore(autosave)) {
                    stopSelf()
                    return@launch
                }
                runCatching { locationManager.removeUpdates(this@TrackingService) }
                startUpdates()
                refreshScreenReceiver()
            }
            return START_STICKY
        }
        // Applies a new interval/power mode too: every start re-requests GPS updates.
        runCatching { locationManager.removeUpdates(this) }
        startUpdates()
        refreshScreenReceiver()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(this) }
        ru.azimut.app.core.platform.PlatformBarometer.stop()
        if (screenReceiverRegistered) runCatching { unregisterReceiver(screenOffReceiver) }
        screenReceiverRegistered = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "azimuth_tracking"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "ru.azimut.app.STOP_TRACKING"
    }
}
