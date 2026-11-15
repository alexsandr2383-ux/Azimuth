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

@Composable
actual fun rememberGpxFilePicker(
    onPicked: (fileName: String, xml: String) -> Unit,
    onError: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val picked = rememberUpdatedState(onPicked)
    val failed = rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val name = context.displayName(uri) ?: "import.gpx"
                val xml = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("Cannot open GPX")
                name to xml
            }.onSuccess { (name, xml) ->
                picked.value(name, xml)
            }.onFailure {
                failed.value()
            }
        }
    }
    return remember(launcher) {
        {
            launcher.launch(
                arrayOf(
                    "application/gpx+xml",
                    "application/xml",
                    "text/xml",
                    "text/plain",
                    "*/*",
                )
            )
        }
    }
}

private fun Context.displayName(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
