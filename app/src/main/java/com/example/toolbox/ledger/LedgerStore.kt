package com.example.toolbox.ledger

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import com.example.toolbox.ui.AppPreferences
import com.example.toolbox.ui.ToolboxStrings
import java.io.File
import org.json.JSONArray

class LedgerStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    private val appContext = context.applicationContext

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        val fromVersion = db.version
        if (fromVersion in 1 until DATABASE_VERSION) {
            runCatching {
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                LedgerDbSnapshots.save(
                    dbFile = File(db.path),
                    dir = File(appContext.noBackupFilesDir, LedgerDbSnapshots.DIR_NAME),
                    fromVersion = fromVersion,
                    nowMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ENTRIES (
                uuid TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                amount_cents INTEGER NOT NULL,
                currency TEXT NOT NULL,
                fx_rate_to_cny REAL NOT NULL DEFAULT 1.0,
                fx_rate_date TEXT,
                fx_rate_source TEXT,
                parent_uuid TEXT,
                link_type TEXT,
                renewal_index INTEGER,
                disposal_type TEXT,
                tag_uuids TEXT NOT NULL DEFAULT '[]',
                type TEXT NOT NULL,
                category TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                note TEXT NOT NULL,
                cost_tracking_mode TEXT NOT NULL,
                salvage_value_cents INTEGER NOT NULL,
                target_days INTEGER,
                retired_at INTEGER,
                cost_ends_at INTEGER,
                billing_cycle TEXT NOT NULL,
                custom_cycle_days INTEGER NOT NULL,
                custom_cycle_unit TEXT NOT NULL DEFAULT 'DAYS',
                is_active_cost INTEGER NOT NULL,
                account_uuid TEXT,
                account_amount_cents INTEGER,
                to_account_uuid TEXT,
                to_amount_cents INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                sync_status TEXT NOT NULL,
                server_revision INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ledger_occurred ON $TABLE_ENTRIES(occurred_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ledger_sync ON $TABLE_ENTRIES(sync_status, updated_at)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TAGS (
                uuid TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                color INTEGER NOT NULL,
                emoji TEXT NOT NULL,
                sort_order INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                sync_status TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ACCOUNTS (
                uuid TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                currency TEXT NOT NULL,
                opening_balance_cents INTEGER NOT NULL,
                opening_at INTEGER NOT NULL,
                color INTEGER NOT NULL,
                emoji TEXT NOT NULL,
                sort_order INTEGER NOT NULL,
                is_archived INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                sync_status TEXT NOT NULL
            )
            """.trimIndent(),
        )
        seedAccounts(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE $TABLE_ENTRIES " +
                    "ADD COLUMN fx_rate_to_cny REAL NOT NULL DEFAULT 1.0",
            )
            db.execSQL(
                "ALTER TABLE $TABLE_ENTRIES ADD COLUMN fx_rate_date TEXT",
            )
        }
        if (oldVersion < 3) {
            db.execSQL(
                "ALTER TABLE $TABLE_ENTRIES ADD COLUMN parent_uuid TEXT",
            )
            db.execSQL(
                "ALTER TABLE $TABLE_ENTRIES ADD COLUMN link_type TEXT",
            )
            db.execSQL(
                "ALTER TABLE $TABLE_ENTRIES ADD COLUMN renewal_index INTEGER",
            )
            db.execSQL(
                "ALTER TABLE $TABLE_ENTRIES ADD COLUMN fx_rate_source TEXT",
            )
            db.execSQL(
                "ALTER TABLE $TABLE_ENTRIES ADD COLUMN disposal_type TEXT",
            )
            db.execSQL(
                "ALTER TABLE $TABLE_ENTRIES ADD COLUMN custom_cycle_unit TEXT NOT NULL DEFAULT 'DAYS'",
            )
            db.execSQL(
                "ALTER TABLE $TABLE_ENTRIES " +
                    "ADD COLUMN tag_uuids TEXT NOT NULL DEFAULT '[]'",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_TAGS (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    color INTEGER NOT NULL,
                    emoji TEXT NOT NULL,
                    sort_order INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    deleted_at INTEGER,
                    sync_status TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_ACCOUNTS (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    currency TEXT NOT NULL,
                    opening_balance_cents INTEGER NOT NULL,
                    opening_at INTEGER NOT NULL,
                    color INTEGER NOT NULL,
                    emoji TEXT NOT NULL,
                    sort_order INTEGER NOT NULL,
                    is_archived INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    deleted_at INTEGER,
                    sync_status TEXT NOT NULL
                )
                """.trimIndent(),
            )
            listOf(
                "ALTER TABLE $TABLE_ENTRIES ADD COLUMN account_uuid TEXT",
                "ALTER TABLE $TABLE_ENTRIES ADD COLUMN account_amount_cents INTEGER",
                "ALTER TABLE $TABLE_ENTRIES ADD COLUMN to_account_uuid TEXT",
                "ALTER TABLE $TABLE_ENTRIES ADD COLUMN to_amount_cents INTEGER",
            ).forEach { db.execSQL(it) }
            val producedUuids = backfillEntryTags(db)
            // Only the category tags the backfill actually referenced are
            // seeded — the rest were retired in v4.
            seedCategoryTags(db, producedUuids)
            seedAccounts(db)
            backfillEntryAccounts(db)
        }
        if (oldVersion < 4) {
            // v4: unused untouched default tags are tombstoned; new installs
            // start with zero tags. Only defaults never renamed/edited
            // (still stamped updated_at = 0) and unreferenced by any live
            // entry are retired — tombstones are left alone.
            val strings = ToolboxStrings(AppPreferences(appContext).language())
            val defaultUuids = LedgerTags.defaultTags(
                LedgerCategory.entries.associateWith { strings.ledgerCategory(it) },
            ).mapTo(HashSet()) { it.uuid }
            val tags = queryTags(db, includeDeleted = false)
            val unused = tags.filter { it.uuid in defaultUuids }.let { defaults ->
                LedgerTags.unusedDefaultTagUuids(defaults, liveEntryTagUuids(db))
            }
            if (unused.isNotEmpty()) {
                val now = System.currentTimeMillis()
                for (uuid in unused) {
                    db.update(
                        TABLE_TAGS,
                        ContentValues().apply {
                            put("deleted_at", now)
                            put("updated_at", now)
                            put("sync_status", LedgerSyncStatus.PENDING_PUSH.name)
                        },
                        "uuid = ?",
                        arrayOf(uuid),
                    )
                }
            }
        }
        if (oldVersion < 5) {
            db.execSQL(
                "ALTER TABLE $TABLE_ENTRIES ADD COLUMN cost_ends_at INTEGER",
            )
        }
    }

    /** Seeds the shared default account (localized name, epoch-0 LWW). */
    private fun seedAccounts(db: SQLiteDatabase) {
        val strings = ToolboxStrings(AppPreferences(appContext).language())
        db.insertWithOnConflict(
            TABLE_ACCOUNTS,
            null,
            toAccountValues(LedgerAccounts.defaultAccount(strings.defaultAccountName)),
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    /** Legacy entries post to the default account. */
    private fun backfillEntryAccounts(db: SQLiteDatabase) {
        db.execSQL(
            "UPDATE $TABLE_ENTRIES SET account_uuid = ? WHERE account_uuid IS NULL",
            arrayOf(LedgerAccounts.DEFAULT_ACCOUNT_UUID),
        )
    }

    /**
     * Inserts only the category-derived default tags whose uuid is in
     * [uuids] (names localized for the current app language).
     * CONFLICT_IGNORE never revives a tombstoned tag.
     */
    private fun seedCategoryTags(db: SQLiteDatabase, uuids: Set<String>) {
        if (uuids.isEmpty()) return
        val strings = ToolboxStrings(AppPreferences(appContext).language())
        val names = LedgerCategory.entries.associateWith { strings.ledgerCategory(it) }
        for (tag in LedgerTags.defaultTags(names)) {
            if (tag.uuid !in uuids) continue
            db.insertWithOnConflict(
                TABLE_TAGS,
                null,
                toTagValues(tag),
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
    }

    /**
     * Gives every entry one tag uuid derived from its legacy category.
     * Returns the set of default-tag uuids it wrote.
     */
    private fun backfillEntryTags(db: SQLiteDatabase): Set<String> {
        val produced = HashSet<String>()
        db.query(
            TABLE_ENTRIES,
            arrayOf("uuid", "category"),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(0)
                val category = LedgerCategory.fromStorage(cursor.getString(1))
                val tagUuid = LedgerTags.categoryTagUuid(category)
                produced += tagUuid
                val tagUuids = JSONArray()
                    .put(tagUuid)
                    .toString()
                db.update(
                    TABLE_ENTRIES,
                    ContentValues().apply { put("tag_uuids", tagUuids) },
                    "uuid = ? AND (tag_uuids IS NULL OR tag_uuids = '[]')",
                    arrayOf(uuid),
                )
            }
        }
        return produced
    }

    fun upsert(entry: LedgerEntry) {
        val now = System.currentTimeMillis()
        val updated = entry.copy(
            updatedAtMillis = now,
            syncStatus = LedgerSyncStatus.PENDING_PUSH,
        )
        writableDatabase.insertWithOnConflict(
            TABLE_ENTRIES,
            null,
            toContentValues(updated),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
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

    private fun toContentValues(entry: LedgerEntry): ContentValues = ContentValues().apply {
        put("uuid", entry.uuid)
        put("title", entry.title)
        put("amount_cents", entry.amountCents)
        put("currency", entry.currency)
        put("fx_rate_to_cny", entry.fxRateToCny)
        if (entry.fxRateDate != null) {
            put("fx_rate_date", entry.fxRateDate)
        } else {
            putNull("fx_rate_date")
        }
        put("fx_rate_source", entry.fxRateSource)
        put("parent_uuid", entry.parentUuid)
        put("link_type", entry.linkType)
        if (entry.renewalIndex != null) {
            put("renewal_index", entry.renewalIndex)
        } else {
            putNull("renewal_index")
        }
        put("disposal_type", entry.disposalType)
        put(
            "tag_uuids",
            JSONArray().apply { entry.tagUuids.forEach { put(it) } }.toString(),
        )
        put("type", entry.type.name)
        put("category", entry.category)
        put("occurred_at", entry.occurredAtMillis)
        put("note", entry.note)
        put("cost_tracking_mode", entry.costTrackingMode.name)
        put("salvage_value_cents", entry.salvageValueCents)
        if (entry.targetDays != null) put("target_days", entry.targetDays) else putNull("target_days")
        if (entry.retiredAtMillis != null) put("retired_at", entry.retiredAtMillis) else putNull("retired_at")
        if (entry.costEndsAtMillis != null) {
            put("cost_ends_at", entry.costEndsAtMillis)
        } else {
            putNull("cost_ends_at")
        }
        put("billing_cycle", entry.billingCycle.name)
        put("custom_cycle_days", entry.customCycleDays)
        put("custom_cycle_unit", entry.customCycleUnit.name)
        put("is_active_cost", if (entry.isActiveCost) 1 else 0)
        put("account_uuid", entry.accountUuid)
        put("account_amount_cents", entry.accountAmountCents)
        put("to_account_uuid", entry.toAccountUuid)
        put("to_amount_cents", entry.toAmountCents)
        put("created_at", entry.createdAtMillis)
        put("updated_at", entry.updatedAtMillis)
        if (entry.deletedAtMillis != null) put("deleted_at", entry.deletedAtMillis) else putNull("deleted_at")
        put("sync_status", entry.syncStatus.name)
        put("server_revision", entry.serverRevision)
    }

    private fun parseTagUuids(raw: String?): List<String> =
        runCatching {
            val array = JSONArray(raw ?: "[]")
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())

    private fun toTagValues(tag: LedgerTag): ContentValues = ContentValues().apply {
        put("uuid", tag.uuid)
        put("name", tag.name)
        put("color", tag.colorArgb)
        put("emoji", tag.emoji)
        put("sort_order", tag.sortOrder)
        put("created_at", tag.createdAtMillis)
        put("updated_at", tag.updatedAtMillis)
        if (tag.deletedAtMillis != null) {
            put("deleted_at", tag.deletedAtMillis)
        } else {
            putNull("deleted_at")
        }
        put("sync_status", tag.syncStatus.name)
    }

    private fun tagFromCursor(cursor: Cursor): LedgerTag {
        val deletedIdx = cursor.getColumnIndexOrThrow("deleted_at")
        return LedgerTag(
            uuid = cursor.getString(cursor.getColumnIndexOrThrow("uuid")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            colorArgb = cursor.getInt(cursor.getColumnIndexOrThrow("color")),
            emoji = cursor.getString(cursor.getColumnIndexOrThrow("emoji")),
            sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sort_order")),
            createdAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
            deletedAtMillis = if (cursor.isNull(deletedIdx)) {
                null
            } else {
                cursor.getLong(deletedIdx)
            },
            syncStatus = runCatching {
                LedgerSyncStatus.valueOf(
                    cursor.getString(cursor.getColumnIndexOrThrow("sync_status")),
                )
            }.getOrDefault(LedgerSyncStatus.PENDING_PUSH),
        )
    }

    fun queryTags(includeDeleted: Boolean = false): List<LedgerTag> =
        queryTags(readableDatabase, includeDeleted)

    private fun queryTags(db: SQLiteDatabase, includeDeleted: Boolean): List<LedgerTag> {
        val result = mutableListOf<LedgerTag>()
        db.query(
            TABLE_TAGS,
            null,
            if (includeDeleted) null else "deleted_at IS NULL",
            null,
            null,
            null,
            "sort_order ASC, name ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(tagFromCursor(cursor))
            }
        }
        return result
    }

    private fun liveEntryTagUuids(db: SQLiteDatabase): Set<String> {
        val used = HashSet<String>()
        db.query(
            TABLE_ENTRIES,
            arrayOf("tag_uuids"),
            "deleted_at IS NULL",
            null,
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                used += parseTagUuids(cursor.getString(0))
            }
        }
        return used
    }

    /** All live entries — used by the tag merge paths. */
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

    private fun toAccountValues(account: LedgerAccount): ContentValues =
        ContentValues().apply {
            put("uuid", account.uuid)
            put("name", account.name)
            put("currency", account.currency)
            put("opening_balance_cents", account.openingBalanceCents)
            put("opening_at", account.openingAtMillis)
            put("color", account.colorArgb)
            put("emoji", account.emoji)
            put("sort_order", account.sortOrder)
            put("is_archived", if (account.isArchived) 1 else 0)
            put("created_at", account.createdAtMillis)
            put("updated_at", account.updatedAtMillis)
            if (account.deletedAtMillis != null) {
                put("deleted_at", account.deletedAtMillis)
            } else {
                putNull("deleted_at")
            }
            put("sync_status", account.syncStatus.name)
        }

    private fun accountFromCursor(cursor: Cursor): LedgerAccount {
        val deletedIdx = cursor.getColumnIndexOrThrow("deleted_at")
        return LedgerAccount(
            uuid = cursor.getString(cursor.getColumnIndexOrThrow("uuid")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            currency = cursor.getString(cursor.getColumnIndexOrThrow("currency")),
            openingBalanceCents = cursor.getLong(
                cursor.getColumnIndexOrThrow("opening_balance_cents"),
            ),
            openingAtMillis = cursor.getLong(
                cursor.getColumnIndexOrThrow("opening_at"),
            ),
            colorArgb = cursor.getInt(cursor.getColumnIndexOrThrow("color")),
            emoji = cursor.getString(cursor.getColumnIndexOrThrow("emoji")),
            sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sort_order")),
            isArchived = cursor.getInt(
                cursor.getColumnIndexOrThrow("is_archived"),
            ) != 0,
            createdAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
            deletedAtMillis = if (cursor.isNull(deletedIdx)) {
                null
            } else {
                cursor.getLong(deletedIdx)
            },
            syncStatus = runCatching {
                LedgerSyncStatus.valueOf(
                    cursor.getString(cursor.getColumnIndexOrThrow("sync_status")),
                )
            }.getOrDefault(LedgerSyncStatus.PENDING_PUSH),
        )
    }

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

    private fun nullableString(cursor: Cursor, column: String): String? {
        val idx = cursor.getColumnIndexOrThrow(column)
        return if (cursor.isNull(idx)) null else cursor.getString(idx)
    }

    private fun nullableLong(cursor: Cursor, column: String): Long? {
        val idx = cursor.getColumnIndexOrThrow(column)
        return if (cursor.isNull(idx)) null else cursor.getLong(idx)
    }

    private fun fromCursor(cursor: Cursor): LedgerEntry {
        val targetDaysIdx = cursor.getColumnIndexOrThrow("target_days")
        val fxRateDateIdx = cursor.getColumnIndexOrThrow("fx_rate_date")
        val renewalIndexIdx = cursor.getColumnIndexOrThrow("renewal_index")
        val retiredAtIdx = cursor.getColumnIndexOrThrow("retired_at")
        val deletedAtIdx = cursor.getColumnIndexOrThrow("deleted_at")

        return LedgerEntry(
            uuid = cursor.getString(cursor.getColumnIndexOrThrow("uuid")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            amountCents = cursor.getLong(cursor.getColumnIndexOrThrow("amount_cents")),
            currency = cursor.getString(cursor.getColumnIndexOrThrow("currency")),
            fxRateToCny = cursor.getDouble(cursor.getColumnIndexOrThrow("fx_rate_to_cny")),
            fxRateDate = if (cursor.isNull(fxRateDateIdx)) {
                null
            } else {
                cursor.getString(fxRateDateIdx)
            },
            fxRateSource = nullableString(cursor, "fx_rate_source"),
            parentUuid = nullableString(cursor, "parent_uuid"),
            linkType = nullableString(cursor, "link_type"),
            renewalIndex = if (cursor.isNull(renewalIndexIdx)) {
                null
            } else {
                cursor.getInt(renewalIndexIdx)
            },
            disposalType = nullableString(cursor, "disposal_type"),
            tagUuids = parseTagUuids(
                cursor.getString(cursor.getColumnIndexOrThrow("tag_uuids")),
            ),
            type = runCatching {
                LedgerEntryType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("type")))
            }.getOrDefault(LedgerEntryType.EXPENSE),
            category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
            occurredAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("occurred_at")),
            note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
            costTrackingMode = runCatching {
                CostTrackingMode.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("cost_tracking_mode")))
            }.getOrDefault(CostTrackingMode.NONE),
            salvageValueCents = cursor.getLong(cursor.getColumnIndexOrThrow("salvage_value_cents")),
            targetDays = if (cursor.isNull(targetDaysIdx)) null else cursor.getInt(targetDaysIdx),
            retiredAtMillis = if (cursor.isNull(retiredAtIdx)) null else cursor.getLong(retiredAtIdx),
            costEndsAtMillis = nullableLong(cursor, "cost_ends_at"),
            billingCycle = runCatching {
                BillingCycle.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("billing_cycle")))
            }.getOrDefault(BillingCycle.MONTHLY),
            customCycleDays = cursor.getInt(cursor.getColumnIndexOrThrow("custom_cycle_days")),
            customCycleUnit = cursor.getString(
                cursor.getColumnIndexOrThrow("custom_cycle_unit"),
            )?.let { runCatching { CycleUnit.valueOf(it) }.getOrNull() }
                ?: CycleUnit.DAYS,
            isActiveCost = cursor.getInt(cursor.getColumnIndexOrThrow("is_active_cost")) != 0,
            accountUuid = nullableString(cursor, "account_uuid"),
            accountAmountCents = nullableLong(cursor, "account_amount_cents"),
            toAccountUuid = nullableString(cursor, "to_account_uuid"),
            toAmountCents = nullableLong(cursor, "to_amount_cents"),
            createdAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
            deletedAtMillis = if (cursor.isNull(deletedAtIdx)) null else cursor.getLong(deletedAtIdx),
            syncStatus = runCatching {
                LedgerSyncStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("sync_status")))
            }.getOrDefault(LedgerSyncStatus.PENDING_PUSH),
            serverRevision = cursor.getLong(cursor.getColumnIndexOrThrow("server_revision")),
        )
    }

    companion object {
        private const val DATABASE_NAME = "toolbox_ledger.db"
        private const val DATABASE_VERSION = 5
        private const val TABLE_ENTRIES = "ledger_entries"
        private const val TABLE_TAGS = "ledger_tags"
        private const val TABLE_ACCOUNTS = "ledger_accounts"
    }
}
