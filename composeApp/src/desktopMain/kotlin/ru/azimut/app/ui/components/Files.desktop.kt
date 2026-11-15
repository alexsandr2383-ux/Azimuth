package ru.azimut.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import ru.azimut.app.core.platform.PlatformFiles
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberTrackFilePicker(
    onPicked: (fileName: String, content: String, bytes: ByteArray?) -> Unit,
    onError: () -> Unit,
): () -> Unit {
    val picked = rememberUpdatedState(onPicked)
    val failed = rememberUpdatedState(onError)
    return remember {
        {
            val chooser = JFileChooser().apply {
                dialogTitle = "Импорт трека/точек / Import track/waypoints"
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = true
                fileFilter = FileNameExtensionFilter(
                    "Треки и точки (*.gpx, *.kml, *.kmz, *.tcx, *.csv, *.geojson)",
                    "gpx", "kml", "kmz", "tcx", "csv", "geojson", "json",
                )
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile
                runCatching { file.readBytes() }
                    .onSuccess { bytes ->
                        val text = bytes.toString(Charsets.UTF_8)
                        picked.value(file.name, text, bytes)
                    }
                    .onFailure { failed.value() }
            }
        }
    }
}

@Composable
actual fun rememberBytesSaver(
    onSaved: (location: String) -> Unit,
    onError: () -> Unit,
): (suggestedName: String, bytes: ByteArray, mimeType: String) -> Unit {
    val saved = rememberUpdatedState(onSaved)
    val failed = rememberUpdatedState(onError)
    return remember {
        { suggestedName: String, bytes: ByteArray, _: String ->
            val exportDir = File(PlatformFiles.navDataRoot(), "export").apply { mkdirs() }
            val chooser = JFileChooser(exportDir).apply {
                dialogTitle = "Сохранить / Save"
                selectedFile = File(exportDir, suggestedName)
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                runCatching { chooser.selectedFile.writeBytes(bytes) }
                    .onSuccess { saved.value(chooser.selectedFile.absolutePath) }
                    .onFailure { failed.value() }
            }
        }
    }
}

@Composable
actual fun rememberFolderPicker(
    onPicked: (location: String) -> Unit,
): () -> Unit {
    val picked = rememberUpdatedState(onPicked)
    return remember {
        {
            val chooser = JFileChooser().apply {
                dialogTitle = "Папка синхронизации / Sync folder"
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                picked.value(chooser.selectedFile.absolutePath)
            }
        }
    }
}
