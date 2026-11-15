package ru.azimut.app.core.platform

/**
 * Storage root for the shared NavData/ folder.
 * All paths passed to read/write are relative to that root.
 */
expect object PlatformFiles {
    fun navDataRoot(): String
    fun ensureDirs()
    fun readText(relativePath: String): String?
    fun readBytes(relativePath: String): ByteArray?
    fun writeText(relativePath: String, text: String)
    fun writeBytes(relativePath: String, bytes: ByteArray)
    fun listFiles(relativeDir: String): List<String>
    /** All file paths under [relativeDir] (or the whole NavData root), relative to the root. */
    fun listAllFiles(relativeDir: String = ""): List<String>
    /** Free space on the NavData volume in bytes; -1 when unknown. */
    fun freeSpaceBytes(): Long
    fun copy(fromRelativePath: String, toRelativePath: String): Boolean
    fun size(relativePath: String): Long
    /** Epoch millis of the last modification; 0 when unknown. */
    fun lastModified(relativePath: String): Long
    fun delete(relativePath: String): Boolean
    fun exists(relativePath: String): Boolean
}

expect object PlatformInfo {
    val name: String

    /** Mouse-driven UI: plain clicks only pan/select, actions live in the right-click menu. */
    val isDesktop: Boolean
}
