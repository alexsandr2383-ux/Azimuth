package ru.azimut.app.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun HoverTooltip(text: String, content: @Composable () -> Unit) = content()
