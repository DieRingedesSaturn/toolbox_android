package com.example.toolbox.ledger

enum class BackupFailure {
    INVALID_FILE,
    NEWER_SCHEMA,
    TOO_LARGE,
}

class BackupException(
    val failure: BackupFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

data class MergePreview(
    val added: Int,
    val updated: Int,
    val unchanged: Int,
)

data class ReplacePlan(
    val writes: List<LedgerEntry>,
    val tagWrites: List<LedgerTag>,
    val accountWrites: List<LedgerAccount> = emptyList(),
    val deletedCount: Int,
    val keptCount: Int,
    val deletedTagCount: Int,
    val deletedAccountCount: Int = 0,
)

/**
 * Pure backup-file logic shared by the JSON export/import flow. The file
 * format is [LedgerSyncPayload]; tombstones travel with the file so a merge
 * or replace elsewhere propagates deletions.
 */
object LedgerBackup {
    const val MAX_BYTES = 20L * 1024 * 1024

    fun parse(json: String): LedgerSyncPayload {
        val payload = try {
            LedgerSyncSerializer.fromJsonString(json.removePrefix("\uFEFF"))
        } catch (e: Exception) {
            throw BackupException(BackupFailure.INVALID_FILE, e)
        }
        if (payload.schemaVersion > LEDGER_SCHEMA_VERSION) {
            throw BackupException(BackupFailure.NEWER_SCHEMA)
        }
        return payload
    }

    /**
     * Same rule as [LedgerStore.importSyncPayload]: incoming wins when
     * incoming.updatedAtMillis >= local.updatedAtMillis.
     */
    fun previewMerge(local: List<LedgerEntry>, incoming: List<LedgerEntry>): MergePreview {
        val localByUuid = local.associateBy { it.uuid }
        var added = 0
        var updated = 0
        var unchanged = 0
        for (entry in incoming) {
            val existing = localByUuid[entry.uuid]
            when {
                existing == null -> added++
                entry.updatedAtMillis > existing.updatedAtMillis -> updated++
                else -> unchanged++
            }
        }
        return MergePreview(added = added, updated = updated, unchanged = unchanged)
    }

    /**
     * Plans a full replace that survives the next WebDAV sync. A wipe+insert
     * would lose the Last-Write-Wins race against the remote, so every row the
     * plan writes is re-stamped beyond all known revisions (at least [nowMillis]) and marked
     * [LedgerSyncStatus.PENDING_PUSH], and local-only live entries become
     * tombstones instead of being deleted outright.
     */
    fun planReplace(
        local: List<LedgerEntry>,
        incoming: List<LedgerEntry>,
        localTags: List<LedgerTag> = emptyList(),
        incomingTags: List<LedgerTag> = emptyList(),
        localAccounts: List<LedgerAccount> = emptyList(),
        incomingAccounts: List<LedgerAccount> = emptyList(),
        nowMillis: Long,
    ): ReplacePlan {
        val latest = sequenceOf(
            local.asSequence().map { it.updatedAtMillis },
            incoming.asSequence().map { it.updatedAtMillis },
            localTags.asSequence().map { it.updatedAtMillis },
            incomingTags.asSequence().map { it.updatedAtMillis },
            localAccounts.asSequence().map { it.updatedAtMillis },
            incomingAccounts.asSequence().map { it.updatedAtMillis },
        ).flatten().maxOrNull() ?: 0L
        require(latest < Long.MAX_VALUE)
        val revisionMillis = maxOf(nowMillis, latest + 1L)
        val incomingUuids = incoming.mapTo(HashSet()) { it.uuid }
        val writes = incoming.map {
            it.copy(
                updatedAtMillis = revisionMillis,
                syncStatus = LedgerSyncStatus.PENDING_PUSH,
            )
        }.toMutableList()
        var deletedCount = 0
        for (entry in local) {
            if (entry.uuid in incomingUuids || entry.deletedAtMillis != null) continue
            deletedCount++
            writes += entry.copy(
                deletedAtMillis = revisionMillis,
                updatedAtMillis = revisionMillis,
                isActiveCost = false,
                syncStatus = LedgerSyncStatus.PENDING_PUSH,
            )
        }
        val incomingTagUuids = incomingTags.mapTo(HashSet()) { it.uuid }
        // Legacy files predate tags/accounts. Keep any local row still
        // referenced by an incoming entry instead of tombstoning it and
        // leaving that entry pointing at a deleted or missing target.
        val referencedTagUuids = incoming
            .flatMapTo(HashSet()) { it.tagUuids }
        val tagWrites = incomingTags.map {
            it.copy(
                updatedAtMillis = revisionMillis,
                syncStatus = LedgerSyncStatus.PENDING_PUSH,
            )
        }.toMutableList()
        var deletedTagCount = 0
        for (tag in localTags) {
            if (tag.uuid in incomingTagUuids || tag.uuid in referencedTagUuids ||
                tag.deletedAtMillis != null
            ) {
                continue
            }
            deletedTagCount++
            tagWrites += tag.copy(
                deletedAtMillis = revisionMillis,
                updatedAtMillis = revisionMillis,
                syncStatus = LedgerSyncStatus.PENDING_PUSH,
            )
        }
        val incomingAccountUuids = incomingAccounts.mapTo(HashSet()) { it.uuid }
        val referencedAccountUuids = incoming.flatMapTo(HashSet()) { entry ->
            listOfNotNull(entry.accountUuid, entry.toAccountUuid)
        }
        val accountWrites = incomingAccounts.map {
            it.copy(
                updatedAtMillis = revisionMillis,
                syncStatus = LedgerSyncStatus.PENDING_PUSH,
            )
        }.toMutableList()
        var deletedAccountCount = 0
        for (account in localAccounts) {
            if (account.uuid in incomingAccountUuids ||
                account.uuid in referencedAccountUuids ||
                account.deletedAtMillis != null
            ) {
                continue
            }
            deletedAccountCount++
            accountWrites += account.copy(
                deletedAtMillis = revisionMillis,
                updatedAtMillis = revisionMillis,
                syncStatus = LedgerSyncStatus.PENDING_PUSH,
            )
        }
        return ReplacePlan(
            writes = writes,
            tagWrites = tagWrites,
            accountWrites = accountWrites,
            deletedCount = deletedCount,
            keptCount = incoming.count { it.deletedAtMillis == null },
            deletedTagCount = deletedTagCount,
            deletedAccountCount = deletedAccountCount,
        )
    }
}
