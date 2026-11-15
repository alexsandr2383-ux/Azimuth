package ru.azimut.app.core.import

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

actual object ZipKml {
    /** A KMZ is untrusted input: cap the uncompressed KML so a zip bomb cannot OOM the app. */
    private const val MAX_KML_BYTES = 64L * 1024 * 1024

    actual fun extract(bytes: ByteArray): String? = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var fallback: String? = null
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.lowercase()
                if (!entry.isDirectory && name.endsWith(".kml")) {
                    val text = readCapped(zip)?.toString(Charsets.UTF_8) ?: return null
                    if (name.endsWith("doc.kml")) return text
                    if (fallback == null) fallback = text
                }
                zip.closeEntry()
            }
            fallback
        }
    }.getOrNull()

    private fun readCapped(zip: ZipInputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_KML_BYTES) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
