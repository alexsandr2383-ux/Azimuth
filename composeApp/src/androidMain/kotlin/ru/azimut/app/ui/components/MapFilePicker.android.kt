package ru.azimut.app.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.azimut.app.core.map.UserMaps
import ru.azimut.app.core.platform.PlatformFiles
import java.io.File

@Composable
actual fun rememberMapFilePicker(
    onImported: (fileNames: List<String>) -> Unit,
    onError: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val imported = rememberUpdatedState(onImported)
    val failed = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val target = File(PlatformFiles.navDataRoot(), UserMaps.DIR).apply { mkdirs() }
                    uris.mapNotNull { uri ->
                        val name = context.mapDisplayName(uri) ?: return@mapNotNull null
                        if (!name.endsWith(".mbtiles", ignoreCase = true)) return@mapNotNull null
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            File(target, name).outputStream().use { output -> input.copyTo(output) }
                        } ?: error("Cannot open $name")
                        name
                    }
                }
            }.onSuccess { imported.value(it) }.onFailure { failed.value() }
        }
    }
    return remember(launcher) {
        { launcher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "*/*")) }
    }
}

private fun Context.mapDisplayName(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
