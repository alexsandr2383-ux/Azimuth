package ru.azimut.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.core.routing.RouteData
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberRouteDataPicker(
    onImported: (count: Int) -> Unit,
    onError: () -> Unit,
): () -> Unit {
    val imported = rememberUpdatedState(onImported)
    val failed = rememberUpdatedState(onError)
    return remember {
        {
            val chooser = JFileChooser().apply {
                dialogTitle = "Импорт сегментов BRouter / Import BRouter segments"
                fileSelectionMode = JFileChooser.FILES_ONLY
                isMultiSelectionEnabled = true
                isAcceptAllFileFilterUsed = false
                fileFilter = FileNameExtensionFilter("BRouter segments (*.rd5)", "rd5")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                runCatching {
                    val target = File(PlatformFiles.navDataRoot(), RouteData.SEGMENTS_DIR).apply { mkdirs() }
                    var count = 0
                    chooser.selectedFiles.forEach { source ->
                        if (source.isFile && source.extension.equals("rd5", ignoreCase = true)) {
                            source.copyTo(File(target, source.name), overwrite = true)
                            count++
                        }
                    }
                    imported.value(count)
                }.onFailure { failed.value() }
            }
        }
    }
}
