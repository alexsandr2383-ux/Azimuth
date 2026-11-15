package ru.azimut.app.ui.components

import androidx.compose.runtime.Composable

@Composable
expect fun HoverTooltip(text: String, content: @Composable () -> Unit)
