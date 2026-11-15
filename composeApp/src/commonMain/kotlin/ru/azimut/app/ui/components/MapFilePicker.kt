package ru.azimut.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Lets the user pick .mbtiles files and copies them into NavData/maps/ off the UI thread.
 * [onImported] receives the copied file names (on the UI thread).
 */
@Composable
expect fun rememberMapFilePicker(
    onImported: (fileNames: List<String>) -> Unit,
    onError: () -> Unit,
): () -> Unit
