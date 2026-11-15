package ru.azimut.app.core.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore: an app-scoped AES key encrypts every secret with AES-GCM.
 * Ciphertext lives in the app's private prefs area, not NavData.
 */
actual object SecretStore {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "azimut-sync-key"
    private const val GCM_TAG_BITS = 128
    private const val PREFS = "azimut-secrets"
    private val prefs by lazy { ru.azimut.app.core.platform.AndroidApp.context.getSharedPreferences(PREFS, 0) }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    actual fun saveSecret(key: String, value: String) {
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(key, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString("$key.ct", Base64.encodeToString(ct, Base64.NO_WRAP))
                .apply()
        }
    }

    actual fun loadSecret(key: String): String? = runCatching {
        val ivB64 = prefs.getString(key, null) ?: return null
        val ctB64 = prefs.getString("$key.ct", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivB64, Base64.NO_WRAP)),
        )
        cipher.doFinal(Base64.decode(ctB64, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }.getOrNull()

    actual fun deleteSecret(key: String) {
        prefs.edit().remove(key).remove("$key.ct").apply()
    }
}
