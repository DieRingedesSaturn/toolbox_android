package com.example.toolbox.totp

import com.example.toolbox.ledger.WebDavClient
import com.example.toolbox.ledger.WebDavException
import com.example.toolbox.ledger.WebDavFailure
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

object TotpWebDavSnapshots {
    const val KEEP = 10
    const val INDEX_FILE = "totp-backups.json"
    val NAME_REGEX = Regex("""^totp-backup-\d{8}-\d{6}\.json$""")
    private val NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun snapshotName(millis: Long, zone: ZoneId): String =
        "totp-backup-${Instant.ofEpochMilli(millis).atZone(zone).format(NAME_FORMATTER)}.json"

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
) {

    fun sync(nowMillis: Long = System.currentTimeMillis()): TotpWebDavSyncResult {
        var attempt = 0
        var finalMerged: TotpMergeResult? = null
        var uploadedBody: String? = null
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val remote = client.get(VAULT_FILE)
            val etag = remote?.let { WebDavClient.ifMatchValue(it.etag) }
            if (remote != null && etag == null) {
                throw WebDavException(WebDavFailure.VERSION_UNAVAILABLE)
            }
            val remoteEntries = if (remote == null) {
                emptyList()
            } else {
                TotpVaultCrypto.decryptVault(remote.body, passphrase)
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
            val name = TotpWebDavSnapshots.snapshotName(nowMillis, zone)
            client.put(name, body)
            val index = TotpWebDavSnapshots.parseIndex(
                client.get(TotpWebDavSnapshots.INDEX_FILE)?.body,
            )
            val (keep, delete) = TotpWebDavSnapshots.prune(index, name)
            client.put(TotpWebDavSnapshots.INDEX_FILE, TotpWebDavSnapshots.indexJson(keep))
            delete.forEach { old -> runCatching { client.delete(old) } }
            snapshotName = name
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
