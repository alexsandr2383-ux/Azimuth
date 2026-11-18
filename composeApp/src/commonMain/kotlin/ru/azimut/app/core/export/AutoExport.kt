package ru.azimut.app.core.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.azimut.app.core.gpx.Gpx
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.core.sync.LocalFolderStorage

/**
 * AlpineQuest-style auto-export: each saved recording gets a GPX copy in the
 * export folder — NavData/export/ by default, or a user-picked folder
 * (absolute path on desktop, SAF tree URI on Android).
 */
object AutoExport {
    // Characters rejected by Windows/Android file names, plus control characters.
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    /** GPX file name for the track: sanitized track name, uniquified with « (2)». */
    fun fileName(track: Track, taken: Set<String> = emptySet()): String {
        var base = ILLEGAL.replace(track.name.trim(), "-").trim('.', ' ')
        if (base.isBlank()) base = "track-" + track.startedAt
        val takenLower = taken.map { it.lowercase() }.toSet()
        var name = "$base.gpx"
        var n = 2
        while (name.lowercase() in takenLower) {
            name = "$base ($n).gpx"
            n++
        }
        return name
    }

    /** Writes the GPX copy; returns the file name, or null when the write failed. */
    suspend fun saveTrackCopy(track: Track, folder: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val content = Gpx.writeTrack(track)
            if (folder.isBlank()) {
                val name = fileName(track, PlatformFiles.listFiles("export").toSet())
                PlatformFiles.writeText("export/$name", content)
                name
            } else {
                val storage = LocalFolderStorage(folder)
                val taken = storage.listAll().map { it.path.substringAfterLast('/') }.toSet()
                val name = fileName(track, taken)
                if (storage.write(name, content.toByteArray(Charsets.UTF_8))) name else null
            }
        }.getOrNull()
    }
}
