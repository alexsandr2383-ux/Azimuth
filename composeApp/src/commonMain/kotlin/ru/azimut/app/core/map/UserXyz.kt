package ru.azimut.app.core.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.azimut.app.core.platform.PlatformFiles

/**
 * User-added XYZ tile sources (url template with {z}/{x}/{y}). The map engine's
 * own ambient cache keeps fetched tiles for offline re-viewing; explicit bulk
 * download for these is not provided (license honesty: bulk scraping a foreign
 * tile server is the user's responsibility, not a feature).
 */
@Serializable
data class XyzSource(
    val id: String,
    val name: String,
    val urlTemplate: String,
    val minZoom: Int = 0,
    val maxZoom: Int = 18,
    val attribution: String = "",
    val addedAt: Long = 0,
)

object UserXyz {
    const val DIR = "xyz"
    private const val STATE = "xyz/xyz.json"
    const val PREFIX = "xyz:"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _sources = MutableStateFlow<List<XyzSource>>(emptyList())
    val sourcesFlow: StateFlow<List<XyzSource>> get() = _sources
    var sources: List<XyzSource>
        get() = _sources.value
        private set(v) { _sources.value = v }

    fun refresh() {
        val text = PlatformFiles.readText(STATE) ?: run {
            sources = emptyList()
            return
        }
        sources = runCatching { json.decodeFromString<List<XyzSource>>(text.removePrefix("\uFEFF")) }
            .getOrDefault(emptyList())
    }

    fun save(source: XyzSource) {
        val next = (sources.filterNot { it.id == source.id } + source).sortedBy { it.name.lowercase() }
        sources = next
        runCatching { PlatformFiles.writeText(STATE, json.encodeToString(next)) }
    }

    fun remove(id: String) {
        sources = sources.filterNot { it.id == id }
        runCatching { PlatformFiles.writeText(STATE, json.encodeToString(sources)) }
    }

    /** Exposed as extra base styles, so they appear in the layers menu. */
    fun asStyles(): List<MapStyle> = sources.map { src ->
        MapStyle(
            id = PREFIX + src.id,
            titleRu = src.name,
            titleEn = src.name,
            tileUrl = src.urlTemplate,
            cacheLayer = "xyz_${src.id}",
            maxZoom = src.maxZoom,
            androidMaxZoom = src.maxZoom,
            attribution = src.attribution.ifBlank { src.name },
            minTileBytes = 1,
            bulkDownload = false,
        )
    }
}
