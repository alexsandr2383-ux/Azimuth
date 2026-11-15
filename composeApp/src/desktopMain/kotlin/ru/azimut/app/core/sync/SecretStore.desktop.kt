package ru.azimut.app.core.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop secrets: AES-GCM with a local key file next to NavData (DPAPI is not
 * reachable from a plain JVM, so this is obfuscation, not system-grade protection —
 * the Help screen says so). Secrets live in Documents/Azimut/azimut.key, never in
 * NavData, so they are not picked up by sync or backups.
 */
actual object SecretStore {
    private const val KEY_FILE = "azimut.key"
    private val json = Json { ignoreUnknownKeys = true }

    private val keyFile: File
        get() {
            val home = System.getProperty("user.home") ?: "."
            return File(home, "Documents/Azimut/$KEY_FILE")
        }

    private fun secretKey(): SecretKey {
        if (keyFile.isFile) {
            val lines = keyFile.readLines().filter { it.isNotBlank() }
            val keyLine = lines.firstOrNull { !it.startsWith("#") } ?: return regenerate()
            val bytes = decodeB64(keyLine)
            if (bytes.size == 32) return SecretKeySpec(bytes, "AES")
            return regenerate()
        }
        return regenerate()
    }

    private fun regenerate(): SecretKey {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256, SecureRandom())
        val key = generator.generateKey()
        val tmp = File(keyFile.parentFile, keyFile.name + ".tmp")
        keyFile.parentFile?.mkdirs()
        tmp.writeText(
            encodeB64(key.encoded) + "\n" +
                "# Azimuth local secret key (AES-256). Delete to invalidate stored passwords.\n\u0002",
        )
        tmp.renameTo(keyFile)
        runCatching {
            Files.setPosixFilePermissions(
                keyFile.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
        return key
    }

    private fun loadEntries(): MutableMap<String, String> {
        val secretKey = secretKey()
        val blob = keyFile.readLines().firstOrNull { it.startsWith("\u0002") }
            ?.substring(1)?.trim()
            ?.let(::decodeB64)
            ?: return mutableMapOf()
        if (blob.size < 12) return mutableMapOf()
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, blob.copyOfRange(0, 12)))
            val payload = cipher.doFinal(blob.copyOfRange(12, blob.size)).toString(Charsets.UTF_8)
            json.decodeFromString<Map<String, String>>(payload).toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    private fun persist(entries: Map<String, String>) {
        val secretKey = secretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val payload = json.encodeToString(entries)
        val blob = cipher.iv + cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        val lines = keyFile.readLines()
        val keyLine = lines.firstOrNull { !it.startsWith("#") } ?: encodeB64(secretKey.encoded)
        val comment = lines.firstOrNull { it.startsWith("#") }
            ?: "# Azimuth local secret key (AES-256). Delete to invalidate stored passwords."
        // Atomic write: a power cut mid-persist must not corrupt the key file
        // (every stored secret becomes unrecoverable then).
        val tmp = File(keyFile.parentFile, keyFile.name + ".tmp")
        tmp.writeText("$keyLine\n$comment\n\u0002${encodeB64(blob)}\n")
        runCatching {
            java.nio.file.Files.move(
                tmp.toPath(),
                keyFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        }.recoverCatching {
            java.nio.file.Files.move(
                tmp.toPath(),
                keyFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }.onFailure {
            tmp.delete()
            throw it
        }
    }

    actual fun saveSecret(key: String, value: String) {
        runCatching {
            val entries = loadEntries()
            entries[key] = value
            persist(entries)
        }
    }

    actual fun loadSecret(key: String): String? = runCatching { loadEntries()[key] }.getOrNull()

    actual fun deleteSecret(key: String) {
        runCatching {
            val entries = loadEntries()
            entries.remove(key)
            persist(entries)
        }
    }

    private fun encodeB64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

    private fun decodeB64(text: String): ByteArray = java.util.Base64.getDecoder().decode(text.trim())
}
