package ru.azimut.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
)

@Serializable
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    val timeMillis: Long = 0L,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val bearing: Float? = null,
    val susanin: Boolean = false,
    /** The segment leading to this point is a foot/bridle path ("TRAIL, not a road"). */
    val trail: Boolean = false,
)

@Serializable
data class Waypoint(
    val id: String,
    val name: String,
    val description: String = "",
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    val createdAt: Long = 0L,
    val kind: String = "point",
    val category: String = "poi",
    val isPoi: Boolean = false,
    /** File names in NavData/photos/. */
    val photos: List<String> = emptyList(),
    /** WAV file names in NavData/voice/. */
    val voiceNotes: List<String> = emptyList(),
    val favorite: Boolean = false,
) {
    val poiType: String
        get() = when {
            category in PoiTypes.ALL -> category
            kind == "imported" -> PoiTypes.OTHER
            else -> PoiTypes.OWN
        }
}

@Serializable
data class Track(
    val id: String,
    val name: String,
    val profile: String,
    val startedAt: Long,
    val points: List<TrackPoint>,
    val distanceMeters: Double = 0.0,
    val susanin: Boolean = false,
    /** [KIND_TRACK] for recorded rides, [KIND_ROUTE] for planned routes. */
    val kind: String = KIND_TRACK,
    /** Fords on a planned route, counted by the offline router from OSM data. */
    val fordCount: Int = 0,
    /** Total planned duration in seconds (from the offline router); null for plans saved before v0.4.0. */
    val plannedTimeSeconds: Double? = null,
)

const val KIND_TRACK = "track"
const val KIND_ROUTE = "route"

@Serializable
data class TrackSummary(
    val id: String,
    val name: String,
    val profile: String,
    val startedAt: Long,
    val distanceMeters: Double,
    val pointCount: Int,
    val susanin: Boolean = false,
    val kind: String = KIND_TRACK,
    val fordCount: Int = 0,
    /** Wall-clock duration of a recorded track (last point − start) or router time for plans; null = unknown. */
    val durationSeconds: Long? = null,
)

fun Track.toSummary(): TrackSummary = TrackSummary(
    id = id,
    name = name,
    profile = profile,
    startedAt = startedAt,
    distanceMeters = distanceMeters,
    pointCount = points.size,
    susanin = susanin,
    kind = kind,
    fordCount = fordCount,
    durationSeconds = if (kind == KIND_ROUTE) {
        plannedTimeSeconds?.toLong()
    } else {
        points.lastOrNull()?.timeMillis?.takeIf { it > startedAt }?.let { (it - startedAt) / 1000 }
    },
)

@Serializable
data class AppSettings(
    val language: String = "ru",
    val theme: String = "dark",
    val transportProfile: String = "enduro",
    val mapLayer: String = "base",
    val mapStyle: String = "esri_street",
    val mapOpacity: Float = 0.7f,
    /** Backups retention: keep the newest N snapshots in backups/. */
    val backupKeep: Int = 20,
    val overlayEnabled: Boolean = false,
    val overlayLayer: String = "satellite_hd",
    val overlayOpacity: Float = 0.6f,
    val showPois: Boolean = true,
    val poiTypes: Set<String> = PoiTypes.defaults,
    /** Last map viewport, restored on launch (mobile; desktop uses MapViewStore). */
    val mapCenterLat: Double? = null,
    val mapCenterLon: Double? = null,
    val mapZoom: Double? = null,
    val orientationMode: String = "north",
    val headingSmoothing: Boolean = true,
    val susaninEnabled: Boolean = false,
    val susaninCount: Int = 0,
    /** Route dialog: build along roads only, no hiking trails. */
    val avoidTrails: Boolean = false,
    /** Fuel ramps: tank size, consumption and reserve; 0 = the calculation is off. */
    val fuelTankLiters: Double = 0.0,
    val fuelConsumptionL100: Double = 0.0,
    val fuelReserveLiters: Double = 0.0,
    val recordingIntervalSec: Int = 1,
    val batterySaver: Boolean = false,
    /** Do not add track points while standing still (speed below ~0.8 m/s). */
    val skipStops: Boolean = false,
    /** Yellow dashed sunset-azimuth line from the current position (off: it reads as noise). */
    val showSunsetAzimuth: Boolean = false,
    /** Download POI for the area together with the offline map area download. */
    val poiAutoLoad: Boolean = true,
    val backgroundRecording: Boolean = true,
    val autoScan: Boolean = true,
    val weatherRelief: Boolean = true,
    val voiceNotes: Boolean = true,
    val photoQuality: String = "high",
    val expandedCategories: List<String> = emptyList(),
    val baseLat: Double? = null,
    val baseLon: Double? = null,
    val rallyLat: Double? = null,
    val rallyLon: Double? = null,
    /** Desktop position source: "sim" or a serial port name such as COM3. */
    val gpsSource: String = "sim",
    val gpsBaud: Int = 9600,
    /** Android immersive mode: hide the system navigation buttons. */
    val hideSystemButtons: Boolean = true,
    /** Protected areas (OOPT) polygon layer on the map. */
    val showProtected: Boolean = true,
    /** Warn once per zone when the recording enters a protected/restricted area. */
    val protectedWarnings: Boolean = true,
    /** DEM download source id: "glo30", "glo90" or "custom". */
    val demSource: String = "glo30",
    /** Custom DEM URL template for demSource="custom"; {tile} is the tile name. */
    val demUrlTemplate: String = "",
    /** Sync remote: WebDAV base URL (blank = disabled). */
    val syncWebdavUrl: String = "",
    /** WebDAV user name; the password lives in the platform secret store. */
    val syncWebdavUser: String = "",
    /** Local/USB folder for folder sync: an absolute path (desktop) or SAF tree URI (Android). */
    val syncFolderPath: String = "",
    /** Sync remote kind: "folder", "webdav" or "lan" (desktop HTTP server). */
    val syncKind: String = "folder",
    /** Large speed readout for handlebar mounting. */
    val bigSpeed: Boolean = false,
    /** Hillshade overlay from the imported DEM. */
    val hillshade: Boolean = false,
    /** Warn when approaching a saved waypoint (off by default: it beeps). */
    val proximityAlerts: Boolean = false,
    /** Map menu shows every item; compact shows only the frequent ones. */
    val menuDetailed: Boolean = false,
    /** Settings screen shows every section; compact hides the rarely used ones. */
    val detailedSettings: Boolean = false,
) {
    val basePoint: GeoPoint?
        get() = if (baseLat != null && baseLon != null) GeoPoint(baseLat, baseLon) else null

    val rallyPoint: GeoPoint?
        get() = if (rallyLat != null && rallyLon != null) GeoPoint(rallyLat, rallyLon) else null
}

enum class TransportProfile(
    val id: String,
    val titleRu: String,
    val titleEn: String,
) {
    ENDURO("enduro", "Эндуро", "Enduro"),
    FOOT("foot", "Пеший", "Hiking"),
    BIKE("bike", "Велосипед", "Bike"),
    MTB("mtb", "MTB", "MTB"),
    MOTORCYCLE("motorcycle", "Мото", "Motorcycle"),
    ATV("atv", "Квадро", "ATV"),
    CAR("car", "Авто", "Car"),
    OFFROAD("4x4", "Внедорожник", "4x4"),
    ;

    fun title(lang: String): String = if (lang == "en") titleEn else titleRu

    companion object {
        fun byId(id: String): TransportProfile = entries.firstOrNull { it.id == id } ?: ENDURO
    }
}
