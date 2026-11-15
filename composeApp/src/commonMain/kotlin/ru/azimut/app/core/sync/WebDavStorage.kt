package ru.azimut.app.core.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.azimut.app.core.platform.PlatformHttp

/**
 * WebDAV storage over PlatformHttp: PROPFIND for listing, GET/PUT/DELETE for files.
 * Credentials come from [SecretStore]; only https/http URLs with basic auth.
 */
class WebDavStorage(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
) : RemoteStorage {
    override val name: String = "WebDAV"

    private val root = baseUrl.trimEnd('/')

    private fun authHeader(): String? {
        if (username.isBlank() && password.isBlank()) return null
        val raw = "$username:$password"
        val encoded = PlatformBase64.encode(raw.toByteArray(Charsets.UTF_8))
        return "Basic $encoded"
    }

    /** Root + segment-wise encoded path. No "//" collapse: "https://host" must keep its scheme. */
    private fun url(path: String): String {
        val encoded = path.trimStart('/').split('/').joinToString("/") { seg ->
            seg.toByteArray(Charsets.UTF_8).joinToString("") { b ->
                val v = b.toInt() and 0xFF
                if (v in '0'.code..'9'.code || v in 'A'.code..'Z'.code || v in 'a'.code..'z'.code ||
                    v == '-'.code || v == '_'.code || v == '.'.code
                ) {
                    v.toChar().toString()
                } else {
                    "%%%02X".format(v)
                }
            }
        }
        return "$root/$encoded"
    }

    override suspend fun read(relativePath: String): ByteArray? {
        val headers = mutableMapOf<String, String>()
        authHeader()?.let { headers["Authorization"] = it }
        val resp = PlatformHttp.request("GET", url(relativePath), headers) ?: return null
        if (resp.status !in 200..299) return null
        return resp.body
    }

    override suspend fun write(relativePath: String, bytes: ByteArray): Boolean {
        val headers = mutableMapOf<String, String>()
        authHeader()?.let { headers["Authorization"] = it }
        headers["Content-Type"] = "application/octet-stream"
        val put = PlatformHttp.request("PUT", url(relativePath), headers, bytes)
        return put != null && put.status in 200..299
    }

    override suspend fun delete(relativePath: String): Boolean {
        val headers = mutableMapOf<String, String>()
        authHeader()?.let { headers["Authorization"] = it }
        val resp = PlatformHttp.request("DELETE", url(relativePath), headers)
        return resp != null && resp.status in 200..299
    }

    override suspend fun listAll(): List<SyncEntry> {
        val headers = mutableMapOf<String, String>()
        authHeader()?.let { headers["Authorization"] = it }
        headers["Depth"] = "infinity"
        val body = """<?xml version="1.0"?><D:propfind xmlns:D="DAV:"><D:prop>
            <D:displayname/><D:getcontentlength/><D:getlastmodified/><D:resourcetype/>
        </D:prop></D:propfind>""".trimIndent()
        val resp = PlatformHttp.request(
            "PROPFIND",
            "$root/",
            headers,
            body.toByteArray(Charsets.UTF_8),
        ) ?: return emptyList()
        if (resp.status !in 200..299) return emptyList()
        val xml = resp.body.toString(Charsets.UTF_8)
        val entries = mutableListOf<SyncEntry>()
        for (response in Regex("<D:response[\\s\\S]*?</D:response>", RegexOption.IGNORE_CASE)
            .findAll(xml)
            .map { it.value }
            .drop(1) // first response is the root collection itself
        ) {
            val href = Regex("<D:href>(.*?)</D:href>", RegexOption.IGNORE_CASE)
                .find(response)?.groupValues?.get(1) ?: continue
            val isCollection = response.contains("<D:collection", ignoreCase = true) ||
                response.contains("<d:collection", ignoreCase = true)
            if (isCollection) continue
            val path = decodeHref(href, root)
            if (path.isBlank() || SyncEngine.MANIFEST == path) continue
            val size = Regex("<D:getcontentlength>(\\d+)</D:getcontentlength>", RegexOption.IGNORE_CASE)
                .find(response)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val mtime = parseWebDavDate(
                Regex("<D:getlastmodified>(.*?)</D:getlastmodified>", RegexOption.IGNORE_CASE)
                    .find(response)?.groupValues?.get(1),
            )
            entries.add(SyncEntry(path, size, mtime))
        }
        return entries
    }

    /** Strips the base path and URL-encoding from a DAV href; returns a NavData-relative path. */
    private fun decodeHref(href: String, base: String): String {
        val clean = urlDecode(href).trimEnd('/')
        val baseClean = base.trimEnd('/')
        val after = if (baseClean.isNotBlank() && clean.startsWith(baseClean)) {
            clean.removePrefix(baseClean)
        } else {
            clean.substringAfterLast('/')
        }
        return after.trimStart('/')
    }

    private fun parseWebDavDate(raw: String?): Long {
        // RFC 1123: "Tue, 11 Nov 2025 12:34:56 GMT"
        val m = Regex("(\\d{2}) (\\w{3}) (\\d{4}) (\\d{2}):(\\d{2}):(\\d{2})").find(raw ?: return 0L)
            ?: return 0L
        val months = mapOf("Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "May" to 5, "Jun" to 6,
            "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12)
        val month = months[m.groupValues[2]] ?: return 0L
        val date = kotlinx.datetime.LocalDate(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
        return ru.azimut.app.core.astro.TimeUtil.millisUtc(
            date,
            kotlinx.datetime.LocalTime(m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt()),
        ) ?: 0L
    }

    /** Hrefs are percent-encoded UTF-8 paths; '+' in a path is a literal plus, not a space. */
    private fun urlDecode(s: String): String {
        val out = StringBuilder(s.length)
        val buf = ArrayList<Byte>(s.length)
        fun flush() {
            if (buf.isNotEmpty()) {
                out.append(buf.toByteArray().toString(Charsets.UTF_8))
                buf.clear()
            }
        }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val hi = if (c == '%' && i + 2 < s.length) s[i + 1].digitToIntOrNull(16) else null
            val lo = if (hi != null) s[i + 2].digitToIntOrNull(16) else null
            if (hi != null && lo != null) {
                buf.add(((hi shl 4) or lo).toByte())
                i += 3
            } else {
                flush()
                out.append(c)
                i++
            }
        }
        flush()
        return out.toString()
    }
}

/** java.util.Base64 is JVM; both targets are JVM so a tiny helper is enough. */
object PlatformBase64 {
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i + 2 < bytes.size) {
            val v = ((bytes[i].toInt() and 0xFF) shl 16) or
                ((bytes[i + 1].toInt() and 0xFF) shl 8) or
                (bytes[i + 2].toInt() and 0xFF)
            for (s in 3 downTo 0) sb.append(alphabet[((v shr (6 * s)) and 0x3F)])
            i += 3
        }
        val rest = bytes.size - i
        if (rest == 1) {
            val v = (bytes[i].toInt() and 0xFF) shl 4
            sb.append(alphabet[(v shr 6) and 0x3F])
            sb.append(alphabet[v and 0x3F])
            sb.append("==")
        } else if (rest == 2) {
            val v = ((bytes[i].toInt() and 0xFF) shl 8) or ((bytes[i + 1].toInt() and 0xFF) shl 0)
            sb.append(alphabet[(v shr 10) and 0x3F])
            sb.append(alphabet[(v shr 4) and 0x3F])
            sb.append(alphabet[(v shl 2) and 0x3F])
            sb.append("=")
        }
        return sb.toString()
    }
}
