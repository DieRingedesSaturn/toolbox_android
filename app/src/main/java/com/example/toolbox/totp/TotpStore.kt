package com.example.toolbox.totp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import androidx.core.content.edit
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Thrown when the on-device token file exists but cannot be decrypted or parsed.
 * Callers must not replace the file: an empty list is only returned when the
 * vault is actually absent.
 */
class TotpLocalVaultException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal interface TotpLocalCipher {
    fun encrypt(plainText: String): ByteArray
    fun decrypt(raw: ByteArray): String?
}

class TotpStore internal constructor(
    context: Context,
    private val vaultFile: AtomicFile,
    private val cipher: TotpLocalCipher,
) {
    constructor(context: Context) : this(
        context.applicationContext,
        AtomicFile(File(context.applicationContext.noBackupFilesDir, LOCAL_FILE_NAME)),
        AndroidKeystoreTotpCipher,
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun queryAllForSync(): List<TotpEntry> = synchronized(vaultLock) { readEntries() }

    fun queryVisible(): List<TotpEntry> = synchronized(vaultLock) {
        readEntries()
            .filter { it.deletedAtMillis == null }
            .sortedWith(
                compareBy<TotpEntry> { it.issuer.lowercase() }
                    .thenBy { it.accountName.lowercase() }
                    .thenBy { it.createdAtMillis },
            )
    }

    fun upsert(entry: TotpEntry) = synchronized(vaultLock) {
        val current = readEntries().toMutableList()
        val index = current.indexOfFirst { it.uuid == entry.uuid }
        if (index >= 0) {
            require(current[index].updatedAtMillis < Long.MAX_VALUE)
            current[index] = entry.copy(
                updatedAtMillis = maxOf(entry.updatedAtMillis, current[index].updatedAtMillis + 1L),
            )
        } else {
            current.add(entry)
        }
        writeEntries(current)
    }

    fun softDelete(uuid: String, nowMillis: Long = System.currentTimeMillis()) = synchronized(vaultLock) {
        val current = readEntries().map { item ->
            if (item.uuid == uuid) {
                item.copy(
                    updatedAtMillis = maxOf(nowMillis, item.updatedAtMillis + 1L),
                    deletedAtMillis = nowMillis,
                )
            } else {
                item
            }
        }
        writeEntries(current)
    }

    /**
     * Replaces the vault. Refuses to overwrite a file that cannot be read unless
     * [overwriteUnreadable] is set, which is reserved for an explicit import
     * Replace. Sync must leave the flag false so a decrypt failure cannot wipe tokens.
     */
    fun replaceAll(entries: List<TotpEntry>, overwriteUnreadable: Boolean = false) = synchronized(vaultLock) {
        if (!overwriteUnreadable) {
            readEntries()
        }
        writeEntries(entries)
    }

    fun mergeAndSave(remoteEntries: List<TotpEntry>): TotpMergeResult = synchronized(vaultLock) {
        val merged = TotpSyncMerge.merge(readEntries(), remoteEntries)
        writeEntries(merged.entries)
        return merged
    }

    /** A deliberate restore is a new revision, including tombstones for omitted tokens. */
    fun restore(
        entries: List<TotpEntry>,
        overwriteUnreadable: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
    ) = synchronized(vaultLock) {
        val current = if (overwriteUnreadable) {
            runCatching { readEntries() }.getOrDefault(emptyList())
        } else {
            readEntries()
        }
        val latest = (current + entries).maxOfOrNull { it.updatedAtMillis } ?: 0L
        require(latest < Long.MAX_VALUE)
        val stamp = maxOf(nowMillis, latest + 1L)
        val incomingIds = entries.mapTo(HashSet()) { it.uuid }
        val restored = entries.map {
            it.copy(updatedAtMillis = stamp, deletedAtMillis = it.deletedAtMillis?.let { stamp })
        } + current.filter { it.uuid !in incomingIds }.map {
            it.copy(updatedAtMillis = stamp, deletedAtMillis = stamp)
        }
        writeEntries(restored)
    }

    fun savedPassphrase(): String? =
        decryptStored(KEY_VAULT_PASSPHRASE_ENC) ?: decryptStored(KEY_DEFAULT_BACKUP_PASSPHRASE_ENC)

    fun savePassphrase(passphrase: String?) {
        encryptAndStore(KEY_VAULT_PASSPHRASE_ENC, passphrase)
    }

    /** Shared backup passphrase for general settings and ledger; TOTP may override its own. */
    fun savedDefaultBackupPassphrase(): String? =
        decryptStored(KEY_DEFAULT_BACKUP_PASSPHRASE_ENC) ?: decryptStored(KEY_VAULT_PASSPHRASE_ENC)

    fun saveDefaultBackupPassphrase(passphrase: String?) {
        encryptAndStore(KEY_DEFAULT_BACKUP_PASSPHRASE_ENC, passphrase)
    }

    private fun decryptStored(key: String): String? =
        prefs.getString(key, null)?.let { encoded ->
            runCatching {
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                cipher.decrypt(bytes)
            }.getOrNull()
        }

    private fun encryptAndStore(key: String, passphrase: String?) {
        prefs.edit {
            if (passphrase.isNullOrEmpty()) {
                remove(key)
            } else {
                val encrypted = cipher.encrypt(passphrase)
                putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }
        }
    }

    /**
     * Empty only when no vault file exists. A present file that cannot be
     * decrypted or parsed throws [TotpLocalVaultException] so callers cannot
     * mistake it for an empty store and write over it.
     */
    private fun readEntries(): List<TotpEntry> {
        val raw = try {
            vaultFile.readFully()
        } catch (_: FileNotFoundException) {
            return emptyList()
        } catch (failure: Exception) {
            throw TotpLocalVaultException("Couldn't read the token vault", failure)
        }
        val json = cipher.decrypt(raw)
            ?: throw TotpLocalVaultException("Couldn't decrypt the token vault")
        return try {
            TotpVaultCrypto.parseEntriesJson(json)
        } catch (failure: Exception) {
            throw TotpLocalVaultException("Couldn't parse the token vault", failure)
        }
    }

    private fun writeEntries(entries: List<TotpEntry>) {
        val json = TotpVaultCrypto.serializeEntriesJson(entries)
        val encrypted = cipher.encrypt(json)
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

    companion object {
        private val vaultLock = Any()
        internal const val LOCAL_FILE_NAME = "totp-local.bin"
        private const val PREFS_NAME = "totp_prefs"
        private const val KEY_VAULT_PASSPHRASE_ENC = "vault_passphrase_enc"
        private const val KEY_DEFAULT_BACKUP_PASSPHRASE_ENC = "default_backup_passphrase_enc"
    }
}

private object AndroidKeystoreTotpCipher : TotpLocalCipher {
    override fun encrypt(plainText: String): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        val key = secretKey(create = true)
            ?: throw TotpLocalVaultException("Couldn't create the token keystore key")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return cipher.iv + ciphertext
    }

    override fun decrypt(raw: ByteArray): String? = runCatching {
        if (raw.size <= GCM_IV_BYTES) return@runCatching null
        val key = secretKey(create = false) ?: return@runCatching null
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_BITS, raw.copyOfRange(0, GCM_IV_BYTES)),
        )
        String(cipher.doFinal(raw.copyOfRange(GCM_IV_BYTES, raw.size)), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(create: Boolean): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }
        if (!create) return null
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

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "toolbox_totp_local"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
}
