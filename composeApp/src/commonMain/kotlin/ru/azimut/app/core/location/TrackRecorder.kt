package ru.azimut.app.core.location

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime
import ru.azimut.app.core.model.GeoPoint
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.model.TrackPoint
import ru.azimut.app.core.model.distanceMeters
import ru.azimut.app.core.storage.TrackStore

/** Fixes worse than this are shown as the current position but never written to the track. */
const val MAX_TRACK_ACCURACY_M = 30f

data class LocationFix(
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val bearing: Float? = null,
    val timeMillis: Long = Clock.System.now().toEpochMilliseconds(),
)

data class TrackState(
    val recording: Boolean = false,
    val name: String = "",
    val profile: String = "enduro",
    val susanin: Boolean = false,
    val startedAt: Long = 0L,
    val points: List<TrackPoint> = emptyList(),
    val distanceMeters: Double = 0.0,
    val lastFix: LocationFix? = null,
    /** Paused recording keeps the GPS alive but adds no points. */
    val paused: Boolean = false,
)

object TrackRecorder {
    private val trackStore = TrackStore()
    private val _state = MutableStateFlow(TrackState())
    val state: StateFlow<TrackState> = _state.asStateFlow()

    @Volatile private var intervalMs = 1_000L
    @Volatile private var batterySaver = false
    @Volatile private var skipStops = false

    /** Next recorded point opens a new segment (after a pause, restart or long GPS gap). */
    private var breakNext = false

    /** A silence longer than this between accepted fixes is a recording gap, not a slow stretch. */
    private fun gapMs() = maxOf(30_000L, intervalMs * 10)

    /** Minimum time between recorded points. Battery saver never records more often than every 5 s. */
    fun configure(intervalSec: Int, batterySaverEnabled: Boolean, skipStopsEnabled: Boolean = false) {
        intervalMs = intervalSec.coerceIn(1, 3_600) * 1_000L
        batterySaver = batterySaverEnabled
        skipStops = skipStopsEnabled
    }

    private val _saved = MutableSharedFlow<Track>(extraBufferCapacity = 8)

    /** Tracks written to the journal, whoever stopped the recording. */
    val saved: SharedFlow<Track> = _saved

    private val _saveFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** Emitted when stopping could not write the track file (the autosave still holds the points). */
    val saveFailed: SharedFlow<Unit> = _saveFailed

    private val _emptyStop = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** Emitted when a stop produced a track with no points — nothing is saved. */
    val emptyStop: SharedFlow<Unit> = _emptyStop

    /** Journal id this recording continues; null for a fresh track. Kept through toTrack(). */
    @Volatile private var continueTrackId: String? = null

    /** Speeds below this (m/s) count as standing still when skip-stops is on. */
    private val stopSpeedThreshold = 0.8f

    /**
     * Autosave runs off the caller's thread (on Android that is the main looper).
     * The channel is conflated and drained by a single coroutine, so writes never overlap
     * and only the latest request survives; `null` means "clear the autosave".
     */
    /** Background saves (autosave drain and the deferred stop save) never touch the UI thread. */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val autosaveRequests = Channel<Track?>(Channel.CONFLATED)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            for (track in autosaveRequests) {
                runCatching {
                    if (track == null) trackStore.clearAutosave() else trackStore.autosave(track)
                }
            }
        }
    }

    /** True when the last stop could not write the track file (transient IO failure). */
    var lastSaveFailed: Boolean = false
        private set

    @Synchronized
    fun start(profile: String, susanin: Boolean, name: String? = null, continueFrom: Track? = null) {
        // A failed stop keeps the autosave as the only copy of the track — do not wipe it.
        if (!lastSaveFailed) autosaveRequests.trySend(null)
        lastSaveFailed = false
        val now = Clock.System.now().toEpochMilliseconds()
        // Continuing a journal track: keep its id, name, start time and old points, so
        // stopAndSave overwrites the same GPX instead of creating a duplicate.
        continueTrackId = continueFrom?.id
        breakNext = continueFrom != null
        _state.value = TrackState(
            recording = true,
            name = continueFrom?.name ?: name ?: "Трек " + now.toString().takeLast(6),
            profile = continueFrom?.profile ?: profile,
            susanin = continueFrom?.susanin ?: susanin,
            startedAt = continueFrom?.startedAt ?: now,
            points = continueFrom?.points ?: emptyList(),
            distanceMeters = continueFrom?.distanceMeters ?: 0.0,
            lastFix = _state.value.lastFix,
        )
    }

    @Synchronized
    fun onFix(fix: LocationFix) {
        val current = _state.value
        val accurate = fix.accuracy == null || fix.accuracy <= MAX_TRACK_ACCURACY_M
        if (!current.recording || !accurate || current.paused) {
            _state.value = current.copy(lastFix = fix)
            return
        }
        val previous = current.points.lastOrNull()
        if (previous != null && previous.timeMillis > 0 && fix.timeMillis > 0) {
            val minGap = if (batterySaver) maxOf(intervalMs, 5_000L) else intervalMs
            val elapsed = fix.timeMillis - previous.timeMillis
            // Tolerance keeps a 1 s GPS that reports 999 ms from being skipped forever.
            if (elapsed in 0 until minGap - 250L) {
                _state.value = current.copy(lastFix = fix)
                return
            }
        }
        // "Do not record while stopped": standing fixes are dropped (v1 keeps a straight
        // bridge between the last moving point and the next one — no GPX trkseg split).
        if (skipStops && previous != null && (fix.speed ?: 1f) < stopSpeedThreshold) {
            _state.value = current.copy(lastFix = fix)
            return
        }
        val gap = previous != null && previous.timeMillis > 0 && fix.timeMillis > 0 &&
            fix.timeMillis - previous.timeMillis > gapMs()
        val segStart = previous != null && (breakNext || gap)
        breakNext = false
        val point = TrackPoint(
            segStart = segStart,
            lat = fix.lat,
            lon = fix.lon,
            altitude = fix.altitude,
            timeMillis = fix.timeMillis,
            accuracy = fix.accuracy,
            speed = fix.speed,
            bearing = fix.bearing,
            susanin = current.susanin,
        )
        val add = if (previous == null || segStart) 0.0 else distanceMeters(
            GeoPoint(previous.lat, previous.lon),
            GeoPoint(point.lat, point.lon),
        )
        val points = current.points + point
        val updated = current.copy(
            points = points,
            distanceMeters = current.distanceMeters + add,
            lastFix = fix,
        )
        _state.value = updated
        if (points.size == 1 || points.size % 5 == 0) {
            autosaveRequests.trySend(updated.toTrack(continueTrackId))
        }
    }

    /** Updates the displayed position only (e.g. a cached last-known location). */
    @Synchronized
    fun onPassiveFix(fix: LocationFix) {
        _state.value = _state.value.copy(lastFix = fix)
    }

    @Synchronized
    fun restore(track: Track): Boolean {
        if (track.points.isEmpty()) return false
        breakNext = true
        val last = track.points.last()
        _state.value = TrackState(
            recording = true,
            name = track.name,
            profile = track.profile,
            susanin = track.susanin,
            startedAt = track.startedAt,
            points = track.points,
            distanceMeters = track.distanceMeters,
            lastFix = LocationFix(
                lat = last.lat,
                lon = last.lon,
                altitude = last.altitude,
                accuracy = last.accuracy,
                speed = last.speed,
                bearing = last.bearing,
                timeMillis = last.timeMillis.takeIf { it > 0 }
                    ?: Clock.System.now().toEpochMilliseconds(),
            ),
        )
        autosaveRequests.trySend(track)
        return true
    }

    @Synchronized
    fun setSusanin(enabled: Boolean) {
        _state.value = _state.value.copy(susanin = enabled)
    }

    /** Pause/resume the current recording; GPS keeps running, no points are added while paused. */
    @Synchronized
    fun setPaused(paused: Boolean) {
        val current = _state.value
        if (current.recording) {
            if (!paused && current.paused) breakNext = true
            _state.value = current.copy(paused = paused)
        }
    }

    @Synchronized
    fun rename(name: String) {
        _state.value = _state.value.copy(name = name)
    }

    @Synchronized
    fun stopAndSave(): Track? {
        val current = _state.value
        if (!current.recording) return null
        val track = current.toTrack(continueTrackId)
        val saved = track.points.isEmpty() || runCatching { trackStore.save(track) }.isSuccess
        if (saved) autosaveRequests.trySend(null)
        if (saved && track.points.isNotEmpty()) _saved.tryEmit(track)
        if (!saved && track.points.isNotEmpty()) _saveFailed.tryEmit(Unit)
        if (track.points.isEmpty()) _emptyStop.tryEmit(Unit)
        _state.value = TrackState(lastFix = current.lastFix)
        return track
    }

    /**
     * UI-facing stop: the recording flag flips off synchronously so the button reacts at once,
     * while the file write (seconds for a long track) runs off the main thread.
     * [onDone] fires after the save attempt — the service uses it to stay foregrounded
     * (and thus un-killable) until the write actually finished.
     */
    fun stopAndSaveAsync(onDone: (() -> Unit)? = null) {
        val current = takeForStop() ?: run { onDone?.invoke(); return }
        saveScope.launch {
            val track = current.toTrack(continueTrackId)
            var saved = track.points.isEmpty() || runCatching { trackStore.save(track) }.isSuccess
            if (!saved && track.points.isNotEmpty()) {
                // One retry: autosave writes succeed moments earlier, so the failure
                // is usually transient (flash stall). The autosave file still holds
                // the latest points, so even a second failure is recoverable.
                saved = runCatching { trackStore.save(track) }.isSuccess
            }
            lastSaveFailed = !saved && track.points.isNotEmpty()
            if (saved) {
                // Clear only this recording's autosave; a newer one may have started meanwhile.
                if (_state.value.startedAt == 0L) autosaveRequests.trySend(null)
                if (track.points.isNotEmpty()) _saved.tryEmit(track)
            } else if (track.points.isNotEmpty()) {
                _saveFailed.tryEmit(Unit)
            }
            if (track.points.isEmpty()) _emptyStop.tryEmit(Unit)
            onDone?.invoke()
        }
    }

    @Synchronized
    private fun takeForStop(): TrackState? {
        val current = _state.value
        if (!current.recording) return null
        _state.value = TrackState(lastFix = current.lastFix)
        return current
    }

    @Synchronized
    fun clear() {
        autosaveRequests.trySend(null)
        continueTrackId = null
        _state.value = TrackState(lastFix = _state.value.lastFix)
    }
}

private fun TrackState.toTrack(continueId: String?): Track {
    val now = Clock.System.now().toEpochMilliseconds()
    // The default "Трек 776000" carries no information; at save time it becomes
    // "Трек dd.MM · длина". A user-chosen name is kept as is.
    val defaultName = Regex("^Трек \\d{6}$").matches(name) || name.isBlank()
    val resolvedName = if (defaultName) {
        val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(if (startedAt > 0) startedAt else now)
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        val lenText = if (distanceMeters >= 950) {
            "%.1f км".format(distanceMeters / 1000)
        } else {
            "${distanceMeters.toInt()} м"
        }
        // Minutes keep two same-day recordings apart (duplicate-name debt from v0.4).
        "Трек ${dt.dayOfMonth.toString().padStart(2, '0')}.${dt.monthNumber.toString().padStart(2, '0')} " +
            "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')} · $lenText"
    } else {
        name
    }
    return Track(
        // A continued recording overwrites the original journal file (same id).
        id = continueId ?: "track-$startedAt-${profile}",
        name = resolvedName.ifBlank { "Трек" },
        profile = profile,
        startedAt = if (startedAt > 0) startedAt else now,
        points = points,
        distanceMeters = distanceMeters,
        susanin = susanin || points.any { it.susanin },
    )
}
