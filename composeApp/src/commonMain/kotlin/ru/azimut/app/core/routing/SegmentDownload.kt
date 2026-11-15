package ru.azimut.app.core.routing

/** Downloads BRouter .rd5 segment files from brouter.de into NavData/routing/segments4. */
expect object SegmentDownloader {
    /** Returns the names that failed to download. */
    suspend fun download(names: List<String>, onProgress: (done: Int, total: Int) -> Unit): List<String>
}
