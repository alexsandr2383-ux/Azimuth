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
