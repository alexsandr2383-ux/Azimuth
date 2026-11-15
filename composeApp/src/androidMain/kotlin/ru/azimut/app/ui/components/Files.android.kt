package ru.azimut.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
actual fun rememberTrackFilePicker(
    onPicked: (fileName: String, content: String, bytes: ByteArray?) -> Unit,
    onError: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val picked = rememberUpdatedState(onPicked)
    val failed = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Reading can be megabytes over SAF — keep it off the main thread.
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Cannot open $uri")
            }.onSuccess { bytes ->
                val name = uri.lastPathSegment ?: "track.geojson"
                picked.value(name, bytes.toString(Charsets.UTF_8), bytes)
            }.onFailure { failed.value() }
        }
    }
    return remember(launcher) {
        {
            runCatching {
                launcher.launch(
                    arrayOf(
                        "application/gpx+xml",
                        "application/vnd.google-earth.kml+xml",
                        "application/vnd.google-earth.kmz",
                        "application/vnd.garmin.tcx+xml",
                        "text/csv",
                        "application/geo+json",
                        "application/json",
                        "application/octet-stream",
                        "*/*",
                    ),
                )
            }.onFailure { failed.value() }
        }
    }
}

@Composable
actual fun rememberBytesSaver(
    onSaved: (location: String) -> Unit,
    onError: () -> Unit,
): (suggestedName: String, bytes: ByteArray, mimeType: String) -> Unit {
    val context = LocalContext.current
    val saved = rememberUpdatedState(onSaved)
    val failed = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val bytes = pendingBytes
        pendingBytes = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Cannot open $uri")
            }.onSuccess { saved.value(uri.lastPathSegment ?: uri.toString()) }.onFailure { failed.value() }
        }
    }
    return remember(launcher) {
        { suggestedName: String, bytes: ByteArray, mimeType: String ->
            pendingBytes = bytes
            runCatching { launcher.launch(suggestedName) }.onFailure { failed.value() }
        }
    }
}

@Composable
actual fun rememberFolderPicker(
    onPicked: (location: String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val picked = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Persist the grant so the sync engine can reach the folder across restarts.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        picked.value(uri.toString())
    }
    return remember(launcher) {
        { runCatching { launcher.launch(null) } }
    }
}
