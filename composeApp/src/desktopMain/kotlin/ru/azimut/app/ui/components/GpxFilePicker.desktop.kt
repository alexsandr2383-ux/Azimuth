package ru.azimut.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberGpxFilePicker(
    onPicked: (fileName: String, xml: String) -> Unit,
    onError: () -> Unit,
): () -> Unit {
    val picked = rememberUpdatedState(onPicked)
    val failed = rememberUpdatedState(onError)
    return remember {
        {
            val chooser = JFileChooser().apply {
                dialogTitle = "Импорт GPX / Import GPX"
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = true
                fileFilter = FileNameExtensionFilter("GPX (*.gpx)", "gpx")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile
                runCatching { file.readText() }
                    .onSuccess { picked.value(file.name, it) }
                    .onFailure { failed.value() }
            }
        }
    }
}
