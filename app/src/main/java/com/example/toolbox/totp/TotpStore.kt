package com.example.toolbox.totp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import androidx.core.content.edit
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class TotpStore(context: Context) {
    private val appContext = context.applicationContext
    private val vaultFile = AtomicFile(File(appContext.noBackupFilesDir, LOCAL_FILE_NAME))
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun queryAllForSync(): List<TotpEntry> {
        if (!vaultFile.baseFile.exists()) return emptyList()
        val raw = runCatching { vaultFile.readFully() }.getOrNull() ?: return emptyList()
        if (raw.size <= GCM_IV_BYTES) return emptyList()
        val json = decryptBytes(raw) ?: return emptyList()
        return runCatching { TotpVaultCrypto.parseEntriesJson(json) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun queryVisible(): List<TotpEntry> =
        queryAllForSync()
            .filter { it.deletedAtMillis == null }
            .sortedWith(
                compareBy<TotpEntry> { it.issuer.lowercase() }
                    .thenBy { it.accountName.lowercase() }
                    .thenBy { it.createdAtMillis },
            )

    @Synchronized
    fun upsert(entry: TotpEntry) {
        val current = queryAllForSync().toMutableList()
        val index = current.indexOfFirst { it.uuid == entry.uuid }
        if (index >= 0) {
            current[index] = entry
        } else {
            current.add(entry)
        }
        writeAll(current)
    }

    @Synchronized
    fun softDelete(uuid: String, nowMillis: Long = System.currentTimeMillis()) {
        val current = queryAllForSync().map { item ->
            if (item.uuid == uuid) {
                item.copy(
                    updatedAtMillis = maxOf(nowMillis, item.updatedAtMillis + 1L),
                    deletedAtMillis = nowMillis,
                )
            } else {
                item
            }
        }
        writeAll(current)
    }

    @Synchronized
    fun replaceAll(entries: List<TotpEntry>) {
        writeAll(entries)
    }

    @Synchronized
    fun mergeAndSave(remoteEntries: List<TotpEntry>): TotpMergeResult {
        val merged = TotpSyncMerge.merge(queryAllForSync(), remoteEntries)
        writeAll(merged.entries)
        return merged
    }

    fun savedPassphrase(): String? =
        prefs.getString(KEY_VAULT_PASSPHRASE_ENC, null)?.let { encoded ->
            runCatching {
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                decryptBytes(bytes)
            }.getOrNull()
        }

    fun savePassphrase(passphrase: String?) {
        prefs.edit {
            if (passphrase.isNullOrEmpty()) {
                remove(KEY_VAULT_PASSPHRASE_ENC)
            } else {
                val encrypted = encryptBytes(passphrase)
                putString(KEY_VAULT_PASSPHRASE_ENC, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }
        }
    }

    private fun writeAll(entries: List<TotpEntry>) {
        val json = TotpVaultCrypto.serializeEntriesJson(entries)
        val encrypted = encryptBytes(json)
        val output = vaultFile.startWrite()
        try {
            output.write(encrypted)
            output.fd.sync()
            vaultFile.finishWrite(output)
        } catch (failure: Throwable) {
            vaultFile.failWrite(output)
            throw failure
        }
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

    private fun encryptBytes(plainText: String): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return cipher.iv + ciphertext
    }

    private fun decryptBytes(raw: ByteArray): String? = runCatching {
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
        private const val LOCAL_FILE_NAME = "totp-local.bin"
        private const val PREFS_NAME = "totp_prefs"
        private const val KEY_VAULT_PASSPHRASE_ENC = "vault_passphrase_enc"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "toolbox_totp_local"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
