package com.example.toolbox.totp

import com.example.toolbox.ledger.BackupTool
import com.example.toolbox.ledger.WebDavHistory
import com.example.toolbox.ledger.WebDavClient
import com.example.toolbox.ledger.WebDavException
import com.example.toolbox.ledger.WebDavFailure
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

object TotpWebDavSnapshots {
    const val KEEP = 10
    const val INDEX_FILE = "totp-backups.json"
    val NAME_REGEX = WebDavHistory.nameRegex(BackupTool.TOTP)

    fun snapshotName(millis: Long, zone: ZoneId): String =
        WebDavHistory.snapshotName(BackupTool.TOTP, millis, zone)

    fun parseIndex(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONObject(json).optJSONArray("snapshots") ?: JSONArray()
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { NAME_REGEX.matches(it) }
            }
        }.getOrDefault(emptyList())
    }

    fun indexJson(names: List<String>): String {
        val array = JSONArray()
        names.forEach { array.put(it) }
        return JSONObject().put("snapshots", array).toString()
    }

    fun prune(existing: List<String>, newName: String): Pair<List<String>, List<String>> {
        val combined = (existing + newName).distinct().filter { NAME_REGEX.matches(it) }
        val keep = combined.takeLast(KEEP)
        return keep to (combined - keep.toSet())
    }
}

data class TotpWebDavSyncResult(
    val totalTokens: Int,
    val pulledCount: Int,
    val pushedCount: Int,
    val snapshotName: String?,
    val snapshotFailure: WebDavFailure? = null,
)

class TotpWebDavSync(
    private val client: WebDavClient,
    private val passphrase: String,
    private val loadLocal: () -> List<TotpEntry>,
    private val applyMerged: (List<TotpEntry>) -> Unit,
    private val iterations: Int = TotpVaultCrypto.DEFAULT_ITERATIONS,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Reads a legacy file in the parent folder on the first Toolbox sync. */
    private val legacyClient: WebDavClient? = null,
    private val legacyClients: List<WebDavClient> = listOfNotNull(legacyClient),
) {

    fun pull(): List<TotpEntry>? {
        val remote = client.get(VAULT_FILE)
        if (remote != null && WebDavClient.ifMatchValue(remote.etag) == null) {
            throw WebDavException(WebDavFailure.VERSION_UNAVAILABLE)
        }
        val file = remote ?: legacyClients.firstNotNullOfOrNull { it.get(VAULT_FILE) } ?: return null
        return TotpVaultCrypto.decryptVault(file.body, passphrase)
    }

    fun sync(nowMillis: Long = System.currentTimeMillis()): TotpWebDavSyncResult {
        client.ensureFolder()
        var attempt = 0
        var finalMerged: TotpMergeResult? = null
        var uploadedBody: String? = null
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val remote = client.get(VAULT_FILE)
            // A missing tool vault migrates from the nearest legacy folder;
            // only the new tool folder is written. Legacy files stay untouched.
            val remoteSource = remote ?: legacyClients.firstNotNullOfOrNull { it.get(VAULT_FILE) }
            val etag = remote?.let { WebDavClient.ifMatchValue(it.etag) }
            if (remote != null && etag == null) {
                throw WebDavException(WebDavFailure.VERSION_UNAVAILABLE)
            }
            val remoteEntries = if (remoteSource == null) {
                emptyList()
            } else {
                TotpVaultCrypto.decryptVault(remoteSource.body, passphrase)
            }
            val merged = TotpSyncMerge.merge(loadLocal(), remoteEntries)
            val body = TotpVaultCrypto.encryptVault(
                entries = merged.entries,
                passphrase = passphrase,
                iterations = iterations,
                nowMillis = nowMillis,
            )
            try {
                if (remote == null) {
                    client.put(VAULT_FILE, body, ifNoneMatch = "*")
                } else {
                    client.put(VAULT_FILE, body, ifMatch = etag)
                }
                applyMerged(merged.entries)
                finalMerged = merged
                uploadedBody = body
                break
            } catch (e: WebDavException) {
                if (e.failure == WebDavFailure.PRECONDITION_FAILED && attempt < MAX_ATTEMPTS) {
                    continue
                }
                throw e
            }
        }
        val merged = finalMerged ?: throw WebDavException(WebDavFailure.PRECONDITION_FAILED)
        val body = uploadedBody!!
        var snapshotName: String? = null
        var snapshotFailure: WebDavFailure? = null
        try {
            snapshotName = WebDavHistory(client, BackupTool.TOTP).save(body, nowMillis, zone)
        } catch (e: WebDavException) {
            snapshotFailure = e.failure
        } catch (_: Exception) {
            snapshotFailure = WebDavFailure.NETWORK
        }
        return TotpWebDavSyncResult(
            totalTokens = merged.entries.count { it.deletedAtMillis == null },
            pulledCount = merged.pulledCount,
            pushedCount = merged.pushedCount,
            snapshotName = snapshotName,
            snapshotFailure = snapshotFailure,
        )
    }

    companion object {
        const val VAULT_FILE = "totp-vault.json"
        private const val MAX_ATTEMPTS = 2
    }
}
