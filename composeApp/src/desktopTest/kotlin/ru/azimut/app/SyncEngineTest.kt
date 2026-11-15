package ru.azimut.app

import java.nio.file.Files
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import ru.azimut.app.core.sync.encodePath
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import ru.azimut.app.core.sync.RemoteStorage
import ru.azimut.app.core.sync.SyncEngine
import ru.azimut.app.core.sync.SyncEntry
import ru.azimut.app.core.sync.SyncManifest

class SyncEngineTest {

    /** Old manifests (before the baseline fields) must keep decoding. */
    @Test
    fun entryOldJsonMigratesWithDefaults() {
        val entry = Json { ignoreUnknownKeys = true }
            .decodeFromString<SyncEntry>("""{"path":"tracks/x.gpx","size":10,"modifiedAt":123}""")
        assertEquals(0L, entry.remoteModifiedAt)
        assertEquals(0L, entry.remoteSize)
    }

    @Test
    fun planWithBaselineSuppressesEcho() {
        // Both sides untouched since the last sync, but mtimes drifted (e.g. the
        // remote re-wrote the file when it was uploaded): nothing must transfer.
        val local = SyncManifest(listOf(SyncEntry("a.txt", 10, 5_000)))
        val remote = listOf(SyncEntry("a.txt", 10, 9_999))
        val baseline = SyncManifest(
            listOf(SyncEntry("a.txt", 10, 5_000, remoteModifiedAt = 9_999, remoteSize = 10)),
        )
        val plan = SyncEngine.plan(local, remote, baseline)
        assertEquals(emptyList(), plan.upload)
        assertEquals(emptyList(), plan.download)
        assertEquals(emptyList(), plan.conflicts)
    }

    @Test
    fun planWithBaselineClassifiesOneSidedEdits() {
        val local = SyncManifest(
            listOf(
                SyncEntry("edited-local.txt", 20, 7_000), // edited since baseline
                SyncEntry("touched-remote.txt", 10, 5_000), // untouched locally
            ),
        )
        val remote = listOf(
            SyncEntry("edited-local.txt", 10, 5_000), // untouched remotely
            SyncEntry("touched-remote.txt", 30, 8_000), // edited remotely
        )
        val baseline = SyncManifest(
            listOf(
                SyncEntry("edited-local.txt", 10, 5_000, remoteModifiedAt = 5_000, remoteSize = 10),
                SyncEntry("touched-remote.txt", 10, 5_000, remoteModifiedAt = 5_000, remoteSize = 10),
            ),
        )
        val plan = SyncEngine.plan(local, remote, baseline)
        assertEquals(listOf("edited-local.txt"), plan.upload)
        assertEquals(listOf("touched-remote.txt"), plan.download)
        assertEquals(emptyList(), plan.conflicts)
    }

    @Test
    fun planWithBaselineConflictsOnlyOnBothSidedEdits() {
        val local = SyncManifest(listOf(SyncEntry("c.txt", 20, 7_000)))
        val remote = listOf(SyncEntry("c.txt", 30, 8_000))
        val baseline = SyncManifest(
            listOf(SyncEntry("c.txt", 10, 5_000, remoteModifiedAt = 5_000, remoteSize = 10)),
        )
        val plan = SyncEngine.plan(local, remote, baseline)
        assertEquals(emptyList(), plan.upload)
        assertEquals(emptyList(), plan.download)
        assertEquals(listOf("c.txt"), plan.conflicts.map { it.path })
        // Identical content on both sides is not a conflict even after both mtimes moved.
        val same = SyncEngine.plan(
            SyncManifest(listOf(SyncEntry("c.txt", 20, 7_000, hash = "42"))),
            listOf(SyncEntry("c.txt", 20, 8_000, hash = "42")),
            baseline,
        )
        assertEquals(emptyList(), same.conflicts)
    }

    /** In-memory remote with controllable mtimes. */
    private class FakeStorage(
        val files: MutableMap<String, Pair<ByteArray, Long>>,
    ) : RemoteStorage {
        override val name = "fake"
        override suspend fun read(relativePath: String): ByteArray? = files[relativePath]?.first
        override suspend fun write(relativePath: String, bytes: ByteArray): Boolean {
            files[relativePath] = bytes to System.currentTimeMillis()
            return true
        }

        override suspend fun delete(relativePath: String): Boolean = files.remove(relativePath) != null
        override suspend fun listAll(): List<SyncEntry> = files.map { (path, value) ->
            SyncEntry(path, value.first.size.toLong(), value.second)
        }
    }

    @Test
    fun runResolvesConflictAndConverges() {
        val oldHome = System.getProperty("user.home")
        val temp = Files.createTempDirectory("azimuth-sync-test").toFile()
        System.setProperty("user.home", temp.absolutePath)
        try {
            val localBytes = byteArrayOf(1, 2, 3, 4)
            ru.azimut.app.core.platform.PlatformFiles.writeBytes("tracks/x.bin", localBytes)
            val localMtime = ru.azimut.app.core.platform.PlatformFiles.lastModified("tracks/x.bin")
            val remoteBytes = byteArrayOf(9, 9)
            val storage = FakeStorage(mutableMapOf("tracks/x.bin" to (remoteBytes to localMtime)))

            // Same mtime, different size -> conflict; local is not older, so local wins.
            val first = runBlocking { SyncEngine.run(storage) }
            assertTrue(first is SyncEngine.Result.Done)
            val done = first as SyncEngine.Result.Done
            assertEquals(1, done.conflictCopies)
            // Winner transferred to the remote.
            assertEquals(localBytes.toList(), storage.files["tracks/x.bin"]!!.first.toList())
            // Loser kept as a stamped remote copy.
            val copy = storage.files.entries.single { it.key.contains("conflict-") && it.key.endsWith("x.bin") }
            assertEquals(remoteBytes.toList(), copy.value.first.toList())

            // Baseline saved: the next run converges instead of re-detecting the conflict.
            val manifest = SyncEngine.loadManifest()
            val entry = manifest?.entries?.firstOrNull { it.path == "tracks/x.bin" }
            assertTrue(entry != null && entry.remoteModifiedAt != 0L)
            val second = runBlocking { SyncEngine.run(storage) }
            assertTrue(second is SyncEngine.Result.Done && (second as SyncEngine.Result.Done).conflictCopies == 0)
        } finally {
            System.setProperty("user.home", oldHome)
            temp.deleteRecursively()
        }
    }

    @Test
    fun runKeepsBinaryLocalCopyOnTiedDownload() {
        val oldHome = System.getProperty("user.home")
        val temp = Files.createTempDirectory("azimuth-sync-test").toFile()
        System.setProperty("user.home", temp.absolutePath)
        try {
            val localBytes = byteArrayOf(0, 0xFF.toByte(), 0x7F, 1)
            ru.azimut.app.core.platform.PlatformFiles.writeBytes("tracks/y.bin", localBytes)
            val localMtime = ru.azimut.app.core.platform.PlatformFiles.lastModified("tracks/y.bin")
            val remoteBytes = byteArrayOf(5, 6, 7)
            // Remote newer by 800 ms: inside the 1 s copy window, outside the 500 ms tie.
            val storage = FakeStorage(mutableMapOf("tracks/y.bin" to (remoteBytes to localMtime + 800)))

            val result = runBlocking { SyncEngine.run(storage) }
            assertTrue(result is SyncEngine.Result.Done)
            // Remote version downloaded...
            assertEquals(
                remoteBytes.toList(),
                ru.azimut.app.core.platform.PlatformFiles.readBytes("tracks/y.bin")!!.toList(),
            )
            // ...and the local binary copy is byte-exact (writeText would have mangled it).
            val copyName = ru.azimut.app.core.platform.PlatformFiles.listFiles("tracks")
                .single { it.contains("conflict-") }
            assertEquals(
                localBytes.toList(),
                ru.azimut.app.core.platform.PlatformFiles.readBytes("tracks/$copyName")!!.toList(),
            )
        } finally {
            System.setProperty("user.home", oldHome)
            temp.deleteRecursively()
        }
    }

    @Test
    fun encodePathRoundTripsCyrillicAndSpaces() {
        val path = "tracks/Трек 123456.gpx"
        val encoded = encodePath(path)
        assertTrue(' ' !in encoded && '/' !in encoded)
        val decoded = java.net.URLDecoder.decode(encoded, "UTF-8")
        assertEquals(path, decoded)
    }
}
