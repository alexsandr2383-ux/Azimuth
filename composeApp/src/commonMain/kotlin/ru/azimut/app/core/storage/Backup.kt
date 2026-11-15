package ru.azimut.app.core.storage

import ru.azimut.app.core.platform.PlatformFiles

data class BackupEntry(
    val name: String,
    val size: Long,
    val modifiedAt: Long,
)

/** Zip snapshots of NavData/ stored in NavData/backups/. */
expect object Backup {
    /** Creates a snapshot and returns its file name, or null on failure. */
    fun create(): String?

    /** Snapshots, newest first. */
    fun list(): List<BackupEntry>

    /** Restores a snapshot; a safety snapshot of the current state is written first. */
    fun restore(name: String): Boolean
}

/** Retention policy: deletes the oldest snapshots beyond [keep]; returns how many were removed. */
fun pruneBackups(keep: Int): Int {
    if (keep <= 0) return 0
    val victims = Backup.list().sortedByDescending { it.modifiedAt }.drop(keep)
    var deleted = 0
    for (entry in victims) {
        if (PlatformFiles.delete("backups/${entry.name}")) deleted++
    }
    return deleted
}
