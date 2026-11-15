package ru.azimut.app.core.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.azimut.app.core.platform.PlatformFiles

/** One synced file in a manifest: path, size, mtime. Hashes of big blobs (maps) are skipped.
 *  [remoteModifiedAt]/[remoteSize] record the remote side's state at the last successful sync
 *  (baseline): 0 = file was not on the remote side then. Lets plan() tell "unchanged echo"
 *  apart from a real edit on either device. */
@Serializable
data class SyncEntry(
    val path: String,
    val size: Long,
    val modifiedAt: Long,
    val hash: String = "",
    val remoteModifiedAt: Long = 0,
    val remoteSize: Long = 0,
)

@Serializable
data class SyncManifest(val entries: List<SyncEntry> = emptyList(), val savedAt: Long = 0)

/** A remote side (local folder via SAF/java.nio, or a WebDAV root). */
interface RemoteStorage {
    val name: String
    suspend fun read(relativePath: String): ByteArray?
    suspend fun write(relativePath: String, bytes: ByteArray): Boolean
    suspend fun delete(relativePath: String): Boolean
    /** All files under the root with their sizes and mtimes (epoch millis; 0 = unknown). */
    suspend fun listAll(): List<SyncEntry>
}

object SyncEngine {
    const val MANIFEST = "sync_manifest.json"
    /** Folders/files never synced: backups recursively, temp files, the sync manifest itself. */
    private fun excluded(path: String): Boolean = when {
        path == MANIFEST -> true
        path.startsWith("backups/") -> true
        path.endsWith(".tmp", ignoreCase = true) -> true
        path.startsWith("import/") -> false // import/ is synced so both devices pick it up
        else -> false
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Local manifest of NavData content. Hash only small text files (settings, points, index). */
    fun localManifest(): SyncManifest {
        val entries = PlatformFiles.listAllFiles("")
            .filterNot(::excluded)
            .map { path ->
                val size = PlatformFiles.size(path)
                val mtime = PlatformFiles.lastModified(path)
                val hash = if (size <= 512 * 1024) PlatformFiles.readText(path)?.hashCode()?.toString() ?: "" else ""
                SyncEntry(path, size, mtime, hash)
            }
        return SyncManifest(entries, System.currentTimeMillis())
    }

    data class Plan(val upload: List<String>, val download: List<String>, val conflicts: List<Conflict>)

    data class Conflict(val path: String, val localModifiedAt: Long, val remoteModifiedAt: Long)

    /**
     * Computes the transfer plan. With a baseline manifest (saved by the last successful
     * run) each file is classified by what changed since then: untouched echo files are
     * skipped, a one-sided edit transfers, edits on both sides conflict. Without a
     * baseline the legacy policy applies: newer mtime wins; near-tie with differing
     * content is a conflict.
     */
    fun plan(local: SyncManifest, remote: List<SyncEntry>, baseline: SyncManifest? = null): Plan {
        // Defensive: callers may hand in unfiltered manifests; backups/tmp never sync.
        val localEntries = local.entries.filterNot { excluded(it.path) }
        val remoteEntries = remote.filterNot { excluded(it.path) }
        val remoteByPath = remoteEntries.associateBy { it.path }
        val localByPath = localEntries.associateBy { it.path }
        val baseByPath = baseline?.entries?.associateBy { it.path } ?: emptyMap()
        val upload = mutableListOf<String>()
        val download = mutableListOf<String>()
        val conflicts = mutableListOf<Conflict>()
        // Sub-second FS jitter: differences under 500 ms count as a tie.
        for (entry in localEntries) {
            val remoteEntry = remoteByPath[entry.path]
            if (remoteEntry == null) {
                upload.add(entry.path)
                continue
            }
            val base = baseByPath[entry.path]
            if (base != null && base.remoteModifiedAt != 0L) {
                val localChanged = entry.modifiedAt != base.modifiedAt || entry.size != base.size
                val remoteChanged =
                    remoteEntry.modifiedAt != base.remoteModifiedAt || remoteEntry.size != base.remoteSize
                when {
                    !localChanged && !remoteChanged -> continue
                    localChanged && !remoteChanged -> upload.add(entry.path)
                    remoteChanged && !localChanged -> download.add(entry.path)
                    entry.size == remoteEntry.size && entry.hash.isNotBlank() && entry.hash == remoteEntry.hash -> continue
                    else -> conflicts.add(Conflict(entry.path, entry.modifiedAt, remoteEntry.modifiedAt))
                }
                continue
            }
            if (entry.modifiedAt > remoteEntry.modifiedAt + 500) {
                upload.add(entry.path)
            } else if (remoteEntry.modifiedAt > entry.modifiedAt + 500) {
                download.add(entry.path)
            } else if (entry.size == remoteEntry.size &&
                entry.hash.isNotBlank() && entry.hash == remoteEntry.hash
            ) {
                // Identical content under shifted mtimes (echo of an earlier transfer).
            } else if (entry.size != remoteEntry.size &&
                !(entry.hash.isNotBlank() && entry.hash == remoteEntry.hash)
            ) {
                conflicts.add(Conflict(entry.path, entry.modifiedAt, remoteEntry.modifiedAt))
            }
        }
        for (entry in remoteEntries) {
            if (!localByPath.containsKey(entry.path)) download.add(entry.path)
        }
        return Plan(upload, download, conflicts)
    }

    /** Writes the local manifest next to the data so the next run knows the last state. */
    fun saveManifest(manifest: SyncManifest) {
        runCatching { PlatformFiles.writeText(MANIFEST, json.encodeToString(manifest)) }
    }

    fun loadManifest(): SyncManifest? {
        val text = PlatformFiles.readText(MANIFEST) ?: return null
        return runCatching { json.decodeFromString<SyncManifest>(text.removePrefix("\uFEFF")) }.getOrNull()
    }

    sealed class Result {
        data class Done(val uploaded: Int, val downloaded: Int, val conflictCopies: Int) : Result()
        data class Cancelled(val uploaded: Int, val downloaded: Int) : Result()
        data class Failed(val message: String) : Result()
    }

    /**
     * Runs the two-way sync. Never deletes: files missing on one side are copied,
     * not dropped. A conflict keeps both versions — the older side as a stamped copy —
     * and transfers the winner so the next run converges instead of re-detecting.
     */
    suspend fun run(
        storage: RemoteStorage,
        isCancelled: () -> Boolean = { false },
        onProgress: (done: Int, total: Int, phase: String) -> Unit = { _, _, _ -> },
        // Pre-sync safety snapshot is done by the caller (Backup.create()), blocking restore lock is UI's job.
    ): Result {
        val baseline = loadManifest()
        val localNow = localManifest()
        val remoteNow = storage.listAll()
        val plan = plan(localNow, remoteNow, baseline)
        val localByPath = localNow.entries.associateBy { it.path }
        val remoteByPath = remoteNow.associateBy { it.path }
        val total = plan.upload.size + plan.download.size + plan.conflicts.size
        var done = 0
        var uploaded = 0
        var downloaded = 0
        var conflictCopies = 0

        for (path in plan.download) {
            if (isCancelled()) return Result.Cancelled(uploaded, downloaded)
            onProgress(done++, total, "download")
            val bytes = storage.read(path)
            if (bytes != null) {
                // A local file that would be overwritten in a conflict case is kept as a copy.
                val remoteEntry = remoteByPath[path]
                val localEntry = localByPath[path]
                if (PlatformFiles.exists(path) && localEntry != null && remoteEntry != null &&
                    PlatformFiles.size(path) != bytes.size.toLong() &&
                    abs(localEntry.modifiedAt - remoteEntry.modifiedAt) <= 1_000
                ) {
                    val localBytesCopy = PlatformFiles.readBytes(path)
                    if (localBytesCopy != null) {
                        PlatformFiles.writeBytes(conflictPath(path), localBytesCopy)
                        conflictCopies++
                    }
                }
                PlatformFiles.writeBytes(path, bytes)
                downloaded++
            }
        }
        for (path in plan.upload) {
            if (isCancelled()) return Result.Cancelled(uploaded, downloaded)
            onProgress(done++, total, "upload")
            val bytes = PlatformFiles.readBytes(path) ?: continue
            if (storage.write(path, bytes)) uploaded++
        }
        for (conflict in plan.conflicts) {
            if (isCancelled()) return Result.Cancelled(uploaded, downloaded)
            onProgress(done++, total, "conflict")
            // Same mtime, differing content: keep the older side as a stamped copy
            // and transfer the winner, so both sides end up identical.
            val localBytes = PlatformFiles.readBytes(conflict.path)
            val remoteBytes = storage.read(conflict.path)
            if (localBytes != null && remoteBytes != null) {
                if (conflict.localModifiedAt >= conflict.remoteModifiedAt) {
                    storage.write(conflictPath(conflict.path), remoteBytes)
                    if (storage.write(conflict.path, localBytes)) uploaded++
                } else {
                    PlatformFiles.writeBytes(conflictPath(conflict.path), localBytes)
                    PlatformFiles.writeBytes(conflict.path, remoteBytes)
                    downloaded++
                }
                conflictCopies++
            }
        }
        // Baseline for the next run: what each side looked like after this sync.
        saveManifest(baselineManifest(localManifest(), storage.listAll()))
        return Result.Done(uploaded, downloaded, conflictCopies)
    }

    /** Merges local and remote listings into the next-run baseline ([SyncEntry.remoteModifiedAt]). */
    private fun baselineManifest(local: SyncManifest, remote: List<SyncEntry>): SyncManifest {
        val remoteByPath = remote.associateBy { it.path }
        return SyncManifest(
            local.entries.map { entry ->
                val r = remoteByPath[entry.path]
                entry.copy(remoteModifiedAt = r?.modifiedAt ?: 0L, remoteSize = r?.size ?: 0L)
            },
            System.currentTimeMillis(),
        )
    }

    private fun conflictPath(path: String): String {
        val dir = path.substringBeforeLast('/')
        val name = path.substringAfterLast('/')
        val stamp = System.currentTimeMillis()
        return if (dir == path) "conflict-$stamp-$name" else "$dir/conflict-$stamp-$name"
    }
}

private fun abs(value: Long): Long = if (value < 0) -value else value
