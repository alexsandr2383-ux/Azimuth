package ru.azimut.app.core.export

import ru.azimut.app.core.model.Track

/** Render input for the track sheet: the map sketch plus text lines the renderer draws. */
data class TrackSheet(
    val track: Track,
    /** Optional reference line (the recorded track when exporting a plan). */
    val backgroundLine: List<ru.azimut.app.core.model.GeoPoint> = emptyList(),
    val waypoints: List<ru.azimut.app.core.model.Waypoint> = emptyList(),
    /** Lines drawn under the sketch: title first, then summary and warnings. */
    val lines: List<String>,
    /** Height profile; null when there is no DEM data. */
    val heights: List<Double?>,
)

/**
 * Offscreen raster of the track sheet (sketch + profile + text). The Cyrillic text
 * is drawn by the platform graphics stack, which avoids font embedding in PDF:
 * the PDF embeds this image plus a minimal ASCII footer.
 */
expect object TrackImageRenderer {
    /** Renders to PNG bytes; null when rendering is unavailable. */
    fun renderPng(sheet: TrackSheet, width: Int, height: Int): ByteArray?
}

/** Raw RGB pixels (row-major, top-down) for embedding into the PDF. */
expect object TrackImagePixels {
    fun fromPng(png: ByteArray): Triple<IntArray, Int, Int>?
}
