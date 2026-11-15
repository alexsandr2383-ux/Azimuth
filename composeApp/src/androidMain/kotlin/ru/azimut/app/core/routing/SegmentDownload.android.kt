package ru.azimut.app.core.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.azimut.app.core.platform.PlatformFiles
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

actual object SegmentDownloader {
    actual suspend fun download(
        names: List<String>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<String> = withContext(Dispatchers.IO) {
        val failed = mutableListOf<String>()
        names.forEachIndexed { index, name ->
            if (!downloadOne(name)) failed += name
            onProgress(index + 1, names.size)
        }
        failed
    }

    private fun downloadOne(name: String): Boolean = runCatching {
        val dir = File(PlatformFiles.navDataRoot(), RouteData.SEGMENTS_DIR)
        dir.mkdirs()
        val target = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        val connection = URI("https://brouter.de/brouter/segments4/$name").toURL()
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            connection.inputStream.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (target.isFile) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.delete()
            return false
        }
        true
    }.getOrDefault(false)
}
