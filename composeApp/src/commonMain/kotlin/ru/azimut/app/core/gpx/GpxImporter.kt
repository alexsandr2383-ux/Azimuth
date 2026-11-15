package ru.azimut.app.core.gpx

import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.Waypoint

data class GpxImportResult(
    val outcome: GpxImportOutcome,
    val tracks: List<Track> = emptyList(),
    val waypoints: List<Waypoint> = emptyList(),
)

object GpxImporter {
    fun parse(fileName: String, xml: String, fallbackProfile: String): GpxImportResult {
        val parsed = runCatching { Gpx.parseImport(xml, fallbackProfile) }
            .getOrElse { return GpxImportResult(GpxImportOutcome(failed = true)) }
        val sourceId = stableHash(xml)
        val baseName = fileName.substringBeforeLast('.').trim().ifBlank { "GPX" }
        val tracks = parsed.tracks.mapIndexed { index, track ->
            val fallbackName = if (parsed.tracks.size == 1) baseName else "$baseName ${index + 1}"
            track.copy(
                id = "gpx-$sourceId-$index",
                name = track.name.ifBlank { fallbackName },
            )
        }
        val waypoints = parsed.waypoints.mapIndexed { index, waypoint ->
            waypoint.copy(id = "gpxw-$sourceId-$index")
        }
        return GpxImportResult(
            outcome = GpxImportOutcome(
                tracks = tracks.size,
                waypoints = waypoints.size,
                empty = tracks.isEmpty() && waypoints.isEmpty(),
            ),
            tracks = tracks,
            waypoints = waypoints,
        )
    }

    private fun stableHash(value: String): String {
        var hash = -0x340d631b7bdddcdbL
        for (char in value) {
            hash = hash xor char.code.toLong()
            hash *= 0x100000001b3L
        }
        return hash.toULong().toString(16)
    }
}
