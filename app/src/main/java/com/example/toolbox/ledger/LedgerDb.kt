package com.example.toolbox.ledger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.example.toolbox.ui.AppPreferences
import com.example.toolbox.ui.*
import java.io.File
import org.json.JSONArray

abstract class LedgerDb internal constructor(
    context: Context,
    databaseName: String,
    private val snapshotBeforeUpgrade: Boolean,
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, DATABASE_VERSION,
    { throw SQLiteException("Ledger database is corrupt") }) {
    protected val appContext = context.applicationContext

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        val fromVersion = db.version
        if (snapshotBeforeUpgrade && fromVersion in 1 until DATABASE_VERSION) {
            try {
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use {
                    check(it.moveToFirst() && it.getInt(0) == 0) { "Ledger checkpoint is busy" }
                }
                LedgerDbSnapshots.save(
                    dbFile = File(db.path),
                    dir = File(appContext.noBackupFilesDir, LedgerDbSnapshots.DIR_NAME),
                    fromVersion = fromVersion,
                    nowMillis = System.currentTimeMillis(),
                )
            } catch (failure: Exception) {
                // Stop before onUpgrade; the old database remains untouched.
                throw LedgerUpgradeBackupException(failure)
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
    protected fun seedAccounts(db: SQLiteDatabase) {
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
    protected fun seedCategoryTags(db: SQLiteDatabase, uuids: Set<String>) {
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

    protected fun queryTags(db: SQLiteDatabase, includeDeleted: Boolean): List<LedgerTag> {
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



    protected fun liveEntryTagUuids(db: SQLiteDatabase): Set<String> {
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

    companion object {
        internal const val DATABASE_NAME = "toolbox_ledger.db"
        internal const val DATABASE_VERSION = 5
        internal const val TABLE_ENTRIES = "ledger_entries"
        internal const val TABLE_TAGS = "ledger_tags"
        internal const val TABLE_ACCOUNTS = "ledger_accounts"
    }
}
