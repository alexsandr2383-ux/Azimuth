package ru.azimut.app.ui.components

import androidx.compose.runtime.Composable

/**
 * Multi-format track/waypoint file picker: gpx, kml, kmz, tcx, csv, geojson/json.
 * [onPicked] gets the file name, the decoded text (for text formats) and the raw
 * bytes (for KMZ and friends).
 */
@Composable
expect fun rememberTrackFilePicker(
    onPicked: (fileName: String, content: String, bytes: ByteArray?) -> Unit,
    onError: () -> Unit,
): () -> Unit

/** Saves arbitrary bytes under a user-chosen name; [onSaved] gets the location. */
@Composable
expect fun rememberBytesSaver(
    onSaved: (location: String) -> Unit,
    onError: () -> Unit,
): (suggestedName: String, bytes: ByteArray, mimeType: String) -> Unit

/**
 * Folder picker for sync targets: returns an absolute path (desktop) or a SAF
 * tree URI (Android) that the platform storage can resolve later.
 */
@Composable
expect fun rememberFolderPicker(
    onPicked: (location: String) -> Unit,
): () -> Unit
