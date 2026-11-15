package ru.azimut.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.azimut.app.core.map.MbTiles
import ru.azimut.app.core.map.UserMaps
import ru.azimut.app.core.platform.PlatformFiles
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberMapFilePicker(
    onImported: (fileNames: List<String>) -> Unit,
    onError: () -> Unit,
): () -> Unit {
    val imported = rememberUpdatedState(onImported)
    val failed = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    return remember {
        {
            val chooser = JFileChooser().apply {
                dialogTitle = "Импорт карты / Import map"
                fileSelectionMode = JFileChooser.FILES_ONLY
                isMultiSelectionEnabled = true
                isAcceptAllFileFilterUsed = false
                fileFilter = FileNameExtensionFilter("MBTiles (*.mbtiles)", "mbtiles")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val sources = chooser.selectedFiles.toList()
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val target = File(PlatformFiles.navDataRoot(), UserMaps.DIR).apply { mkdirs() }
                            sources.filter { it.isFile }.map { source ->
                                val dest = File(target, source.name)
                                if (source.canonicalPath != dest.canonicalPath) {
                                    MbTiles.close(dest.absolutePath)
                                    source.copyTo(dest, overwrite = true)
                                }
                                source.name
                            }
                        }
                    }.onSuccess { imported.value(it) }.onFailure { failed.value() }
                }
            }
        }
    }
}
