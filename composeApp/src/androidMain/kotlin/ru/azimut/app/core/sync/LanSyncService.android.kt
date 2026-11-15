package ru.azimut.app.core.sync

/** Android never serves LAN sync (the phone is the client); stub for the expect. */
actual object LanSyncService {
    actual fun start(): LanServerInfo? = null
    actual fun stop() {}
    actual val running: Boolean get() = false
}
