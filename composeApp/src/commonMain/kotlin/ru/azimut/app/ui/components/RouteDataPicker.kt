package ru.azimut.app.ui.components

import androidx.compose.runtime.Composable

@Composable
expect fun rememberRouteDataPicker(
    onImported: (count: Int) -> Unit,
    onError: () -> Unit,
): () -> Unit
