package com.example.toolbox.ledger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.core.database.sqlite.transaction
import com.example.toolbox.ui.*
import java.io.File
import org.json.JSONArray

import com.example.toolbox.ledger.LedgerDb.Companion.TABLE_ENTRIES
import com.example.toolbox.ledger.LedgerDb.Companion.TABLE_TAGS
import com.example.toolbox.ledger.LedgerDb.Companion.TABLE_ACCOUNTS

class LedgerUpgradeBackupException(cause: Exception) :
    IllegalStateException("Cannot save the pre-upgrade ledger copy", cause)

class LedgerStore private constructor(
    context: Context,
    databaseName: String,
    snapshotBeforeUpgrade: Boolean,
) : LedgerDb(context, databaseName, snapshotBeforeUpgrade) {
    constructor(context: Context) : this(context, DATABASE_NAME, true)

    fun upsert(entry: LedgerEntry) {
        val now = System.currentTimeMillis()
        val updated = entry.copy(
            updatedAtMillis = now,
            syncStatus = LedgerSyncStatus.PENDING_PUSH,
        )
        val rowId = writableDatabase.insertWithOnConflict(
            TABLE_ENTRIES,
            null,
            toContentValues(updated),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        check(rowId != -1L) { "Ledger entry write failed" }
        notifyChanged()
    }


    fun softDelete(uuid: String) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("deleted_at", now)
            put("updated_at", now)
            put("is_active_cost", 0)
            put("sync_status", LedgerSyncStatus.PENDING_PUSH.name)
        }
        writableDatabase.update(
            TABLE_ENTRIES,
            values,
            "uuid = ?",
            arrayOf(uuid),
        )
        notifyChanged()
    }


    fun toggleActiveStatus(entry: LedgerEntry, isActive: Boolean) {
        val now = System.currentTimeMillis()
        val updated = entry.copy(
            isActiveCost = isActive,
            retiredAtMillis = if (!isActive) now else null,
            updatedAtMillis = now,
            syncStatus = LedgerSyncStatus.PENDING_PUSH,
        )
        upsert(updated)
    }


    fun queryVisibleEntries(): List<LedgerEntry> {
        val result = mutableListOf<LedgerEntry>()
        readableDatabase.query(
            TABLE_ENTRIES,
            null,
            "deleted_at IS NULL",
            null,
            null,
            null,
            "occurred_at DESC, created_at DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor))
            }
        }
        return result
    }


    fun queryAllEntriesForSync(): List<LedgerEntry> {
        val result = mutableListOf<LedgerEntry>()
        readableDatabase.query(
            TABLE_ENTRIES,
            null,
            null,
            null,
            null,
            null,
            "updated_at ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor))
            }
        }
        return result
    }


    fun importSyncPayload(payload: LedgerSyncPayload): Int {
        var mergedCount = 0
        val db = writableDatabase
        db.transaction {
            // Files from older versions carry no tags/accounts: entries get
            // backfill uuids that point at the defaults — seed only the
            // default tags actually referenced and missing from the file.
            seedAccounts(db)
            val referencedDefaults = payload.entries
                .flatMapTo(HashSet()) { it.tagUuids }
                .filterTo(HashSet()) { uuid ->
                    payload.tags.none { it.uuid == uuid }
                }
            seedCategoryTags(db, referencedDefaults)
            for (incoming in payload.accounts) {
                val existing = queryAccountUpdatedAt(db, incoming.uuid)
                if (existing == null || incoming.updatedAtMillis >= existing) {
                    db.insertWithOnConflict(
                        TABLE_ACCOUNTS,
                        null,
                        toAccountValues(incoming),
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
            for (incoming in payload.tags) {
                val existing = queryTagUpdatedAt(db, incoming.uuid)
                if (existing == null || incoming.updatedAtMillis >= existing) {
                    db.insertWithOnConflict(
                        TABLE_TAGS,
                        null,
                        toTagValues(incoming),
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
            for (incoming in payload.entries) {
                val existingUpdated = queryUpdatedAt(db, incoming.uuid)
                if (existingUpdated == null || incoming.updatedAtMillis >= existingUpdated) {
                    db.insertWithOnConflict(
                        TABLE_ENTRIES,
                        null,
                        toContentValues(incoming),
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                    mergedCount++
                }
            }
        }
        notifyChanged()
        return mergedCount
    }

    /**
     * Writes a replace plan verbatim in one transaction. Rows go in via
     * CONFLICT_REPLACE without re-stamping updatedAt — they carry the
     * timestamp and PENDING_PUSH status assigned by [LedgerBackup.planReplace].
     */

    fun applyReplace(plan: ReplacePlan) {
        val db = writableDatabase
        db.transaction {
            for (account in plan.accountWrites) {
                db.insertWithOnConflict(
                    TABLE_ACCOUNTS,
                    null,
                    toAccountValues(account),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            for (tag in plan.tagWrites) {
                db.insertWithOnConflict(
                    TABLE_TAGS,
                    null,
                    toTagValues(tag),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            for (entry in plan.writes) {
                db.insertWithOnConflict(
                    TABLE_ENTRIES,
                    null,
                    toContentValues(entry),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
        notifyChanged()
    }

    /**
     * Inserts renewal/skip-marker rows verbatim in one transaction.
     * CONFLICT_IGNORE leaves an existing row (live or tombstone) untouched so
     * re-running never duplicates.
     */

    fun insertIfAbsent(entries: List<LedgerEntry>) {
        if (entries.isEmpty()) return
        val db = writableDatabase
        db.transaction {
            for (entry in entries) {
                db.insertWithOnConflict(
                    TABLE_ENTRIES,
                    null,
                    toContentValues(entry),
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
        }
        notifyChanged()
    }

    /**
     * Writes the disposal outcome in one transaction: the updated parent asset
     * (or subscription) plus the sale child row when [saleEntry] is not null —
     * a live income row for SOLD, or its tombstone when undoing. Written
     * verbatim via CONFLICT_REPLACE, so a previous tombstoned sale revives.
     */

    fun applyDisposal(updatedAsset: LedgerEntry, saleEntry: LedgerEntry?) {
        val db = writableDatabase
        db.transaction {
            db.insertWithOnConflict(
                TABLE_ENTRIES,
                null,
                toContentValues(updatedAsset),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            saleEntry?.let {
                db.insertWithOnConflict(
                    TABLE_ENTRIES,
                    null,
                    toContentValues(it),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
        notifyChanged()
    }

    /** Validate and migrate an isolated copy, then replace rows atomically. */

    fun restoreSnapshot(snapshot: File, nowMillis: Long = System.currentTimeMillis()) {
        val payload = LedgerDbSnapshots.withStagedCopy(snapshot, appContext.cacheDir) { staged ->
            validateSnapshot(staged)
            LedgerStore(appContext, staged.path, false).use { source ->
                val db = source.readableDatabase
                check(db.isDatabaseIntegrityOk) { "Invalid ledger snapshot" }
                LedgerSyncPayload(
                    entries = source.queryAllEntriesForSync(),
                    tags = source.queryTags(includeDeleted = true),
                    accounts = source.queryAccounts(includeDeleted = true),
                )
            }
        }
        val dir = File(appContext.noBackupFilesDir, LedgerDbSnapshots.DIR_NAME)
        val db = writableDatabase
        db.transaction {
            // Capture all rows under the same write lock as the replacement.
            // A closed, standalone database avoids copying an active WAL pair.
            val current = LedgerSyncPayload(
                entries = queryAllEntriesForSync(),
                tags = queryTags(includeDeleted = true),
                accounts = queryAccounts(includeDeleted = true),
            )
            LedgerDbSnapshots.withTemporaryDatabase(appContext.cacheDir) { backup ->
                LedgerStore(appContext, backup.path, false).use { copy ->
                    copy.writableDatabase.transaction {
                        copy.replaceSnapshotRows(copy.writableDatabase, current)
                    }
                }
                LedgerDbSnapshots.save(
                    dbFile = backup,
                    dir = dir,
                    fromVersion = DATABASE_VERSION,
                    nowMillis = nowMillis,
                    pruneAfterSave = false,
                )
            }
            replaceSnapshotRows(db, payload)
        }
        // Only a committed restore may retire the selected recovery source.
        runCatching { LedgerDbSnapshots.prune(dir, LedgerDbSnapshots.KEEP) }
        notifyChanged()
    }


    private fun validateSnapshot(file: File) {
        // A non-deleting handler prevents SQLite's default corruption recovery
        // from silently turning a broken snapshot into a new, empty ledger.
        SQLiteDatabase.openDatabase(
            file.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
            { throw SQLiteException("Invalid ledger snapshot") },
        ).use { db ->
            check(db.version in 1..DATABASE_VERSION) { "Unsupported ledger schema" }
            check(db.isDatabaseIntegrityOk) { "Invalid ledger snapshot" }
            db.rawQuery("SELECT uuid FROM ledger_entries LIMIT 0", null).use { }
        }
    }


    private fun replaceSnapshotRows(db: SQLiteDatabase, payload: LedgerSyncPayload) {
        db.delete(TABLE_ENTRIES, null, null)
        db.delete(TABLE_TAGS, null, null)
        db.delete(TABLE_ACCOUNTS, null, null)
        payload.accounts.forEach { db.insertOrThrow(TABLE_ACCOUNTS, null, toAccountValues(it)) }
        payload.tags.forEach { db.insertOrThrow(TABLE_TAGS, null, toTagValues(it)) }
        payload.entries.forEach { db.insertOrThrow(TABLE_ENTRIES, null, toContentValues(it)) }
    }


    private fun notifyChanged() {
        runCatching { LedgerWidget.refreshAll(appContext) }
    }


    private fun queryTagUpdatedAt(db: SQLiteDatabase, uuid: String): Long? =
        db.query(
            TABLE_TAGS,
            arrayOf("updated_at"),
            "uuid = ?",
            arrayOf(uuid),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }


    private fun queryUpdatedAt(db: SQLiteDatabase, uuid: String): Long? {
        db.query(
            TABLE_ENTRIES,
            arrayOf("updated_at"),
            "uuid = ?",
            arrayOf(uuid),
            null,
            null,
            null,
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }


    fun queryTags(includeDeleted: Boolean = false): List<LedgerTag> =
        queryTags(readableDatabase, includeDeleted)


    private fun queryLiveEntriesFor(db: SQLiteDatabase): List<LedgerEntry> {
        val result = mutableListOf<LedgerEntry>()
        db.query(
            TABLE_ENTRIES,
            null,
            "deleted_at IS NULL",
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor))
            }
        }
        return result
    }


    fun upsertTag(tag: LedgerTag) {
        val updated = tag.copy(
            updatedAtMillis = System.currentTimeMillis(),
            syncStatus = LedgerSyncStatus.PENDING_PUSH,
        )
        writableDatabase.insertWithOnConflict(
            TABLE_TAGS,
            null,
            toTagValues(updated),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        notifyChanged()
    }


    fun softDeleteTag(uuid: String) {
        val now = System.currentTimeMillis()
        writableDatabase.update(
            TABLE_TAGS,
            ContentValues().apply {
                put("deleted_at", now)
                put("updated_at", now)
                put("sync_status", LedgerSyncStatus.PENDING_PUSH.name)
            },
            "uuid = ?",
            arrayOf(uuid),
        )
        notifyChanged()
    }

    /**
     * Writes sort_order = index for [orderedUuids] in one transaction;
     * only rows whose order actually changed get a new updatedAt stamp.
     */

    fun reorderTags(orderedUuids: List<String>) {
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.transaction {
            val current = queryTags(db, includeDeleted = false)
                .associateBy { it.uuid }
            orderedUuids.forEachIndexed { index, uuid ->
                val tag = current[uuid] ?: return@forEachIndexed
                if (tag.sortOrder == index) return@forEachIndexed
                db.update(
                    TABLE_TAGS,
                    ContentValues().apply {
                        put("sort_order", index)
                        put("updated_at", now)
                        put("sync_status", LedgerSyncStatus.PENDING_PUSH.name)
                    },
                    "uuid = ?",
                    arrayOf(uuid),
                )
            }
        }
        notifyChanged()
    }

    /**
     * Merge tag [fromUuid] into [toUuid]: live entries carrying A get B
     * instead (deduped), then A is tombstoned — all in one transaction.
     */

    fun mergeTag(fromUuid: String, toUuid: String) {
        if (fromUuid == toUuid) return
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.transaction {
            for (entry in queryLiveEntriesFor(db)) {
                if (fromUuid !in entry.tagUuids) continue
                val mapped = LedgerTags.replaceTag(entry.tagUuids, fromUuid, toUuid)
                if (mapped == entry.tagUuids) continue
                db.update(
                    TABLE_ENTRIES,
                    ContentValues().apply {
                        put("tag_uuids", JSONArray().apply {
                            mapped.forEach { put(it) }
                        }.toString())
                        put("updated_at", now)
                        put("sync_status", LedgerSyncStatus.PENDING_PUSH.name)
                    },
                    "uuid = ?",
                    arrayOf(entry.uuid),
                )
            }
            db.update(
                TABLE_TAGS,
                ContentValues().apply {
                    put("deleted_at", now)
                    put("updated_at", now)
                    put("sync_status", LedgerSyncStatus.PENDING_PUSH.name)
                },
                "uuid = ?",
                arrayOf(fromUuid),
            )
        }
        notifyChanged()
    }

    /**
     * Adds [add] and/or removes [remove] on the given entries in one
     * transaction; only entries whose tag list actually changes are written.
     */

    fun updateEntryTags(
        entryUuids: Collection<String>,
        add: String?,
        remove: String?,
    ) {
        if (entryUuids.isEmpty() || (add == null && remove == null)) return
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.transaction {
            for (entry in queryLiveEntriesFor(db)) {
                if (entry.uuid !in entryUuids) continue
                var next = entry.tagUuids
                if (remove != null) next = LedgerTags.removeTag(next, remove)
                if (add != null) next = LedgerTags.addTag(next, add)
                if (next == entry.tagUuids) continue
                db.update(
                    TABLE_ENTRIES,
                    ContentValues().apply {
                        put("tag_uuids", JSONArray().apply {
                            next.forEach { put(it) }
                        }.toString())
                        put("updated_at", now)
                        put("sync_status", LedgerSyncStatus.PENDING_PUSH.name)
                    },
                    "uuid = ?",
                    arrayOf(entry.uuid),
                )
            }
        }
        notifyChanged()
    }


    private fun queryAccountUpdatedAt(db: SQLiteDatabase, uuid: String): Long? =
        db.query(
            TABLE_ACCOUNTS,
            arrayOf("updated_at"),
            "uuid = ?",
            arrayOf(uuid),
            null,
            null,
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }


    fun queryAccounts(includeDeleted: Boolean = false): List<LedgerAccount> {
        val result = mutableListOf<LedgerAccount>()
        readableDatabase.query(
            TABLE_ACCOUNTS,
            null,
            if (includeDeleted) null else "deleted_at IS NULL",
            null,
            null,
            null,
            "sort_order ASC, name ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(accountFromCursor(cursor))
            }
        }
        return result
    }


    fun upsertAccount(account: LedgerAccount) {
        val updated = account.copy(
            updatedAtMillis = System.currentTimeMillis(),
            syncStatus = LedgerSyncStatus.PENDING_PUSH,
        )
        writableDatabase.insertWithOnConflict(
            TABLE_ACCOUNTS,
            null,
            toAccountValues(updated),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        notifyChanged()
    }


    fun softDeleteAccount(uuid: String) {
        val now = System.currentTimeMillis()
        writableDatabase.update(
            TABLE_ACCOUNTS,
            ContentValues().apply {
                put("deleted_at", now)
                put("updated_at", now)
                put("sync_status", LedgerSyncStatus.PENDING_PUSH.name)
            },
            "uuid = ?",
            arrayOf(uuid),
        )
        notifyChanged()
    }

    companion object {
        internal const val DATABASE_NAME = LedgerDb.DATABASE_NAME
    }
}
