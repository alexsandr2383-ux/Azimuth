package ru.azimut.app.core.storage

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.azimut.app.core.gpx.Gpx
import ru.azimut.app.core.model.AppSettings
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.KIND_ROUTE
import ru.azimut.app.core.model.KIND_TRACK
import ru.azimut.app.core.model.TrackSummary
import ru.azimut.app.core.model.Waypoint
import ru.azimut.app.core.model.toSummary
import ru.azimut.app.core.platform.PlatformFiles

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

private inline fun <reified T> decode(text: String): T? =
    runCatching { json.decodeFromString<T>(text.removePrefix("\uFEFF")) }.getOrNull()

/**
 * Decodes [path]; if the file exists but is unreadable, keeps a copy next to it so the
 * next save cannot silently wipe the user's data.
 */
private inline fun <reified T> loadOrPreserve(path: String): T? {
    val text = PlatformFiles.readText(path) ?: return null
    return decode<T>(text) ?: run {
        runCatching { PlatformFiles.writeText("$path.corrupt", text) }
        null
    }
}

class SettingsStore {
    fun load(): AppSettings = loadOrPreserve<AppSettings>("settings.json") ?: AppSettings()

    fun save(settings: AppSettings) {
        PlatformFiles.ensureDirs()
        runCatching { PlatformFiles.writeText("settings.json", json.encodeToString(settings)) }
    }
}

class PointsStore {
    fun load(): List<Waypoint> = loadOrPreserve<List<Waypoint>>("points.json") ?: emptyList()

    fun save(points: List<Waypoint>) {
        PlatformFiles.ensureDirs()
        runCatching {
            PlatformFiles.writeText("points.json", json.encodeToString(points))
            PlatformFiles.writeText("points.gpx", Gpx.writeWaypoints(points))
        }
    }
}

@Serializable
data class TrashedTrack(val summary: TrackSummary, val deletedAt: Long)

/** Pure trash rules, kept free of I/O so they can be unit-tested. */
object TrashPolicy {
    const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000

    fun expired(items: List<TrashedTrack>, now: Long): List<TrashedTrack> =
        items.filter { now - it.deletedAt > RETENTION_MS }

    fun add(items: List<TrashedTrack>, entry: TrashedTrack): List<TrashedTrack> =
        listOf(entry) + items.filterNot { it.summary.id == entry.summary.id }
}

class TrackStore {
    fun save(track: Track) {
        PlatformFiles.ensureDirs()
        PlatformFiles.writeText("tracks/${track.id}.gpx", Gpx.writeTrack(track))
        val summaries = list().toMutableList()
        summaries.removeAll { it.id == track.id }
        summaries.add(0, track.toSummary())
        PlatformFiles.writeText("tracks/index.json", json.encodeToString(summaries))
    }

    fun list(): List<TrackSummary> {
        val stored = loadOrPreserve<List<TrackSummary>>("tracks/index.json") ?: return rebuildIndex()
        if (stored.none { it.durationSeconds == null }) return stored
        // One-time upgrade: an index written before durationSeconds existed gets its entries
        // re-parsed from the GPX files. Routes legitimately have no duration (plannedTimeSeconds
        // arrived later and is absent from older GPX), so only recorded tracks trigger this.
        if (stored.none { it.durationSeconds == null && it.kind != KIND_ROUTE }) return stored
        val fresh = rebuildIndex()
        if (fresh.isEmpty()) return stored
        // Upgrade in place: keep the stored entry identity (dates, names) and fill in only
        // the duration; entries whose GPX is gone survive as they are.
        val byId = fresh.associateBy { it.id }
        return stored.map { s -> byId[s.id]?.let { s.copy(durationSeconds = it.durationSeconds ?: s.durationSeconds) } ?: s }
    }

    /**
     * Splash-safe listing: the stored index, or file-name placeholders when it is
     * missing/broken — parsing a multi-megabyte GPX on ART takes a minute, so the
     * real rebuild happens in the background via [rebuildIfStale].
     */
    fun listQuick(): List<TrackSummary> {
        loadOrPreserve<List<TrackSummary>>("tracks/index.json")?.let { return it }
        return PlatformFiles.listFiles("tracks")
            .filter { it.endsWith(".gpx", ignoreCase = true) && it != "autosave.gpx" }
            .map {
                val id = it.substringBeforeLast('.')
                TrackSummary(
                    id = id,
                    name = id,
                    profile = "enduro",
                    startedAt = PlatformFiles.lastModified("tracks/$it"),
                    distanceMeters = 0.0,
                    pointCount = 0,
                    kind = KIND_TRACK,
                )
            }
            .sortedByDescending { it.startedAt }
    }

    /** Returns the freshly rebuilt index when it was stale, or null when nothing changed. */
    fun rebuildIfStale(): List<TrackSummary>? {
        val stored = loadOrPreserve<List<TrackSummary>>("tracks/index.json")
        val stale = stored == null ||
            stored.any { it.durationSeconds == null && it.kind != KIND_ROUTE }
        if (!stale) return null
        return rebuildIndex().ifEmpty { stored }
    }

    /** Recreates the journal index from the GPX files in tracks/ when it is missing or damaged. */
    private fun rebuildIndex(): List<TrackSummary> {
        val t0 = System.currentTimeMillis()
        val summaries = PlatformFiles.listFiles("tracks")
            .filter { it.endsWith(".gpx", ignoreCase = true) && it != "autosave.gpx" }
            .mapNotNull { fileName ->
                val fileStart = System.currentTimeMillis()
                val text = PlatformFiles.readText("tracks/$fileName") ?: return@mapNotNull null
                val track = runCatching { Gpx.parseImport(text).tracks.firstOrNull() }.getOrNull()
                    ?: return@mapNotNull null
                println("AZ-REBUILD $fileName ${System.currentTimeMillis() - fileStart} ms")
                track.copy(id = fileName.substringBeforeLast('.')).toSummary()
            }
            .sortedByDescending { it.startedAt }
        println("AZ-REBUILD total ${System.currentTimeMillis() - t0} ms, ${summaries.size} tracks")
        if (summaries.isNotEmpty()) {
            runCatching { PlatformFiles.writeText("tracks/index.json", json.encodeToString(summaries)) }
        }
        return summaries
    }

    fun load(id: String): Track? {
        val text = PlatformFiles.readText("tracks/$id.gpx") ?: return null
        return runCatching { Gpx.parseImport(text).tracks.firstOrNull() }.getOrNull()?.let { track ->
            val summary = list().firstOrNull { it.id == id }
            track.copy(id = id, name = summary?.name ?: track.name, kind = summary?.kind ?: track.kind)
        }
    }

    /** Moves the track to the trash (kept [TrashPolicy.RETENTION_MS]); falls back to a plain delete if it cannot be copied. */
    fun delete(id: String): Boolean {
        val path = "tracks/$id.gpx"
        val summary = list().firstOrNull { it.id == id }
        if (summary != null && PlatformFiles.exists(path) && PlatformFiles.copy(path, "tracks/trash/$id.gpx")) {
            val entry = TrashedTrack(summary, Clock.System.now().toEpochMilliseconds())
            runCatching { PlatformFiles.writeText(TRASH_INDEX, json.encodeToString(TrashPolicy.add(listTrash(), entry))) }
        }
        val deleted = PlatformFiles.delete(path) || !PlatformFiles.exists(path)
        if (deleted) {
            val summaries = list().filterNot { it.id == id }
            PlatformFiles.writeText("tracks/index.json", json.encodeToString(summaries))
        }
        return deleted
    }

    fun listTrash(): List<TrashedTrack> =
        loadOrPreserve<List<TrashedTrack>>(TRASH_INDEX) ?: emptyList()

    private fun saveTrash(items: List<TrashedTrack>) =
        PlatformFiles.writeText(TRASH_INDEX, json.encodeToString(items))

    fun restore(id: String): Boolean {
        val entry = listTrash().firstOrNull { it.summary.id == id } ?: return false
        if (!PlatformFiles.copy("tracks/trash/$id.gpx", "tracks/$id.gpx")) return false
        val summaries = list().filterNot { it.id == id }.toMutableList()
        summaries.add(0, entry.summary)
        PlatformFiles.writeText("tracks/index.json", json.encodeToString(summaries))
        PlatformFiles.delete("tracks/trash/$id.gpx")
        saveTrash(listTrash().filterNot { it.summary.id == id })
        return true
    }

    fun purge(id: String) {
        PlatformFiles.delete("tracks/trash/$id.gpx")
        saveTrash(listTrash().filterNot { it.summary.id == id })
    }

    /** Permanently removes trash entries older than the retention period. */
    fun purgeExpired() {
        val now = Clock.System.now().toEpochMilliseconds()
        TrashPolicy.expired(listTrash(), now).forEach { purge(it.summary.id) }
    }

    /** Cheap crash-safe autosave of the current recording. */
    fun autosave(track: Track) {
        PlatformFiles.ensureDirs()
        runCatching { PlatformFiles.writeText("tracks/autosave.gpx", Gpx.writeTrack(track)) }
    }

    fun loadAutosave(): Track? {
        val text = PlatformFiles.readText("tracks/autosave.gpx") ?: return null
        return runCatching { Gpx.parseImport(text).tracks.firstOrNull() }.getOrNull()
    }

    fun clearAutosave() {
        runCatching { PlatformFiles.delete("tracks/autosave.gpx") }
    }

    private companion object {
        const val TRASH_INDEX = "tracks/trash/index.json"
    }
}
