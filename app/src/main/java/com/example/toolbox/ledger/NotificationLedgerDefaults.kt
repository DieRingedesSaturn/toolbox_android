package com.example.toolbox.ledger

import org.json.JSONArray
import org.json.JSONObject

/** Local per-source editor defaults; these never decide or save a transaction. */
data class NotificationLedgerDefaults(
    val type: LedgerEntryType = LedgerEntryType.EXPENSE,
    val accountUuid: String? = null,
    val tagUuids: List<String> = emptyList(),
) {
    fun accountFor(accounts: List<LedgerAccount>, lastAccountUuid: String?): String {
        val available = accounts.filter { it.deletedAtMillis == null && !it.isArchived }
        return listOfNotNull(accountUuid, lastAccountUuid, LedgerAccounts.DEFAULT_ACCOUNT_UUID)
            .firstOrNull { uuid -> available.any { it.uuid == uuid } }
            ?: available.firstOrNull()?.uuid ?: LedgerAccounts.DEFAULT_ACCOUNT_UUID
    }

    fun resolve(accounts: List<LedgerAccount>, tags: List<LedgerTag>): NotificationLedgerDefaults = copy(
        type = type.takeIf { it == LedgerEntryType.INCOME } ?: LedgerEntryType.EXPENSE,
        accountUuid = accountUuid?.takeIf { uuid ->
            accounts.any { it.uuid == uuid && it.deletedAtMillis == null && !it.isArchived }
        },
        tagUuids = tagUuids.distinct().filter { uuid ->
            tags.any { it.uuid == uuid && it.deletedAtMillis == null }
        },
    )

    internal fun toJson(): String = JSONObject().apply {
        put("type", type.name)
        put("account", accountUuid ?: JSONObject.NULL)
        put("tags", JSONArray(tagUuids.distinct()))
    }.toString()

    companion object {
        internal fun fromJson(value: String?): NotificationLedgerDefaults = runCatching {
            val json = JSONObject(value ?: "{}")
            val tags = json.optJSONArray("tags") ?: JSONArray()
            NotificationLedgerDefaults(
                type = if (json.optString("type") == LedgerEntryType.INCOME.name) {
                    LedgerEntryType.INCOME
                } else {
                    LedgerEntryType.EXPENSE
                },
                accountUuid = if (json.isNull("account")) null else {
                    json.optString("account").takeIf { it.isNotBlank() }
                },
                tagUuids = (0 until tags.length()).mapNotNull { index ->
                    (tags.opt(index) as? String)?.takeIf { it.isNotBlank() }
                }.distinct(),
            )
        }.getOrDefault(NotificationLedgerDefaults())
    }
}
