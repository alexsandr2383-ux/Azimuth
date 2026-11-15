package ru.azimut.app.core.storage

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.azimut.app.core.gpx.Gpx
import ru.azimut.app.core.model.AppSettings
import ru.azimut.app.core.model.Track
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

class TrackStore {
    fun save(track: Track) {
        PlatformFiles.ensureDirs()
        PlatformFiles.writeText("tracks/${track.id}.gpx", Gpx.writeTrack(track))
        val summaries = list().toMutableList()
        summaries.removeAll { it.id == track.id }
        summaries.add(0, track.toSummary())
        PlatformFiles.writeText("tracks/index.json", json.encodeToString(summaries))
    }

    fun list(): List<TrackSummary> =
        loadOrPreserve<List<TrackSummary>>("tracks/index.json") ?: rebuildIndex()

    /** Recreates the journal index from the GPX files in tracks/ when it is missing or damaged. */
    private fun rebuildIndex(): List<TrackSummary> {
        val summaries = PlatformFiles.listFiles("tracks")
            .filter { it.endsWith(".gpx", ignoreCase = true) && it != "autosave.gpx" }
            .mapNotNull { fileName ->
                val text = PlatformFiles.readText("tracks/$fileName") ?: return@mapNotNull null
                val track = runCatching { Gpx.parseImport(text).tracks.firstOrNull() }.getOrNull()
                    ?: return@mapNotNull null
                track.copy(id = fileName.substringBeforeLast('.')).toSummary()
            }
            .sortedByDescending { it.startedAt }
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

    fun delete(id: String): Boolean {
        val path = "tracks/$id.gpx"
        val deleted = PlatformFiles.delete(path) || !PlatformFiles.exists(path)
        if (deleted) {
            val summaries = list().filterNot { it.id == id }
            PlatformFiles.writeText("tracks/index.json", json.encodeToString(summaries))
        }
        return deleted
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
}
