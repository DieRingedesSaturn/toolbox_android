package com.example.toolbox.ledger

import org.json.JSONArray
import org.json.JSONObject

const val LEDGER_SCHEMA_VERSION = 3

/**
 * Payload format shared by local file backups (export/import) and the WebDAV
 * ledger.json exchange. Written payloads use [LEDGER_SCHEMA_VERSION]; reading
 * stays backward-compatible with v1 files (missing fx fields default to
 * CNY semantics).
 */
data class LedgerSyncPayload(
    val schemaVersion: Int = LEDGER_SCHEMA_VERSION,
    val clientTimestampMillis: Long = System.currentTimeMillis(),
    val baseServerRevision: Long = 0L,
    val entries: List<LedgerEntry>,
    val tags: List<LedgerTag> = emptyList(),
    val accounts: List<LedgerAccount> = emptyList(),
)

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key) || !has(key)) {
        null
    } else {
        optString(key).takeIf { it.isNotBlank() }
    }

object LedgerSyncSerializer {
    fun toJsonString(payload: LedgerSyncPayload, indentSpaces: Int = 2): String {
        val root = JSONObject()
        root.put("schemaVersion", payload.schemaVersion)
        root.put("clientTimestampMillis", payload.clientTimestampMillis)
        root.put("baseServerRevision", payload.baseServerRevision)

        val entriesArray = JSONArray()
        for (entry in payload.entries) {
            entriesArray.put(entryToJson(entry))
        }
        root.put("entries", entriesArray)
        val tagsArray = JSONArray()
        for (tag in payload.tags) {
            tagsArray.put(tagToJson(tag))
        }
        root.put("tags", tagsArray)
        val accountsArray = JSONArray()
        for (account in payload.accounts) {
            accountsArray.put(accountToJson(account))
        }
        root.put("accounts", accountsArray)
        return if (indentSpaces > 0) root.toString(indentSpaces) else root.toString()
    }

    fun fromJsonString(json: String): LedgerSyncPayload {
        val root = JSONObject(json)
        val schemaVersion = root.optInt("schemaVersion", 1)
        val clientTimestampMillis = root.optLong("clientTimestampMillis", System.currentTimeMillis())
        val baseServerRevision = root.optLong("baseServerRevision", 0L)
        val entriesArray = root.optJSONArray("entries") ?: JSONArray()

        val list = mutableListOf<LedgerEntry>()
        for (i in 0 until entriesArray.length()) {
            val obj = entriesArray.optJSONObject(i) ?: continue
            list.add(jsonToEntry(obj))
        }

        val tagsArray = root.optJSONArray("tags") ?: JSONArray()
        val tags = mutableListOf<LedgerTag>()
        for (i in 0 until tagsArray.length()) {
            val obj = tagsArray.optJSONObject(i) ?: continue
            tags.add(jsonToTag(obj))
        }
        val accountsArray = root.optJSONArray("accounts") ?: JSONArray()
        val accounts = mutableListOf<LedgerAccount>()
        for (i in 0 until accountsArray.length()) {
            val obj = accountsArray.optJSONObject(i) ?: continue
            accounts.add(jsonToAccount(obj))
        }
        return LedgerSyncPayload(
            schemaVersion = schemaVersion,
            clientTimestampMillis = clientTimestampMillis,
            baseServerRevision = baseServerRevision,
            entries = list,
            tags = tags,
            accounts = accounts,
        )
    }

    fun entryToJson(entry: LedgerEntry): JSONObject = JSONObject().apply {
        put("uuid", entry.uuid)
        put("title", entry.title)
        put("amountCents", entry.amountCents)
        put("currency", entry.currency)
        put("fxRateToCny", entry.fxRateToCny)
        if (entry.fxRateDate != null) put("fxRateDate", entry.fxRateDate) else put("fxRateDate", JSONObject.NULL)
        if (entry.fxRateSource != null) put("fxRateSource", entry.fxRateSource) else put("fxRateSource", JSONObject.NULL)
        if (entry.parentUuid != null) put("parentUuid", entry.parentUuid) else put("parentUuid", JSONObject.NULL)
        if (entry.linkType != null) put("linkType", entry.linkType) else put("linkType", JSONObject.NULL)
        if (entry.renewalIndex != null) put("renewalIndex", entry.renewalIndex) else put("renewalIndex", JSONObject.NULL)
        if (entry.disposalType != null) put("disposalType", entry.disposalType) else put("disposalType", JSONObject.NULL)
        val tagUuids = JSONArray()
        for (uuid in entry.tagUuids) {
            tagUuids.put(uuid)
        }
        put("tagUuids", tagUuids)
        put("accountUuid", entry.accountUuid)
        put("accountAmountCents", entry.accountAmountCents ?: JSONObject.NULL)
        put("toAccountUuid", entry.toAccountUuid)
        put("toAmountCents", entry.toAmountCents ?: JSONObject.NULL)
        put("type", entry.type.name)
        put("category", entry.category)
        put("occurredAtMillis", entry.occurredAtMillis)
        put("note", entry.note)
        put("costTrackingMode", entry.costTrackingMode.name)
        put("salvageValueCents", entry.salvageValueCents)
        if (entry.targetDays != null) put("targetDays", entry.targetDays) else put("targetDays", JSONObject.NULL)
        if (entry.retiredAtMillis != null) put("retiredAtMillis", entry.retiredAtMillis) else put("retiredAtMillis", JSONObject.NULL)
        put("billingCycle", entry.billingCycle.name)
        put("customCycleDays", entry.customCycleDays)
        put("customCycleUnit", entry.customCycleUnit.name)
        put("isActiveCost", entry.isActiveCost)
        put("createdAtMillis", entry.createdAtMillis)
        put("updatedAtMillis", entry.updatedAtMillis)
        if (entry.deletedAtMillis != null) put("deletedAtMillis", entry.deletedAtMillis) else put("deletedAtMillis", JSONObject.NULL)
        put("syncStatus", entry.syncStatus.name)
        put("serverRevision", entry.serverRevision)
    }

    fun jsonToEntry(obj: JSONObject): LedgerEntry = LedgerEntry(
        uuid = obj.getString("uuid"),
        title = obj.getString("title"),
        amountCents = obj.getLong("amountCents"),
        currency = obj.optString("currency", "CNY"),
        fxRateToCny = obj.optDouble("fxRateToCny", 1.0).takeIf { it > 0 } ?: 1.0,
        fxRateDate = if (obj.isNull("fxRateDate")) {
            null
        } else {
            obj.optString("fxRateDate").takeIf { it.isNotBlank() }
        },
        fxRateSource = obj.optNullableString("fxRateSource"),
        parentUuid = obj.optNullableString("parentUuid"),
        linkType = obj.optNullableString("linkType"),
        renewalIndex = if (obj.isNull("renewalIndex")) {
            null
        } else {
            obj.optInt("renewalIndex")
        },
        disposalType = obj.optNullableString("disposalType"),
        tagUuids = obj.optJSONArray("tagUuids")?.let { array ->
            (0 until array.length()).mapNotNull {
                array.optString(it).takeIf(String::isNotBlank)
            }
        } ?: listOf(
            LedgerTags.categoryTagUuid(
                LedgerCategory.fromStorage(
                    obj.optString("category", LedgerCategory.OTHER.name),
                ),
            ),
        ),
        // Entries from v1/v2 files without an account post to the default.
        accountUuid = obj.optNullableString("accountUuid")
            ?: LedgerAccounts.DEFAULT_ACCOUNT_UUID,
        accountAmountCents = if (obj.isNull("accountAmountCents")) {
            null
        } else {
            obj.optLong("accountAmountCents")
        },
        toAccountUuid = obj.optNullableString("toAccountUuid"),
        toAmountCents = if (obj.isNull("toAmountCents")) {
            null
        } else {
            obj.optLong("toAmountCents")
        },
        type = runCatching { LedgerEntryType.valueOf(obj.optString("type", "EXPENSE")) }
            .getOrDefault(LedgerEntryType.EXPENSE),
        category = obj.optString("category", LedgerCategory.OTHER.name),
        occurredAtMillis = obj.getLong("occurredAtMillis"),
        note = obj.optString("note", ""),
        costTrackingMode = runCatching { CostTrackingMode.valueOf(obj.optString("costTrackingMode", "NONE")) }
            .getOrDefault(CostTrackingMode.NONE),
        salvageValueCents = obj.optLong("salvageValueCents", 0L),
        targetDays = if (obj.isNull("targetDays")) null else obj.optInt("targetDays", 0).takeIf { it > 0 },
        retiredAtMillis = if (obj.isNull("retiredAtMillis")) null else obj.optLong("retiredAtMillis", 0L).takeIf { it > 0L },
        billingCycle = runCatching { BillingCycle.valueOf(obj.optString("billingCycle", "MONTHLY")) }
            .getOrDefault(BillingCycle.MONTHLY),
        customCycleDays = obj.optInt("customCycleDays", 30).coerceAtLeast(1),
        customCycleUnit = obj.optString("customCycleUnit", "DAYS")
            .let { runCatching { CycleUnit.valueOf(it) }.getOrNull() }
            ?: CycleUnit.DAYS,
        isActiveCost = obj.optBoolean("isActiveCost", true),
        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
        updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis()),
        deletedAtMillis = if (obj.isNull("deletedAtMillis")) null else obj.optLong("deletedAtMillis", 0L).takeIf { it > 0L },
        syncStatus = runCatching { LedgerSyncStatus.valueOf(obj.optString("syncStatus", "PENDING_PUSH")) }
            .getOrDefault(LedgerSyncStatus.PENDING_PUSH),
        serverRevision = obj.optLong("serverRevision", 0L),
    )

    fun tagToJson(tag: LedgerTag): JSONObject = JSONObject().apply {
        put("uuid", tag.uuid)
        put("name", tag.name)
        put("colorArgb", tag.colorArgb)
        put("emoji", tag.emoji)
        put("sortOrder", tag.sortOrder)
        put("createdAtMillis", tag.createdAtMillis)
        put("updatedAtMillis", tag.updatedAtMillis)
        if (tag.deletedAtMillis != null) {
            put("deletedAtMillis", tag.deletedAtMillis)
        } else {
            put("deletedAtMillis", JSONObject.NULL)
        }
        put("syncStatus", tag.syncStatus.name)
    }

    fun jsonToTag(obj: JSONObject): LedgerTag = LedgerTag(
        uuid = obj.getString("uuid"),
        name = obj.getString("name"),
        colorArgb = obj.getInt("colorArgb"),
        emoji = obj.optString("emoji", ""),
        sortOrder = obj.optInt("sortOrder", 0),
        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
        updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis()),
        deletedAtMillis = if (obj.isNull("deletedAtMillis")) {
            null
        } else {
            obj.optLong("deletedAtMillis")
        },
        syncStatus = runCatching {
            LedgerSyncStatus.valueOf(obj.optString("syncStatus", "PENDING_PUSH"))
        }.getOrDefault(LedgerSyncStatus.PENDING_PUSH),
    )

    fun accountToJson(account: LedgerAccount): JSONObject = JSONObject().apply {
        put("uuid", account.uuid)
        put("name", account.name)
        put("currency", account.currency)
        put("openingBalanceCents", account.openingBalanceCents)
        put("openingAtMillis", account.openingAtMillis)
        put("colorArgb", account.colorArgb)
        put("emoji", account.emoji)
        put("sortOrder", account.sortOrder)
        put("isArchived", account.isArchived)
        put("createdAtMillis", account.createdAtMillis)
        put("updatedAtMillis", account.updatedAtMillis)
        if (account.deletedAtMillis != null) {
            put("deletedAtMillis", account.deletedAtMillis)
        } else {
            put("deletedAtMillis", JSONObject.NULL)
        }
        put("syncStatus", account.syncStatus.name)
    }

    fun jsonToAccount(obj: JSONObject): LedgerAccount = LedgerAccount(
        uuid = obj.getString("uuid"),
        name = obj.getString("name"),
        currency = obj.optString("currency", "CNY"),
        openingBalanceCents = obj.optLong("openingBalanceCents", 0L),
        openingAtMillis = obj.optLong("openingAtMillis", 0L),
        colorArgb = obj.optInt("colorArgb", 0xFF78909C.toInt()),
        emoji = obj.optString("emoji", ""),
        sortOrder = obj.optInt("sortOrder", 0),
        isArchived = obj.optBoolean("isArchived", false),
        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
        updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis()),
        deletedAtMillis = if (obj.isNull("deletedAtMillis")) {
            null
        } else {
            obj.optLong("deletedAtMillis")
        },
        syncStatus = runCatching {
            LedgerSyncStatus.valueOf(obj.optString("syncStatus", "PENDING_PUSH"))
        }.getOrDefault(LedgerSyncStatus.PENDING_PUSH),
    )
}
