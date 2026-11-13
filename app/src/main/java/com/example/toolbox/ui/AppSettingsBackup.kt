package com.example.toolbox.ui

import android.content.Context
import com.example.toolbox.ledger.BackupRecoveryStore
import com.example.toolbox.ledger.BackupTool
import com.example.toolbox.ledger.WebDavClient
import com.example.toolbox.ledger.WebDavException
import com.example.toolbox.ledger.WebDavFailure
import com.example.toolbox.ledger.WebDavHistory
import com.example.toolbox.totp.TotpVaultCrypto
import java.time.ZoneId
import org.json.JSONObject

data class SettingsBackupResult(val snapshotName: String?, val snapshotFailure: WebDavFailure?)

/** General preferences only. Every public operation blocks and belongs on Dispatchers.IO. */
object AppSettingsBackup {
    private const val FORMAT = "toolbox-settings-vault"
    private const val AAD = "toolbox-settings-vault:v1"
    private const val FILE = "settings.json"
    private const val MAX_BYTES = 2 * 1024 * 1024

    fun encode(settings: JSONObject, passphrase: String): String {
        val allowed = AppPreferences.sanitizedBackupSettings(settings)
        val plain = JSONObject().put("format", "toolbox-settings").put("version", 1)
            .put("createdAtMillis", System.currentTimeMillis()).put("preferences", allowed).toString()
        require(plain.toByteArray(Charsets.UTF_8).size <= MAX_BYTES / 2)
        return TotpVaultCrypto.encryptPayload(plain, passphrase, FORMAT, AAD)
    }

    fun decode(body: String, passphrase: String): JSONObject {
        val plain = TotpVaultCrypto.decryptPayload(body, passphrase, mapOf(FORMAT to AAD), MAX_BYTES)
        val root = JSONObject(plain)
        require(root.getString("format") == "toolbox-settings" && root.getInt("version") == 1)
        return AppPreferences.sanitizedBackupSettings(root.getJSONObject("preferences"))
    }

    fun backup(client: WebDavClient, settings: JSONObject, passphrase: String): SettingsBackupResult {
        client.ensureFolder()
        val old = client.get(FILE)
        val etag = old?.let { WebDavClient.ifMatchValue(it.etag) }
        if (old != null) {
            if (etag == null) throw WebDavException(WebDavFailure.VERSION_UNAVAILABLE)
            // Changing the entered passphrase must never silently replace an existing vault.
            decode(old.body, passphrase)
        }
        val body = encode(settings, passphrase)
        client.put(FILE, body, ifMatch = etag, ifNoneMatch = if (old == null) "*" else null)
        return try {
            SettingsBackupResult(WebDavHistory(client, BackupTool.SETTINGS).save(
                body, System.currentTimeMillis(), ZoneId.systemDefault(),
            ), null)
        } catch (failure: WebDavException) {
            SettingsBackupResult(null, failure.failure)
        } catch (_: Exception) {
            SettingsBackupResult(null, WebDavFailure.NETWORK)
        }
    }

    fun restore(context: Context, body: String, passphrase: String) {
        val incoming = decode(body, passphrase)
        val preferences = AppPreferences(context)
        BackupRecoveryStore(context, BackupTool.SETTINGS).save(encode(preferences.backupSettings(), passphrase))
        preferences.restoreSettings(incoming)
    }
}
