package ru.azimut.app.core.platform

/** Minimal GET client for optional online data (weather). Returns null on any failure. */
expect object PlatformHttp {
    suspend fun getText(url: String): String?

    /**
     * Arbitrary HTTP request (GET/PUT/PROPFIND/MKCOL/DELETE...) for user-initiated
     * downloads and WebDAV sync. Header names in the result are lower-case.
     */
    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        rangeFrom: Long = 0L,
    ): HttpResp?
}

data class HttpResp(
    val status: Int,
    val headers: Map<String, String>,
    val body: ByteArray,
)
