package ru.azimut.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import ru.azimut.app.core.location.RecordingControl
import ru.azimut.app.core.location.TrackRecorder
import ru.azimut.app.core.model.formatDistance
import ru.azimut.app.core.platform.PlatformLocation
import ru.azimut.app.core.storage.SettingsStore
import java.awt.Dimension

fun main() = application {
    val recorder by TrackRecorder.state.collectAsState()
    var windowVisible by remember { mutableStateOf(true) }
    val trayState = rememberTrayState()
    // Tray texts are fixed at start; the in-app language switch applies to the window.
    val en = remember { SettingsStore().load().language == "en" }
    fun t(ru: String, english: String) = if (en) english else ru

    fun toggleRecording() {
        if (TrackRecorder.state.value.recording) RecordingControl.stop() else RecordingControl.startWithSavedSettings()
    }

    Tray(
        icon = painterResource("icon.png"),
        state = trayState,
        tooltip = if (recorder.recording) {
            t("Азимут — запись: ", "Azimuth — recording: ") + formatDistance(recorder.distanceMeters)
        } else {
            t("Азимут", "Azimuth")
        },
        onAction = { windowVisible = true },
        menu = {
            Item(t("Открыть", "Open"), onClick = { windowVisible = true })
            Item(
                if (recorder.recording) t("Остановить и сохранить запись", "Stop and save recording")
                else t("Начать запись трека", "Start track recording"),
                onClick = ::toggleRecording,
            )
            Separator()
            Item(t("Выход", "Exit"), onClick = {
                if (TrackRecorder.state.value.recording) RecordingControl.stopBlocking()
                exitApplication()
            })
        },
    )

    Window(
        onCloseRequest = {
            val background = runCatching { SettingsStore().load().backgroundRecording }.getOrDefault(true)
            if (TrackRecorder.state.value.recording && background) {
                // Keep recording in the background; the tray icon brings the window back.
                windowVisible = false
                trayState.sendNotification(
                    Notification(
                        t("Азимут", "Azimuth"),
                        t(
                            "Запись трека продолжается. Окно — через значок в трее.",
                            "Track recording continues. Reopen the window from the tray icon.",
                        ),
                    )
                )
            } else {
                if (TrackRecorder.state.value.recording) RecordingControl.stopBlocking()
                exitApplication()
            }
        },
        visible = windowVisible,
        title = "Азимут / Azimuth",
        icon = painterResource("icon.png"),
        state = rememberWindowState(
            placement = WindowPlacement.Maximized,
            width = 1400.dp,
            height = 900.dp,
        ),
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.R) {
                toggleRecording()
                true
            } else {
                false
            }
        },
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(980, 700)
            PlatformLocation.startPreview()
        }
        App()
    }
}
