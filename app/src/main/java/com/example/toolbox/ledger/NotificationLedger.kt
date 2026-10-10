package com.example.toolbox.ledger

import android.annotation.SuppressLint
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.content.edit
import java.math.BigDecimal
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

data class NotificationLedgerCandidate(
    val id: String,
    val packageName: String,
    val occurredAtMillis: Long,
    val amountsCents: List<Long>,
    val title: String = "",
    val text: String = "",
    val skipReason: NotificationSkipReason? = null,
)

enum class NotificationSkipReason { EMPTY_TEXT, VERIFICATION_CODE, NO_AMOUNT }

data class ParsedNotification(
    val amountsCents: List<Long>,
    val skipReason: NotificationSkipReason?,
)

/**
 * Keeps every notification from selected source apps — extracted amounts plus the
 * title and body so the user can audit what was captured. Records stay local only,
 * expire after [MAX_AGE_MILLIS], and are wiped when the feature is turned off.
 */
class NotificationLedgerStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferencesPackageName = context.packageName
    private val preferences = appContext.getSharedPreferences(
        "notification_ledger",
        Context.MODE_PRIVATE,
    )

    fun enabled(): Boolean = preferences.getBoolean("enabled", false)

    fun setEnabled(value: Boolean) {
        preferences.edit { putBoolean("enabled", value) }
        if (!value) clearCandidates()
        runCatching { LedgerNotificationListener.setActive(appContext, value) }
    }

    fun allowedPackages(): Set<String> = preferences.getStringSet("packages", emptySet())
        ?.toSet().orEmpty()

    fun defaultsFor(packageName: String): NotificationLedgerDefaults =
        NotificationLedgerDefaults.fromJson(preferences.getString("defaults:$packageName", null))

    @SuppressLint("UseKtx") // KTX edit discards the commit result; the editor needs failure feedback.
    fun saveDefaults(packageName: String, defaults: NotificationLedgerDefaults) {
        require(packageName.isNotBlank())
        check(preferences.edit().putString("defaults:$packageName", defaults.toJson()).commit()) {
            "Notification defaults write failed"
        }
    }

    fun setAllowedPackages(packages: Set<String>) {
        preferences.edit { putStringSet("packages", packages.toSet()) }
        synchronized(lock) {
            saveRecords(readRecords().filter { it.packageName in packages })
        }
    }

    fun records(): List<NotificationLedgerCandidate> = synchronized(lock) {
        val fresh = readRecords().filter {
            System.currentTimeMillis() - it.occurredAtMillis in 0..MAX_AGE_MILLIS &&
                it.packageName in allowedPackages()
        }
        saveRecords(fresh)
        fresh.sortedByDescending { it.occurredAtMillis }
    }

    fun candidates(): List<NotificationLedgerCandidate> =
        records().filter { it.amountsCents.isNotEmpty() }

    fun removeCandidate(id: String, occurredAtMillis: Long? = null, amountsCents: List<Long>? = null) {
        synchronized(lock) {
            saveRecords(readRecords().filterNot {
                it.id == id && (occurredAtMillis == null || it.occurredAtMillis == occurredAtMillis) &&
                    (amountsCents == null || it.amountsCents == amountsCents)
            })
        }
    }

    fun clearCandidates() {
        synchronized(lock) { preferences.edit { remove("candidates") } }
    }

    fun record(notification: StatusBarNotification) {
        if (notification.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.notification.extras
        val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)
            ?.toString().orEmpty().take(MAX_FIELD_CHARS)
        val text = listOfNotNull(
            extras?.getCharSequence(android.app.Notification.EXTRA_TEXT),
            extras?.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT),
            extras?.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT),
        ).joinToString(" ").take(MAX_FIELD_CHARS)
        recordParts(
            packageName = notification.packageName,
            key = notification.key,
            occurredAtMillis = notification.postTime,
            title = title,
            text = text,
        )
    }

    internal fun recordParts(
        packageName: String,
        key: String,
        occurredAtMillis: Long,
        title: String,
        text: String,
    ) {
        if (!enabled() || packageName == preferencesPackageName ||
            packageName !in allowedPackages()
        ) return
        val body = listOf(title, text).filter { it.isNotBlank() }.joinToString(" ")
        val result = if (body.isBlank()) {
            ParsedNotification(emptyList(), NotificationSkipReason.EMPTY_TEXT)
        } else {
            NotificationAmountParser.parseDetailed(body)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val item = NotificationLedgerCandidate(
            id = digest,
            packageName = packageName,
            occurredAtMillis = occurredAtMillis,
            amountsCents = result.amountsCents,
            title = title,
            text = text,
            skipReason = result.skipReason,
        )
        synchronized(lock) {
            saveRecords((listOf(item) + readRecords().filterNot { it.id == digest }).trimmed())
        }
    }

    private fun List<NotificationLedgerCandidate>.trimmed(): List<NotificationLedgerCandidate> =
        filter { it.amountsCents.isNotEmpty() }.take(MAX_CANDIDATES) +
            filter { it.amountsCents.isEmpty() }.take(MAX_IGNORED)

    private fun readRecords(): List<NotificationLedgerCandidate> = runCatching {
        val array = JSONArray(preferences.getString("candidates", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val amounts = item.getJSONArray("amounts")
            NotificationLedgerCandidate(
                id = item.getString("id"),
                packageName = item.getString("package"),
                occurredAtMillis = item.getLong("time"),
                amountsCents = (0 until amounts.length()).map { amounts.getLong(it) },
                title = item.optString("title"),
                text = item.optString("text"),
                skipReason = item.optString("reason").takeIf { it.isNotEmpty() }
                    ?.let { runCatching { NotificationSkipReason.valueOf(it) }.getOrNull() },
            )
        }
    }.getOrDefault(emptyList())

    private fun saveRecords(items: List<NotificationLedgerCandidate>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("package", item.packageName)
                put("time", item.occurredAtMillis)
                put("amounts", JSONArray(item.amountsCents))
                put("title", item.title)
                put("text", item.text)
                item.skipReason?.let { put("reason", it.name) }
            })
        }
        preferences.edit { putString("candidates", array.toString()) }
    }

    private companion object {
        val lock = Any()
        const val MAX_CANDIDATES = 30
        const val MAX_IGNORED = 15
        const val MAX_FIELD_CHARS = 800
        const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1_000
    }
}

/** Deliberately offers possible amounts for a person to choose, rather than deciding a transaction. */
internal object NotificationAmountParser {
    private val amountPattern = Regex(
        "(?<![A-Za-z\\d.,])((?:[¥￥]|人民币|RMB|CNY)\\s*)?" +
            "((?:\\d{1,3}(?:,\\d{3})+|\\d{1,7})(?:\\.\\d{1,2})?)" +
            "(\\s*(?:元|圆|CNY|RMB))?(?![A-Za-z\\d.])",
        RegexOption.IGNORE_CASE,
    )
    private val twoDecimals = Regex("\\.\\d{2}$")
    private val codeNotice = Regex(
        "验证码|校验码|动态码|动态密码|确认码|安全码|" +
            "\\b(?:otp|one-time|passcode|verification code)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val balanceLead = Regex(
        "(?:余额|可用额度|\\bbalance)\\s*(?:[:：=]|为|is)?\\s*$",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): List<Long> = parseDetailed(text).amountsCents

    fun parseDetailed(text: String): ParsedNotification {
        if (codeNotice.containsMatchIn(text)) {
            return ParsedNotification(emptyList(), NotificationSkipReason.VERIFICATION_CODE)
        }
        val amounts = amountPattern.findAll(text)
            .mapNotNull { match ->
                val number = match.groupValues[2]
                val marked = match.groupValues[1].isNotEmpty() || match.groupValues[3].isNotEmpty()
                if (!marked && !twoDecimals.containsMatchIn(number)) return@mapNotNull null
                val lead = text.substring(0, match.range.first)
                if (balanceLead.containsMatchIn(lead)) return@mapNotNull null
                runCatching {
                    BigDecimal(number.replace(",", "")).movePointRight(2).longValueExact()
                }.getOrNull()?.takeIf { it > 0 }
            }
            .distinct()
            .take(10)
            .toList()
        return ParsedNotification(
            amountsCents = amounts,
            skipReason = if (amounts.isEmpty()) NotificationSkipReason.NO_AMOUNT else null,
        )
    }
}
