package ru.azimut.app.core.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

actual object PlatformHttp {
    actual suspend fun getText(url: String): String? {
        val resp = request("GET", url) ?: return null
        if (resp.status !in 200..299) return null
        return resp.body.toString(Charsets.UTF_8)
    }

    actual suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        rangeFrom: Long,
    ): HttpResp? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Azimuth/0.1 (offline navigation)")
                if (rangeFrom > 0) setRequestProperty("Range", "bytes=$rangeFrom-")
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Length", body.size.toString())
                    body.inputStream().use { input -> outputStream.use { input.copyTo(it) } }
                }
            }
            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val bytes = ByteArrayOutputStream().also { out -> stream?.use { it.copyTo(out) } }.toByteArray()
                val headerNames = connection.headerFields ?: emptyMap()
                val flat = headerNames.entries
                    .filter { it.key != null }
                    .associate { it.key.lowercase() to (it.value.firstOrNull() ?: "") }
                HttpResp(status, flat, bytes)
            } finally {
                connection.disconnect()
            }
        }.onFailure {
            // Silent nulls look like "no network" in callers; keep the real cause visible.
            android.util.Log.w("AzimuthHttp", "request $method $url failed", it)
        }.getOrNull()
    }
}
