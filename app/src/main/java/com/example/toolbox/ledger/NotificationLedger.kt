package com.example.toolbox.ledger

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
)

/** Keeps only extracted amounts and the source package, never notification bodies. */
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

    fun setAllowedPackages(packages: Set<String>) {
        preferences.edit { putStringSet("packages", packages.toSet()) }
        synchronized(lock) {
            saveCandidates(readCandidates().filter { it.packageName in packages })
        }
    }

    fun candidates(): List<NotificationLedgerCandidate> = synchronized(lock) {
        val fresh = readCandidates().filter {
            System.currentTimeMillis() - it.occurredAtMillis in 0..MAX_AGE_MILLIS &&
                it.packageName in allowedPackages()
        }
        saveCandidates(fresh)
        fresh.sortedByDescending { it.occurredAtMillis }
    }

    fun removeCandidate(id: String) {
        synchronized(lock) { saveCandidates(readCandidates().filterNot { it.id == id }) }
    }

    fun clearCandidates() {
        synchronized(lock) { preferences.edit { remove("candidates") } }
    }

    fun record(notification: StatusBarNotification) {
        if (!enabled() || notification.packageName == preferencesPackageName ||
            notification.packageName !in allowedPackages()
        ) return
        if (notification.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.notification.extras ?: return
        val text = listOfNotNull(
            extras.getCharSequence(android.app.Notification.EXTRA_TITLE),
            extras.getCharSequence(android.app.Notification.EXTRA_TEXT),
            extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT),
        ).joinToString(" ").take(2_000)
        val amounts = NotificationAmountParser.parse(text)
        if (amounts.isEmpty()) return
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(notification.key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val item = NotificationLedgerCandidate(
            id = digest,
            packageName = notification.packageName,
            occurredAtMillis = notification.postTime,
            amountsCents = amounts,
        )
        synchronized(lock) {
            saveCandidates((listOf(item) + readCandidates().filterNot { it.id == digest })
                .take(MAX_CANDIDATES))
        }
    }

    private fun readCandidates(): List<NotificationLedgerCandidate> = runCatching {
        val array = JSONArray(preferences.getString("candidates", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val amounts = item.getJSONArray("amounts")
            NotificationLedgerCandidate(
                id = item.getString("id"),
                packageName = item.getString("package"),
                occurredAtMillis = item.getLong("time"),
                amountsCents = (0 until amounts.length()).map { amounts.getLong(it) },
            )
        }
    }.getOrDefault(emptyList())

    private fun saveCandidates(items: List<NotificationLedgerCandidate>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("package", item.packageName)
                put("time", item.occurredAtMillis)
                put("amounts", JSONArray(item.amountsCents))
            })
        }
        preferences.edit { putString("candidates", array.toString()) }
    }

    private companion object {
        val lock = Any()
        const val MAX_CANDIDATES = 30
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
    private val balanceLead = Regex("余额|可用额度|balance", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<Long> {
        if (codeNotice.containsMatchIn(text)) return emptyList()
        return amountPattern.findAll(text)
            .mapNotNull { match ->
                val number = match.groupValues[2]
                val marked = match.groupValues[1].isNotEmpty() || match.groupValues[3].isNotEmpty()
                if (!marked && !twoDecimals.containsMatchIn(number)) return@mapNotNull null
                val lead = text.substring((match.range.first - 6).coerceAtLeast(0), match.range.first)
                if (balanceLead.containsMatchIn(lead)) return@mapNotNull null
                runCatching {
                    BigDecimal(number.replace(",", "")).movePointRight(2).longValueExact()
                }.getOrNull()?.takeIf { it > 0 }
            }
            .distinct()
            .take(10)
            .toList()
    }
}
