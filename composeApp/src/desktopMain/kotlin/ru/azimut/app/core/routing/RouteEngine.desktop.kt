package ru.azimut.app.core.routing

import btools.mapaccess.OsmNode
import btools.router.OsmNodeNamed
import btools.router.ProfileCache
import btools.router.RoutingContext
import btools.router.RoutingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.platform.PlatformFiles
import java.io.File
import kotlin.math.roundToInt

actual object RouteEngine {
    private val profileFiles = listOf(
        "lookups.dat",
        "hiking-mountain.brf",
        "trekking.brf",
        "mtb.brf",
        "car-fast.brf",
        "car-vario.brf",
        "moped.brf",
        "enduro.brf",
    )

    actual suspend fun route(request: RouteRequest): RouteResult = withContext(Dispatchers.IO) {
        val segments = File(PlatformFiles.navDataRoot(), RouteData.SEGMENTS_DIR)
        val missing = RouteData.missingSegments(request.stops)
        if (missing.isNotEmpty()) {
            return@withContext RouteResult(
                error = RouteError.NO_SEGMENTS,
                details = missing.joinToString(", "),
            )
        }

        runCatching {
            routeBlocking(request, segments)
        }.getOrElse { error ->
            RouteResult(
                error = RouteError.FAILED,
                details = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun routeBlocking(request: RouteRequest, segments: File): RouteResult {
        val profiles = ensureProfiles()
        val context = RoutingContext().apply {
            localFunction = File(profiles, request.routingProfileFile()).absolutePath
            outputFormat = "geojson"
            waypointCatchingRange = 250.0
        }
        ProfileCache.parseProfile(context)

        val waypoints = request.stops.mapIndexed { index, stop -> stop.toOsmNode("p$index") }
        val engine = RoutingEngine(null, null, segments, waypoints, context).apply {
            quite = true
            doRun(0)
        }

        val error = engine.errorMessage?.takeIf { it.isNotBlank() }
        if (error != null) {
            return RouteResult(error = RouteError.NO_ROUTE, details = error)
        }
        val track = engine.foundTrack
            ?: return RouteResult(error = RouteError.NO_ROUTE, details = "empty track")

        val base = RouteResult(
            distanceMeters = track.distance,
            ascendMeters = track.ascend,
            timeSeconds = track.totalSeconds,
            points = track.nodes.map { node ->
                GeoPoint(
                    lat = node.iLat / 1_000_000.0 - 90.0,
                    lon = node.iLon / 1_000_000.0 - 180.0,
                    altitude = node.sElev.takeIf { it != Short.MIN_VALUE }?.let { it / 4.0 },
                )
            },
        )
        return RouteAnalysis.analyze(base, RouteAnalysis.parseMessages(track.aggregateMessages()))
    }

    @Synchronized
    private fun ensureProfiles(): File {
        val target = File(PlatformFiles.navDataRoot(), RouteData.PROFILES_DIR)
        target.mkdirs()
        val loader = RouteEngine::class.java.classLoader ?: error("No class loader")
        profileFiles.forEach { name ->
            val bytes = loader.getResourceAsStream("brouter/profiles2/$name")?.use { it.readBytes() }
                ?: error("Missing BRouter profile: $name")
            syncProfile(File(target, name), bytes)
        }
        return target
    }

    /** Rewrites a bundled profile when the app ships a newer version of it. */
    private fun syncProfile(file: File, bytes: ByteArray) {
        if (file.isFile && file.length() == bytes.size.toLong() && file.readBytes().contentEquals(bytes)) return
        file.writeBytes(bytes)
    }

    private fun GeoPoint.toOsmNode(name: String): OsmNodeNamed {
        val node = OsmNodeNamed(
            OsmNode(
                ((lon + 180.0) * 1_000_000.0).roundToInt(),
                ((lat + 90.0) * 1_000_000.0).roundToInt(),
            )
        )
        node.name = name
        return node
    }
}
