package ru.azimut.app.core.platform

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual object PlatformFiles {
    private val root: File
        get() {
            val base = AndroidApp.context.getExternalFilesDir(null) ?: AndroidApp.context.filesDir
            return File(base, "NavData")
        }

    actual fun navDataRoot(): String = root.absolutePath

    actual fun ensureDirs() {
        listOf(
            "maps",
            "tracks",
            "dem",
            "photos",
            "voice",
            "backups",
            "import",
            "protected",
            "export",
            "routing/segments4",
            "routing/profiles2",
        ).forEach {
            File(root, it).mkdirs()
        }
    }

    actual fun readText(relativePath: String): String? = runCatching {
        val file = File(root, relativePath)
        if (file.exists()) file.readText() else null
    }.getOrNull()

    actual fun readBytes(relativePath: String): ByteArray? = runCatching {
        val file = File(root, relativePath)
        if (file.exists()) file.readBytes() else null
    }.getOrNull()

    actual fun writeText(relativePath: String, text: String) {
        writeAtomically(File(root, relativePath), text.toByteArray(Charsets.UTF_8))
    }

    actual fun writeBytes(relativePath: String, bytes: ByteArray) {
        writeAtomically(File(root, relativePath), bytes)
    }

    actual fun listFiles(relativeDir: String): List<String> =
        File(root, relativeDir).listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()

    actual fun listAllFiles(relativeDir: String): List<String> {
        val base = File(root, relativeDir)
        if (!base.exists()) return emptyList()
        return base.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toList()
    }

    actual fun freeSpaceBytes(): Long = runCatching { root.usableSpace }.getOrDefault(-1L)

    actual fun copy(fromRelativePath: String, toRelativePath: String): Boolean = runCatching {
        val source = File(root, fromRelativePath)
        val target = File(root, toRelativePath)
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
        true
    }.getOrDefault(false)

    actual fun size(relativePath: String): Long =
        runCatching { File(root, relativePath).length() }.getOrDefault(0L)

    actual fun delete(relativePath: String): Boolean = File(root, relativePath).delete()

    actual fun lastModified(relativePath: String): Long =
        runCatching { File(root, relativePath).lastModified() }.getOrDefault(0L)

    actual fun exists(relativePath: String): Boolean = File(root, relativePath).exists()

    // --- Phone-wide scan (requires «All files access» on Android 11+) ---

    private val SCAN_EXTS = setOf("gpx", "kml", "kmz", "tcx", "geojson", "json", "mbtiles", "sqlitedb", "gpkg", "hgt", "tif", "tiff")
    private val SKIP_DIRS = setOf("Android", "DCIM", ".android", "data", "obb", "lost+found")

    actual fun scanExternalFiles(): List<String> {
        if (!hasAllFilesAccess()) return emptyList()
        val base = File("/sdcard")
        if (!base.isDirectory) return emptyList()
        val navData = root.canonicalPath
        val found = mutableListOf<String>()
        fun walk(dir: File, depth: Int) {
            if (depth > 8 || found.size >= 500) return
            val list = runCatching { dir.listFiles() }.getOrNull() ?: return
            for (f in list) {
                if (found.size >= 500) return
                if (f.isDirectory) {
                    if (f.name in SKIP_DIRS) continue
                    val canonical = runCatching { f.canonicalPath }.getOrNull() ?: continue
                    if (canonical == navData || navData.startsWith(canonical + "/")) continue
                    walk(f, depth + 1)
                } else {
                    val ext = f.extension.lowercase()
                    if (ext in SCAN_EXTS && f.length() > 0L) found.add(f.absolutePath)
                }
            }
        }
        walk(base, 0)
        return found
    }

    actual fun externalSize(absolutePath: String): Long = runCatching {
        File(absolutePath).length()
    }.getOrDefault(-1L)

    actual fun readExternalFile(absolutePath: String): ByteArray? = runCatching {
        val f = File(absolutePath)
        if (f.isFile && f.canRead()) f.readBytes() else null
    }.getOrNull()

    actual fun copyExternalToNavData(absolutePath: String, relativePath: String): Boolean = runCatching {
        val target = File(root, relativePath)
        target.parentFile?.mkdirs()
        File(absolutePath).copyTo(target, overwrite = true)
        true
    }.getOrDefault(false)

    actual fun hasAllFilesAccess(): Boolean = runCatching {
        android.os.Environment.isExternalStorageManager()
    }.getOrDefault(false)

    actual fun requestAllFilesAccess() {
        runCatching {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                android.net.Uri.parse("package:" + AndroidApp.context.packageName),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            AndroidApp.context.startActivity(intent)
        }
    }

    /** Writes via a temp file + rename so a crash mid-write never leaves a truncated file. */
    private fun writeAtomically(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

actual object PlatformInfo {
    actual val name: String = "Android"
    actual val isDesktop: Boolean = false
}
