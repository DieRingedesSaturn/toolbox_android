package com.example.toolbox.ledger

import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

data class MergeResult(
    val entries: List<LedgerEntry>,
    val pulledCount: Int,
    val pushedCount: Int,
)

object LedgerSyncMerge {

    /**
     * Last-Write-Wins merge by uuid: the entry with the higher
     * [LedgerEntry.updatedAtMillis] wins, ties go to remote (matching
     * [LedgerStore.importSyncPayload]'s >= rule). Every output entry is
     * marked [LedgerSyncStatus.SYNCED] and the list is sorted by
     * updatedAtMillis ascending.
     */
    fun merge(local: List<LedgerEntry>, remote: List<LedgerEntry>): MergeResult {
        val localByUuid = local.associateBy { it.uuid }
        val remoteByUuid = remote.associateBy { it.uuid }
        var pulled = 0
        var pushed = 0
        val merged = (localByUuid.keys + remoteByUuid.keys).map { uuid ->
            val localEntry = localByUuid[uuid]
            val remoteEntry = remoteByUuid[uuid]
            val chosen = when {
                remoteEntry == null -> {
                    pushed++
                    localEntry!!
                }
                localEntry == null -> {
                    pulled++
                    remoteEntry
                }
                remoteEntry.updatedAtMillis >= localEntry.updatedAtMillis -> {
                    if (remoteEntry.updatedAtMillis > localEntry.updatedAtMillis) pulled++
                    remoteEntry
                }
                else -> {
                    pushed++
                    localEntry
                }
            }
            chosen.copy(syncStatus = LedgerSyncStatus.SYNCED)
        }
        return MergeResult(
            entries = merged.sortedBy { it.updatedAtMillis },
            pulledCount = pulled,
            pushedCount = pushed,
        )
    }

    /** Same Last-Write-Wins rule applied to tags. */
    fun mergeTags(local: List<LedgerTag>, remote: List<LedgerTag>): List<LedgerTag> {
        val localByUuid = local.associateBy { it.uuid }
        val remoteByUuid = remote.associateBy { it.uuid }
        return (localByUuid.keys + remoteByUuid.keys).map { uuid ->
            val localTag = localByUuid[uuid]
            val remoteTag = remoteByUuid[uuid]
            val chosen = when {
                remoteTag == null -> localTag!!
                localTag == null -> remoteTag
                remoteTag.updatedAtMillis >= localTag.updatedAtMillis -> remoteTag
                else -> localTag
            }
            chosen.copy(syncStatus = LedgerSyncStatus.SYNCED)
        }.sortedBy { it.updatedAtMillis }
    }

    /** Same Last-Write-Wins rule applied to accounts. */
    fun mergeAccounts(
        local: List<LedgerAccount>,
        remote: List<LedgerAccount>,
    ): List<LedgerAccount> {
        val localByUuid = local.associateBy { it.uuid }
        val remoteByUuid = remote.associateBy { it.uuid }
        return (localByUuid.keys + remoteByUuid.keys).map { uuid ->
            val localAccount = localByUuid[uuid]
            val remoteAccount = remoteByUuid[uuid]
            val chosen = when {
                remoteAccount == null -> localAccount!!
                localAccount == null -> remoteAccount
                remoteAccount.updatedAtMillis >= localAccount.updatedAtMillis -> remoteAccount
                else -> localAccount
            }
            chosen.copy(syncStatus = LedgerSyncStatus.SYNCED)
        }.sortedBy { it.updatedAtMillis }
    }
}

object WebDavSnapshots {
    const val KEEP = 10
    const val INDEX_FILE = "ledger-backups.json"
    val NAME_REGEX = WebDavHistory.nameRegex(BackupTool.LEDGER)

    fun snapshotName(millis: Long, zone: ZoneId): String =
        WebDavHistory.snapshotName(BackupTool.LEDGER, millis, zone)

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

    /**
     * Returns (keep, delete): the last [KEEP] names of existing + newName
     * (deduplicated, chronological) are kept; the rest must be deleted.
     */
    fun prune(existing: List<String>, newName: String): Pair<List<String>, List<String>> {
        val combined = (existing + newName).distinct().filter { NAME_REGEX.matches(it) }
        val keep = combined.takeLast(KEEP)
        return keep to (combined - keep.toSet())
    }
}

data class WebDavSyncResult(
    val totalEntries: Int,
    val pulledCount: Int,
    val pushedCount: Int,
    val snapshotName: String?,
    val snapshotFailure: WebDavFailure?,
)

/**
 * Whole-file Last-Write-Wins sync against a user-owned WebDAV folder.
 * ledger.json holds the full ledger (including tombstones); every sync also
 * writes a dated snapshot for manual recovery, keeping the newest
 * [WebDavSnapshots.KEEP].
 *
 * All methods block; call them from Dispatchers.IO.
 */
class LedgerWebDavSync(
    private val client: WebDavClient,
    private val loadLocal: () -> List<LedgerEntry>,
    private val loadLocalTags: () -> List<LedgerTag>,
    private val loadLocalAccounts: () -> List<LedgerAccount> = { emptyList() },
    private val applyMerged: (LedgerSyncPayload) -> Unit,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Reads a legacy file in the parent folder on the first Toolbox sync. */
    private val legacyClient: WebDavClient? = null,
    private val legacyClients: List<WebDavClient> = listOfNotNull(legacyClient),
    private val vault: LedgerBackupVault? = null,
) {

    fun testConnection() {
        client.ensureFolder()
        client.put(TEST_FILE, "{}")
        client.delete(TEST_FILE)
    }

    fun pull(): LedgerSyncPayload? {
        val remote = client.get(LEDGER_FILE)
        if (remote != null && WebDavClient.ifMatchValue(remote.etag) == null) {
            throw WebDavException(WebDavFailure.VERSION_UNAVAILABLE)
        }
        val file = remote ?: legacyClients.firstNotNullOfOrNull { it.get(LEDGER_FILE) } ?: return null
        return LedgerBackup.parse(vault?.decode(file.body) ?: file.body)
    }

    fun sync(nowMillis: Long = System.currentTimeMillis()): WebDavSyncResult {
        client.ensureFolder()
        var merged: MergeResult? = null
        var mergedTags: List<LedgerTag> = emptyList()
        var mergedAccounts: List<LedgerAccount> = emptyList()
        var body = ""
        var attempt = 0
        while (true) {
            attempt++
            val remote = client.get(LEDGER_FILE)
            // A missing tool file migrates from the nearest legacy folder;
            // only the new tool folder is written. Legacy files stay untouched.
            val remoteSource = remote ?: legacyClients.firstNotNullOfOrNull { it.get(LEDGER_FILE) }
            val etag = remote?.let { WebDavClient.ifMatchValue(it.etag) }
            if (remote != null && etag == null) {
                throw WebDavException(WebDavFailure.VERSION_UNAVAILABLE)
            }
            val remotePayload = remoteSource?.let { file ->
                try {
                    LedgerSyncSerializer.fromJsonString(vault?.decode(file.body) ?: file.body)
                } catch (e: Exception) {
                    throw WebDavException(WebDavFailure.INVALID_REMOTE_FILE, cause = e)
                }.also { payload ->
                    if (payload.schemaVersion > LEDGER_SCHEMA_VERSION) {
                        throw WebDavException(WebDavFailure.NEWER_SCHEMA)
                    }
                }
            }

            merged = LedgerSyncMerge.merge(
                loadLocal(),
                remotePayload?.entries.orEmpty(),
            )
            mergedTags = LedgerSyncMerge.mergeTags(
                loadLocalTags(),
                remotePayload?.tags.orEmpty(),
            )
            mergedAccounts = LedgerSyncMerge.mergeAccounts(
                loadLocalAccounts(),
                remotePayload?.accounts.orEmpty(),
            )
            body = LedgerSyncSerializer.toJsonString(
                LedgerSyncPayload(
                    clientTimestampMillis = nowMillis,
                    entries = merged.entries,
                    tags = mergedTags,
                    accounts = mergedAccounts,
                ),
            )
            body = vault?.encode(body) ?: body
            try {
                client.put(
                    LEDGER_FILE,
                    body,
                    ifMatch = etag,
                    ifNoneMatch = if (remote == null) "*" else null,
                )
                break
            } catch (e: WebDavException) {
                if (e.failure == WebDavFailure.PRECONDITION_FAILED && attempt < MAX_ATTEMPTS) {
                    continue
                }
                throw e
            }
        }

        applyMerged(
            LedgerSyncPayload(
                clientTimestampMillis = nowMillis,
                entries = merged.entries,
                tags = mergedTags,
                accounts = mergedAccounts,
            ),
        )

        var snapshotName: String? = null
        var snapshotFailure: WebDavFailure? = null
        try {
            snapshotName = WebDavHistory(client, BackupTool.LEDGER).save(body, nowMillis, zone)
        } catch (e: WebDavException) {
            snapshotFailure = e.failure
        } catch (e: Exception) {
            snapshotFailure = WebDavFailure.NETWORK
        }

        return WebDavSyncResult(
            totalEntries = merged.entries.size,
            pulledCount = merged.pulledCount,
            pushedCount = merged.pushedCount,
            snapshotName = snapshotName,
            snapshotFailure = snapshotFailure,
        )
    }

    companion object {
        const val LEDGER_FILE = "ledger.json"
        private const val TEST_FILE = ".toolbox-connection-test"
        private const val MAX_ATTEMPTS = 3
    }
}
