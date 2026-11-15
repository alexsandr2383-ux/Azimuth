package ru.azimut.app.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import ru.azimut.app.core.platform.PlatformFiles
import ru.azimut.app.core.routing.RouteData
import java.io.File

@Composable
actual fun rememberRouteDataPicker(
    onImported: (count: Int) -> Unit,
    onError: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val imported = rememberUpdatedState(onImported)
    val failed = rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            runCatching {
                val target = File(PlatformFiles.navDataRoot(), RouteData.SEGMENTS_DIR).apply { mkdirs() }
                var count = 0
                uris.forEach { uri ->
                    val name = context.routeDisplayName(uri) ?: return@forEach
                    if (!name.endsWith(".rd5", ignoreCase = true)) return@forEach
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        File(target, name).outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Cannot open $name")
                    count++
                }
                imported.value(count)
            }.onFailure { failed.value() }
        }
    }
    return remember(launcher) {
        {
            launcher.launch(arrayOf("application/octet-stream", "*/*"))
        }
    }
}

private fun Context.routeDisplayName(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
