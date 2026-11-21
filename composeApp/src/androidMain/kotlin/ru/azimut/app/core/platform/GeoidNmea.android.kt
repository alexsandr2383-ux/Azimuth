package ru.azimut.app.core.platform

import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.location.OnNmeaMessageListener

/**
 * Geoid separation N (ellipsoid − sea level) from the receiver's own NMEA GGA sentences.
 * Android < 14 only reports the ellipsoid height in Location.altitude; MSL = altitude − N.
 */
object GeoidNmea {
    @Volatile private var separation: Double? = null
    @Volatile private var updatedAt = 0L
    private var listener: OnNmeaMessageListener? = null
    /** Preview and the recording service both use it; the listener lives while either does. */
    private var users = 0

    /** Latest geoid separation, or null when no GGA arrived in the last 10 minutes. */
    fun current(): Double? =
        separation?.takeIf { System.currentTimeMillis() - updatedAt < 10 * 60_000L }

    @Synchronized
    fun register(manager: LocationManager) {
        if (Build.VERSION.SDK_INT >= 34) return
        users++
        if (listener != null) return
        val l = OnNmeaMessageListener { message, _ -> parse(message) }
        runCatching {
            manager.addNmeaListener(l, Handler(Looper.getMainLooper()))
            listener = l
        }
    }

    @Synchronized
    fun unregister(manager: LocationManager) {
        if (users > 0) users--
        if (users > 0) return
        listener?.let { runCatching { manager.removeNmeaListener(it) } }
        listener = null
    }

    /** $xxGGA,time,lat,N,lon,E,fix,sats,hdop,alt,M,geoidSep,M,... */
    internal fun parse(message: String) {
        val body = message.trim().substringBefore('*')
        val f = body.split(',')
        if (f.size < 12 || !f[0].endsWith("GGA")) return
        if ((f[6].toIntOrNull() ?: 0) == 0) return
        val n = f[11].toDoubleOrNull() ?: return
        if (n in -120.0..120.0) {
            separation = n
            updatedAt = System.currentTimeMillis()
        }
    }
}
