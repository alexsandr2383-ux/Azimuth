package ru.azimut.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import ru.azimut.app.core.platform.PlatformFiles
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberGpxSaver(
    onSaved: (location: String) -> Unit,
    onError: () -> Unit,
): (suggestedName: String, content: String) -> Unit {
    val saved = rememberUpdatedState(onSaved)
    val failed = rememberUpdatedState(onError)
    return remember {
        { suggestedName, content ->
            val exportDir = File(PlatformFiles.navDataRoot(), "export").apply { mkdirs() }
            val chooser = JFileChooser(exportDir).apply {
                dialogTitle = "Экспорт GPX / Export GPX"
                fileFilter = FileNameExtensionFilter("GPX (*.gpx)", "gpx")
                selectedFile = File(exportDir, suggestedName)
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                val chosen = chooser.selectedFile
                val file = if (chosen.extension.equals("gpx", ignoreCase = true)) chosen else File(chosen.path + ".gpx")
                runCatching { file.writeText(content, Charsets.UTF_8) }
                    .onSuccess { saved.value(file.absolutePath) }
                    .onFailure { failed.value() }
            }
        }
    }
}
