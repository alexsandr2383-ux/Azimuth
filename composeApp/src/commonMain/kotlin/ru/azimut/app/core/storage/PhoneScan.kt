package ru.azimut.app.core.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.azimut.app.core.platform.PlatformFiles

/**
 * Phone-wide file search (the «Все файлы» mode): finds GPX/KML/maps/DEM in
 * user-accessible folders of the device and feeds them into the same import
 * pipeline as NavData/import/. Deduplicated by absolute path + size.
 */
object PhoneScan {
    private const val STATE_PATH = "phone-scan.json"

    private val EXTS = setOf("gpx", "kml", "kmz", "tcx", "geojson", "json", "mbtiles", "sqlitedb", "gpkg", "hgt", "tif", "tiff")

    data class Found(val name: String, val path: String, val fingerprint: String)

    fun pending(): List<Found> = runCatching {
        val known = loadState().files.associate { it.path to it.fingerprint }
        PlatformFiles.scanExternalFiles().mapNotNull { path ->
            val name = path.substringAfterLast('/').substringAfterLast('\\')
            if (name == "phone-scan.json") return@mapNotNull null // our own state file
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in EXTS) return@mapNotNull null
            val fingerprint = PlatformFiles.externalSize(path).toString()
            if (fingerprint == "-1") return@mapNotNull null
            if (known[path] == fingerprint) null else Found(name, path, fingerprint)
        }.sortedBy { it.name.lowercase() }
    }.getOrDefault(emptyList())

    fun mark(files: List<Found>) {
        if (files.isEmpty()) return
        val merged = loadState().files.associateBy { it.path }.toMutableMap()
        files.forEach { merged[it.path] = PFile(it.path, it.fingerprint) }
        runCatching {
            PlatformFiles.writeText(STATE_PATH, json.encodeToString(PState(merged.values.toList())))
        }
    }

    private fun loadState(): PState {
        val text = PlatformFiles.readText(STATE_PATH) ?: return PState()
        return runCatching { json.decodeFromString<PState>(text.removePrefix("\uFEFF")) }
            .getOrDefault(PState())
    }
}

@Serializable
private data class PFile(val path: String, val fingerprint: String)

@Serializable
private data class PState(val files: List<PFile> = emptyList())

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
