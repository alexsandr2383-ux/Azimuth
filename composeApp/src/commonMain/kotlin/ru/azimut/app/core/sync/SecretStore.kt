package ru.azimut.app.core.sync

/**
 * Credential storage: Android Keystore (hardware-backed AES) on Android; an AES key
 * file next to NavData on desktop (DPAPI is not reachable from a JVM without native
 * code — that limitation is stated in Help).
 */
expect object SecretStore {
    fun saveSecret(key: String, value: String)
    fun loadSecret(key: String): String?
    fun deleteSecret(key: String)
}
