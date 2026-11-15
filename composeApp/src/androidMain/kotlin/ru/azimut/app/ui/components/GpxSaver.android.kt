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
actual fun rememberGpxSaver(
    onSaved: (location: String) -> Unit,
    onError: () -> Unit,
): (suggestedName: String, content: String) -> Unit {
    val context = LocalContext.current
    val saved = rememberUpdatedState(onSaved)
    val failed = rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri ->
        val content = pending
        pending = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        // SAF writes can be slow for long tracks — keep them off the main thread.
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                    ?: error("Cannot open $uri")
            }.onSuccess { saved.value(uri.lastPathSegment ?: uri.toString()) }.onFailure { failed.value() }
        }
    }
    return remember(launcher) {
        { suggestedName, content ->
            pending = content
            runCatching { launcher.launch(suggestedName) }.onFailure { failed.value() }
        }
    }
}
