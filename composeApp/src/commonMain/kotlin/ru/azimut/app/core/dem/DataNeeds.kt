package ru.azimut.app.core.dem

import ru.azimut.app.core.protected.OverpassZones
import ru.azimut.app.core.protected.ProtectedAreas

/**
 * What the app is missing for its features. Everything funnels to one question:
 * the UI shows "Загрузить?" once and [DataFetcher] does the rest — no menu hunting.
 */
enum class DataNeed(val titleRu: String, val titleEn: String) {
    DEM("рельеф (DEM)", "terrain (DEM)"),
    ZONES("зоны запретов из OSM", "OSM restriction zones"),
}

object DataNeeds {
    fun missing(dem: DemStore, lat: Double, lon: Double, zoneFilesLoaded: Boolean): List<DataNeed> {
        val out = mutableListOf<DataNeed>()
        if (!dem.hasTileFor(lat, lon)) out.add(DataNeed.DEM)
        if (!zoneFilesLoaded) out.add(DataNeed.ZONES)
        return out
    }
}

data class FetchReport(
    val demImported: Int,
    val zones: Int,
    val offline: Boolean,
    val serverBusy: Boolean,
    val failed: Boolean,
)

object DataFetcher {
    /**
     * Downloads every [need] for the district around lat/lon. Progress [onStage]
     * reports (step, totalSteps). Never throws; results are aggregated honestly.
     */
    suspend fun fetch(
        needs: List<DataNeed>,
        lat: Double,
        lon: Double,
        demSource: DemSourceDef,
        demUrlTemplate: String,
        onStage: (step: Int, total: Int) -> Unit = { _, _ -> },
    ): FetchReport {
        var zones = 0
        var demImported = 0
        var offline = false
        var busy = false
        var failed = false
        if (DataNeed.ZONES in needs) {
            onStage(1, needs.size)
            val r = OverpassZones.fetchAround(lat, lon, 0.5)
            when (r.outcome) {
                OverpassZones.Outcome.OK -> zones = r.zones
                OverpassZones.Outcome.EMPTY -> {}
                OverpassZones.Outcome.NO_NET -> offline = true
                OverpassZones.Outcome.SERVER_BUSY -> busy = true
                OverpassZones.Outcome.FAILED -> failed = true
            }
        }
        if (DataNeed.DEM in needs) {
            onStage(needs.size, needs.size)
            val plans = DemImport.tilesForPoints(listOf(lat to lon), demSource, demUrlTemplate)
            val r = DemImport.downloadTiles(plans, onProgress = { _, _ -> })
            demImported = r.imported
            if (r.offline) offline = true
            if (r.failed.isNotEmpty()) failed = true
        }
        return FetchReport(demImported, zones, offline, busy, failed)
    }
}
