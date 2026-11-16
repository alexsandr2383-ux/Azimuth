package ru.azimut.app.core.location

import ru.azimut.app.core.model.AppSettings
import ru.azimut.app.core.model.Track
import ru.azimut.app.core.platform.PlatformLocation
import ru.azimut.app.core.storage.SettingsStore

/** Start/stop recording from any entry point (map button, tray, hotkey). */
object RecordingControl {
    fun start(settings: AppSettings, continueFrom: Track? = null) {
        TrackRecorder.configure(settings.recordingIntervalSec, settings.batterySaver, settings.skipStops)
        PlatformLocation.applySettings(
            settings.recordingIntervalSec,
            settings.batterySaver,
            settings.backgroundRecording,
        )
        TrackRecorder.start(settings.transportProfile, settings.susaninEnabled, continueFrom = continueFrom)
        PlatformLocation.start()
    }

    /** For entry points outside the UI state; settings are saved on every change. */
    fun startWithSavedSettings() = start(SettingsStore().load())

    /** Stops from the UI: recording ends at once, the file write runs in the background. */
    fun stop() {
        PlatformLocation.stop()
        TrackRecorder.stopAndSaveAsync()
    }

    /** Blocking stop for process shutdown, where the write must finish before exit. */
    fun stopBlocking(): Track? {
        PlatformLocation.stop()
        return TrackRecorder.stopAndSave()
    }
}
