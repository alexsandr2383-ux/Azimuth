package ru.azimut.app.core.sync

/**
 * LAN sync server (desktop runs it; Android has no need to serve). The UI shows
 * the address and token, the phone connects via [LanHttpStorage].
 */
data class LanServerInfo(
    val port: Int,
    val token: String,
    val addresses: List<String>,
) {
    fun addressLine(): String = addresses.firstOrNull()?.let { "$it:$port" } ?: "localhost:$port"
}

expect object LanSyncService {
    /** Starts the server; null on failure (port busy, etc.). */
    fun start(): LanServerInfo?
    fun stop()
    val running: Boolean
}
