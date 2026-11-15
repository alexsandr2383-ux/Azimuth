package ru.azimut.app.core.sync

import kotlinx.serialization.json.Json
import ru.azimut.app.core.platform.PlatformHttp

/** Percent-encodes a path component for query strings (UTF-8). */
fun encodePath(path: String): String = buildString {
    path.toByteArray(Charsets.UTF_8).forEach { b ->
        val v = b.toInt() and 0xFF
        if (v in '0'.code..'9'.code || v in 'A'.code..'Z'.code || v in 'a'.code..'z'.code ||
            v == '-'.code || v == '_'.code || v == '.'.code
        ) {
            append(v.toChar())
        } else {
            append("%%%02X".format(v))
        }
    }
}

/**
 * Client for the desktop LAN sync server (Settings → Sync → "LAN server").
 * Protocol: header X-Azimut-Token on every request; GET /manifest returns the
 * server's SyncManifest JSON; GET/PUT/DELETE /file?path=... transfer files.
 */
class LanHttpStorage(
    private val baseUrl: String,
    private val token: String,
) : RemoteStorage {
    override val name: String = "LAN"

    private val root = baseUrl.trimEnd('/')

    private fun headers(): Map<String, String> = mapOf("X-Azimut-Token" to token)

    private fun guard(path: String): String? {
        if (path.isBlank() || path.contains("..") || path.startsWith("/")) return null
        return path.replace('\\', '/')
    }

    override suspend fun read(relativePath: String): ByteArray? {
        val path = guard(relativePath) ?: return null
        val resp = PlatformHttp.request("GET", "$root/file?path=${encodePath(path)}", headers())
        if (resp == null || resp.status != 200) return null
        return resp.body
    }

    override suspend fun write(relativePath: String, bytes: ByteArray): Boolean {
        val path = guard(relativePath) ?: return false
        val resp = PlatformHttp.request(
            "PUT",
            "$root/file?path=${encodePath(path)}",
            headers(),
            bytes,
        )
        return resp != null && resp.status == 200
    }

    override suspend fun delete(relativePath: String): Boolean {
        val path = guard(relativePath) ?: return false
        val resp = PlatformHttp.request("DELETE", "$root/file?path=${encodePath(path)}", headers())
        return resp != null && resp.status == 200
    }

    override suspend fun listAll(): List<SyncEntry> {
        val resp = PlatformHttp.request("GET", "$root/manifest", headers()) ?: return emptyList()
        if (resp.status != 200) return emptyList()
        return runCatching {
            Json { ignoreUnknownKeys = true }
                .decodeFromString<SyncManifest>(resp.body.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
                .entries
        }.getOrDefault(emptyList())
    }
}
