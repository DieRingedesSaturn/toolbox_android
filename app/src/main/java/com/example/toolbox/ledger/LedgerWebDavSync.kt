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
        val result = LastWriteWins.merge(
            local,
            remote,
            id = { it.uuid },
            updatedAt = { it.updatedAtMillis },
            finish = { it.copy(syncStatus = LedgerSyncStatus.SYNCED) },
        )
        return MergeResult(
            entries = result.items,
            pulledCount = result.pulledCount,
            pushedCount = result.pushedCount,
        )
    }

    fun mergeTags(local: List<LedgerTag>, remote: List<LedgerTag>): List<LedgerTag> =
        LastWriteWins.merge(
            local,
            remote,
            id = { it.uuid },
            updatedAt = { it.updatedAtMillis },
            finish = { it.copy(syncStatus = LedgerSyncStatus.SYNCED) },
        ).items

    fun mergeAccounts(
        local: List<LedgerAccount>,
        remote: List<LedgerAccount>,
    ): List<LedgerAccount> =
        LastWriteWins.merge(
            local,
            remote,
            id = { it.uuid },
            updatedAt = { it.updatedAtMillis },
            finish = { it.copy(syncStatus = LedgerSyncStatus.SYNCED) },
        ).items
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

    internal val engine = WholeFileWebDavSync(
        client = client,
        tool = BackupTool.LEDGER,
        fileName = LEDGER_FILE,
        legacyClients = legacyClients,
        empty = { LedgerSyncPayload(entries = emptyList()) },
        decode = { body ->
            val payload = try {
                LedgerSyncSerializer.fromJsonString(vault?.decode(body) ?: body)
            } catch (error: Exception) {
                throw WebDavException(WebDavFailure.INVALID_REMOTE_FILE, cause = error)
            }
            if (payload.schemaVersion > LEDGER_SCHEMA_VERSION) throw WebDavException(WebDavFailure.NEWER_SCHEMA)
            payload
        },
        encode = { payload, now ->
            val body = LedgerSyncSerializer.toJsonString(payload.copy(clientTimestampMillis = now))
            vault?.encode(body) ?: body
        },
        merge = { local, remote ->
            val entries = LedgerSyncMerge.merge(local.entries, remote.entries)
            FileMerge(
                LedgerSyncPayload(
                    entries = entries.entries,
                    tags = LedgerSyncMerge.mergeTags(local.tags, remote.tags),
                    accounts = LedgerSyncMerge.mergeAccounts(local.accounts, remote.accounts),
                ),
                entries.pulledCount,
                entries.pushedCount,
            )
        },
        zone = zone,
    )

    private fun localPayload() = LedgerSyncPayload(
        entries = loadLocal(), tags = loadLocalTags(), accounts = loadLocalAccounts(),
    )

    fun testConnection() = client.testConnection()

    fun pull(): LedgerSyncPayload? = engine.read().payload

    fun sync(nowMillis: Long = System.currentTimeMillis()): WebDavSyncResult =
        engine.sync(::localPayload, applyMerged, nowMillis).toLedgerResult()

    companion object {
        const val LEDGER_FILE = "ledger.json"

        suspend fun syncTargets(
            services: List<WebDavServiceProfile>,
            passphrase: String,
            loadLocal: () -> LedgerSyncPayload,
            applyMerged: (LedgerSyncPayload) -> Unit,
            nowMillis: Long = System.currentTimeMillis(),
        ): List<Result<WebDavSyncResult>> {
            val engines = services.map { service ->
                LedgerWebDavSync(
                    client = service.syncClient(BackupTool.LEDGER),
                    loadLocal = { emptyList() }, loadLocalTags = { emptyList() }, applyMerged = {},
                    legacyClients = service.legacyClients(BackupTool.LEDGER),
                    vault = LedgerBackupVault(passphrase),
                ).engine
            }
            return WholeFileWebDavSync.syncTargets(engines, loadLocal, applyMerged, nowMillis)
                .map { result -> result.map { it.toLedgerResult() } }
        }
    }
}

private fun FileSyncResult<LedgerSyncPayload>.toLedgerResult() = WebDavSyncResult(
    totalEntries = payload.entries.size,
    pulledCount = pulled,
    pushedCount = pushed,
    snapshotName = snapshotName,
    snapshotFailure = snapshotFailure,
)
