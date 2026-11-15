package ru.azimut.app.ui.components

import androidx.compose.runtime.Composable

@Composable
expect fun rememberGpxFilePicker(
    onPicked: (fileName: String, xml: String) -> Unit,
    onError: () -> Unit,
): () -> Unit
