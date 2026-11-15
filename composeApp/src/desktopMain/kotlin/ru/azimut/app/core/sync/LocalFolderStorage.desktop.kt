package ru.azimut.app.core.sync

import java.io.File

actual class LocalFolderStorage actual constructor(private val config: String) : RemoteStorage {
    private val dir: File = File(config)
    override val name: String
        get() = dir.absolutePath.substringAfterLast(File.separatorChar).ifBlank { dir.absolutePath }

    private fun resolve(relativePath: String): File {
        // Zip-slip guard: the resolved path must stay under the root.
        val root = dir.canonicalFile
        val target = File(dir, relativePath.replace('\\', '/')).canonicalFile
        require(target.path.startsWith(root.path + File.separator) || target == root) {
            "path escapes the sync root"
        }
        return target
    }

    override suspend fun read(relativePath: String): ByteArray? = runCatching {
        val file = resolve(relativePath)
        if (file.isFile) file.readBytes() else null
    }.getOrNull()

    override suspend fun write(relativePath: String, bytes: ByteArray): Boolean = runCatching {
        val file = resolve(relativePath)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".sync-tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(file) || file.delete() && tmp.renameTo(file)
    }.getOrDefault(false)

    override suspend fun delete(relativePath: String): Boolean = runCatching {
        resolve(relativePath).delete()
    }.getOrDefault(false)

    override suspend fun listAll(): List<SyncEntry> = runCatching {
        if (!dir.isDirectory) return emptyList()
        dir.walkTopDown()
            .filter { it.isFile }
            .map { SyncEntry(it.relativeTo(dir).invariantSeparatorsPath, it.length(), it.lastModified()) }
            .toList()
    }.getOrDefault(emptyList())
}
