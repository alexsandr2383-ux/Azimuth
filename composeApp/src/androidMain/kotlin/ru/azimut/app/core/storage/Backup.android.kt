package ru.azimut.app.core.storage

import ru.azimut.app.core.map.MbTiles
import ru.azimut.app.core.platform.PlatformFiles
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual object Backup {
    private val root: File
        get() = File(PlatformFiles.navDataRoot())

    private val dir: File
        get() = File(root, "backups").apply { mkdirs() }

    actual fun create(): String? = runCatching {
        PlatformFiles.ensureDirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        var name = "azimut-backup-$stamp.zip"
        var target = File(dir, name)
        var suffix = 2
        while (target.exists()) {
            name = "azimut-backup-$stamp-$suffix.zip"
            target = File(dir, name)
            suffix++
        }
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            val base = root
            base.walkTopDown()
                .filter { it.isFile }
                .filterNot { it.name.endsWith(".tmp", ignoreCase = true) }
                .filterNot { it.relativeTo(base).invariantSeparatorsPath.startsWith("backups/") }
                .forEach { file ->
                    val entryName = file.relativeTo(base).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        name
    }.getOrNull()

    actual fun list(): List<BackupEntry> = runCatching {
        dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".zip", ignoreCase = true) }
            ?.map { BackupEntry(it.name, it.length(), it.lastModified()) }
            ?.sortedByDescending { it.modifiedAt }
            ?: emptyList()
    }.getOrDefault(emptyList())

    actual fun restore(name: String): Boolean = runCatching {
        if (name.contains('/') || name.contains('\\') || !name.endsWith(".zip", ignoreCase = true)) {
            return false
        }
        val source = File(dir, name)
        if (!source.isFile) return false
        // Never restore without a rollback point.
        if (create() == null) return false
        MbTiles.closeAll()
        val rootPath = root.canonicalFile
        ZipInputStream(source.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val relative = entry.name.replace('\\', '/')
                if (relative.startsWith("backups/")) continue
                val dest = File(rootPath, relative).canonicalFile
                if (!dest.path.startsWith(rootPath.path + File.separator)) continue
                dest.parentFile?.mkdirs()
                dest.outputStream().use { zip.copyTo(it) }
                zip.closeEntry()
            }
        }
        true
    }.getOrDefault(false)
}
