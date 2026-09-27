package com.example.toolbox.ledger

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.net.URI
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class WebDavConfig(
    val folderUrl: String,
    val username: String,
    val password: String,
) {
    val isComplete: Boolean
        get() = folderUrl.isNotBlank() && username.isNotBlank() && password.isNotEmpty()
}

object WebDavPaths {

    fun normalizeFolderUrl(raw: String): String? {
        val trimmed = raw.trim().replace(" ", "%20")
        if (trimmed.isEmpty()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        val ascii = uri.toASCIIString()
        return if (ascii.endsWith("/")) ascii else "$ascii/"
    }

    fun fileUrl(folderUrl: String, name: String): String =
        folderUrl + URLEncoder.encode(name, Charsets.UTF_8.name()).replace("+", "%20")

    fun basicAuthHeader(username: String, password: String): String =
        "Basic " + java.util.Base64.getEncoder()
            .encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
}

/**
 * Persists the WebDAV endpoint locally. The password is encrypted with an
 * Android Keystore AES/GCM key before it touches SharedPreferences.
 */
class WebDavConfigStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): WebDavConfig = WebDavConfig(
        folderUrl = prefs.getString(KEY_FOLDER_URL, "") ?: "",
        username = prefs.getString(KEY_USERNAME, "") ?: "",
        password = prefs.getString(KEY_PASSWORD_ENC, null)?.let { decrypt(it) } ?: "",
    )

    fun isPasswordUnavailable(): Boolean {
        val encoded = prefs.getString(KEY_PASSWORD_ENC, null) ?: return false
        return decrypt(encoded) == null
    }

    fun save(config: WebDavConfig) {
        prefs.edit {
            putString(KEY_FOLDER_URL, config.folderUrl)
            putString(KEY_USERNAME, config.username)
            if (config.password.isEmpty()) {
                remove(KEY_PASSWORD_ENC)
            } else {
                putString(KEY_PASSWORD_ENC, encrypt(config.password))
            }
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    fun lastSyncAtMillis(): Long? =
        if (prefs.contains(KEY_LAST_SYNC_AT)) prefs.getLong(KEY_LAST_SYNC_AT, 0L) else null

    fun saveLastSync(millis: Long) {
        prefs.edit { putLong(KEY_LAST_SYNC_AT, millis) }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
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

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = runCatching {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        if (raw.size <= GCM_IV_BYTES) return@runCatching null
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, raw.copyOfRange(0, GCM_IV_BYTES)),
        )
        String(cipher.doFinal(raw.copyOfRange(GCM_IV_BYTES, raw.size)), Charsets.UTF_8)
    }.getOrNull()

    companion object {
        private const val PREFS_NAME = "ledger_webdav"
        private const val KEY_FOLDER_URL = "folder_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD_ENC = "password_enc"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "toolbox_ledger_webdav"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
