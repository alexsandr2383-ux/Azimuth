package ru.azimut.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Asks where to save a GPX file and writes [content] there.
 * [onSaved] gets a human-readable location of the file.
 */
@Composable
expect fun rememberGpxSaver(
    onSaved: (location: String) -> Unit,
    onError: () -> Unit,
): (suggestedName: String, content: String) -> Unit

/** File name without characters Windows/Android reject. */
fun gpxFileName(name: String): String =
    name.replace(Regex("[\\u0000-\\u001F]"), "")
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
        .trimEnd('.', ' ')
        .ifBlank { "route" } + ".gpx"
