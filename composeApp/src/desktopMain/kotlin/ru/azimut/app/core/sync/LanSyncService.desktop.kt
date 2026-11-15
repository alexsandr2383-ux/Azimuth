package ru.azimut.app.core.sync

import com.sun.net.httpserver.HttpExchange
import kotlinx.serialization.encodeToString
import com.sun.net.httpserver.HttpServer
import ru.azimut.app.core.platform.PlatformFiles
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * Desktop LAN sync server over the JDK's built-in com.sun.net.httpserver —
 * no new dependency. Files are served from NavData with a shared random token;
 * path traversal is rejected, writes are atomic-ish (tmp + rename).
 */
actual object LanSyncService {
    private var server: HttpServer? = null
    private var info: LanServerInfo? = null
    private var executor: java.util.concurrent.ExecutorService? = null

    actual val running: Boolean get() = server != null

    actual fun start(): LanServerInfo? = runCatching {
        if (server != null) return info
        val token = newToken()
        val http = HttpServer.create(InetSocketAddress("0.0.0.0", 0), 0)
        http.createContext("/") { exchange -> handle(exchange, token) }
        val exec = Executors.newFixedThreadPool(2)
        http.executor = exec
        executor = exec
        http.start()
        server = http
        info = LanServerInfo(
            port = http.address.port,
            token = token,
            addresses = localAddresses(),
        )
        info
    }.getOrNull()

    actual fun stop() {
        runCatching { server?.stop(0) }
        runCatching { executor?.shutdown() }
        executor = null
        server = null
        info = null
    }

    private fun newToken(): String {
        val alphabet = "ACDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        return buildString { repeat(12) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
    }

    private fun localAddresses(): List<String> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filter { it is java.net.Inet4Address && it.isSiteLocalAddress }
            .map { (it as java.net.Inet4Address).hostAddress }
            .toList()
            .distinct()
    }.getOrDefault(emptyList())

    private fun handle(exchange: HttpExchange, token: String) {
        runCatching {
            val given = exchange.requestHeaders.getFirst("X-Azimut-Token")
            if (given != token) {
                exchange.sendResponseHeaders(403, -1)
                return
            }
            val uri = exchange.requestURI
            when {
                uri.path == "/manifest" && exchange.requestMethod == "GET" -> {
                    val json = ru.azimut.app.core.sync.SyncEngine.localManifest()
                        .let { kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(it) }
                    val bytes = json.toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                uri.path == "/file" -> {
                    val path = exchange.requestURI.query
                        ?.split('&')
                        ?.firstOrNull { it.startsWith("path=") }
                        ?.substring(5)
                        ?.let(java.net.URLDecoder::decode)
                        ?: ""
                    // Containment against the real filesystem. Rejecting only ".."
                    // and a leading '/' is not enough: File(root, "C:/x") silently
                    // drops the root on Windows, so drive-qualified paths must fail
                    // via canonical-path containment.
                    val root = File(PlatformFiles.navDataRoot()).canonicalFile
                    if (path.isBlank() || path.contains("..")) {
                        exchange.sendResponseHeaders(400, -1)
                        return
                    }
                    val file = File(root, path)
                    if (file.canonicalFile != root &&
                        !file.canonicalPath.startsWith(root.canonicalPath + File.separator)
                    ) {
                        exchange.sendResponseHeaders(400, -1)
                        return
                    }
                    when (exchange.requestMethod) {
                        "GET" -> {
                            if (!file.isFile) {
                                exchange.sendResponseHeaders(404, -1)
                                return
                            }
                            exchange.sendResponseHeaders(200, file.length())
                            exchange.responseBody.use { Files.copy(file.toPath(), it) }
                        }
                        "PUT" -> {
                            file.parentFile?.mkdirs()
                            val tmp = File(file.parentFile, file.name + ".lan-tmp")
                            // Stream the body to disk: bodies can be hundreds of MB.
                            val written = runCatching {
                                exchange.requestBody.use { input ->
                                    tmp.outputStream().use { input.copyTo(it) }
                                }
                            }.isSuccess
                            // File.renameTo() does not replace an existing target on
                            // Windows and would report success via a silent 200; use
                            // Files.move with REPLACE_EXISTING and fail loudly.
                            val moved = written && runCatching {
                                Files.move(
                                    tmp.toPath(),
                                    file.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                                )
                            }.recoverCatching {
                                Files.move(
                                    tmp.toPath(),
                                    file.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                )
                            }.isSuccess
                            if (moved) {
                                exchange.sendResponseHeaders(200, -1)
                            } else {
                                tmp.delete()
                                exchange.sendResponseHeaders(500, -1)
                            }
                        }
                        "DELETE" -> {
                            if (!file.exists() || file.delete()) {
                                exchange.sendResponseHeaders(200, -1)
                            } else {
                                exchange.sendResponseHeaders(500, -1)
                            }
                        }
                        else -> exchange.sendResponseHeaders(405, -1)
                    }
                }
                else -> exchange.sendResponseHeaders(404, -1)
            }
        }.getOrDefault(run { exchange.sendResponseHeaders(500, -1); exchange.close() })
    }
}
