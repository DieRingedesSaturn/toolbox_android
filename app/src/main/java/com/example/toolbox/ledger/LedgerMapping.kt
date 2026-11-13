package com.example.toolbox.ledger

import android.content.ContentValues
import android.database.Cursor
import com.example.toolbox.ui.*
import org.json.JSONArray

internal fun toContentValues(entry: LedgerEntry): ContentValues = ContentValues().apply {
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


internal fun parseTagUuids(raw: String?): List<String> =
    runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())


internal fun toTagValues(tag: LedgerTag): ContentValues = ContentValues().apply {
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


internal fun tagFromCursor(cursor: Cursor): LedgerTag {
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


internal fun toAccountValues(account: LedgerAccount): ContentValues =
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


internal fun accountFromCursor(cursor: Cursor): LedgerAccount {
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


internal fun nullableString(cursor: Cursor, column: String): String? {
    val idx = cursor.getColumnIndexOrThrow(column)
    return if (cursor.isNull(idx)) null else cursor.getString(idx)
}


internal fun nullableLong(cursor: Cursor, column: String): Long? {
    val idx = cursor.getColumnIndexOrThrow(column)
    return if (cursor.isNull(idx)) null else cursor.getLong(idx)
}


internal fun fromCursor(cursor: Cursor): LedgerEntry {
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
