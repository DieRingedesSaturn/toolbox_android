package com.example.toolbox.ledger

import android.annotation.SuppressLint
import android.app.Notification
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
    /** SHA-256 of [text], kept so a repost can be recognized without storing the body. */
    val bodyFingerprint: String = "",
    val skipReason: NotificationSkipReason? = null,
    /** Source-aware parse result; null when no payment template matched. */
    val payment: NotificationPayment? = null,
    /** Entry this candidate was auto-booked as; null while still a suggestion. */
    val autoBookedEntryUuid: String? = null,
)

/** Notification fields extracted on the callback thread; safe to hand to a worker. */
data class NotificationLedgerInput(
    val packageName: String,
    val key: String,
    val occurredAtMillis: Long,
    val title: String,
    val text: String,
)

enum class NotificationSkipReason { EMPTY_TEXT, VERIFICATION_CODE, NO_AMOUNT }

data class ParsedNotification(
    val amountsCents: List<Long>,
    val skipReason: NotificationSkipReason?,
)

/**
 * Keeps every notification from selected source apps — extracted amounts plus the
 * title so the user can audit what was captured. The notification body is not
 * written to SharedPreferences; only a SHA-256 fingerprint is kept so a repost
 * can be recognized. Verification-code notices are the exception that
 * also drops the title: they retain only source, time, and skip reason, never the
 * OTP text. Records stay local only, are pruned after [MAX_AGE_MILLIS] when the
 * list is read or another notification arrives, and are wiped when the feature is
 * turned off.
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

    /** Remembers the ledger entry a candidate was automatically booked as. */
    fun markAutoBooked(id: String, entryUuid: String) {
        synchronized(lock) {
            saveRecords(readRecords().map {
                if (it.id == id) it.copy(autoBookedEntryUuid = entryUuid) else it
            })
        }
    }

    /** Drops the auto-book marker after the entry was undone. */
    fun clearAutoBook(id: String) {
        synchronized(lock) {
            saveRecords(readRecords().map {
                if (it.id == id) it.copy(autoBookedEntryUuid = null) else it
            })
        }
    }

    fun clearCandidates() {
        synchronized(lock) { preferences.edit { remove("candidates") } }
    }

    /** Removes notification bodies already stored by older versions. */
    internal fun redactStoredBodies() {
        synchronized(lock) {
            val raw = preferences.getString("candidates", null) ?: return
            val array = runCatching { JSONArray(raw) }.getOrNull()
            if (array == null) {
                if (raw.contains("\"text\"")) {
                    preferences.edit { remove("candidates") }
                }
                return
            }
            var changed = false
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                if (item.optString("text").isNotEmpty()) {
                    if (item.optString("textHash").isEmpty()) {
                        item.put("textHash", fingerprint(item.optString("text")))
                    }
                    item.put("text", "")
                    changed = true
                }
            }
            if (changed) {
                preferences.edit { putString("candidates", array.toString()) }
            }
        }
    }

    fun record(input: NotificationLedgerInput): NotificationLedgerCandidate? = recordParts(
        packageName = input.packageName,
        key = input.key,
        occurredAtMillis = input.occurredAtMillis,
        title = input.title,
        text = input.text,
    )

    internal fun recordParts(
        packageName: String,
        key: String,
        occurredAtMillis: Long,
        title: String,
        text: String,
    ): NotificationLedgerCandidate? {
        if (!enabled() || packageName == preferencesPackageName ||
            packageName !in allowedPackages()
        ) return null
        val body = listOf(title, text).filter { it.isNotBlank() }.joinToString(" ")
        val result = if (body.isBlank()) {
            ParsedNotification(emptyList(), NotificationSkipReason.EMPTY_TEXT)
        } else {
            NotificationAmountParser.parseDetailed(body)
        }
        // A verification-code notice keeps no body and never reaches payment
        // parsing: the one-time code is the sensitive part.
        val keepText = result.skipReason != NotificationSkipReason.VERIFICATION_CODE
        val storedTitle = if (keepText) title else ""
        val storedText = if (keepText) text else ""
        val storedFingerprint = fingerprint(storedText)
        val payment = if (body.isBlank() || !keepText) null else {
            PaymentNotificationParser.parse(packageName, title, text)
        }
        val digest = fingerprint(key)
        synchronized(lock) {
            val nowMillis = System.currentTimeMillis()
            val existing = readRecords().filter {
                nowMillis - it.occurredAtMillis in 0..MAX_AGE_MILLIS
            }
            val previous = existing.firstOrNull { it.id == digest }
            val isRepost = previous != null &&
                previous.title == storedTitle &&
                previous.bodyFingerprint == storedFingerprint &&
                previous.payment == payment &&
                kotlin.math.abs(occurredAtMillis - previous.occurredAtMillis) <=
                REPOST_WINDOW_MILLIS
            val item = NotificationLedgerCandidate(
                id = digest,
                packageName = packageName,
                occurredAtMillis = occurredAtMillis,
                amountsCents = result.amountsCents,
                title = storedTitle,
                text = storedText,
                bodyFingerprint = storedFingerprint,
                skipReason = result.skipReason,
                payment = payment,
                // Only an actual repost keeps the auto-book marker. Payment
                // apps may reuse a notification key for a later transaction.
                autoBookedEntryUuid = previous?.autoBookedEntryUuid?.takeIf { isRepost },
            )
            saveRecords((listOf(item) + existing.filterNot { it.id == digest }).trimmed())
            return item
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
            val legacyText = item.optString("text")
            NotificationLedgerCandidate(
                id = item.getString("id"),
                packageName = item.getString("package"),
                occurredAtMillis = item.getLong("time"),
                amountsCents = (0 until amounts.length()).map { amounts.getLong(it) },
                title = item.optString("title"),
                text = "",
                bodyFingerprint = item.optString("textHash").ifEmpty { fingerprint(legacyText) },
                skipReason = item.optString("reason").takeIf { it.isNotEmpty() }
                    ?.let { runCatching { NotificationSkipReason.valueOf(it) }.getOrNull() },
                payment = item.optJSONObject("pay")?.let(::paymentFromJson),
                autoBookedEntryUuid = item.optString("autoEntry").takeIf { it.isNotEmpty() },
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
                // Body text used to be stored in plaintext. Keep the key empty and
                // store only a hash so reposts can still be recognized.
                put("text", "")
                if (item.bodyFingerprint.isNotEmpty()) put("textHash", item.bodyFingerprint)
                item.skipReason?.let { put("reason", it.name) }
                item.payment?.let { put("pay", paymentToJson(it)) }
                item.autoBookedEntryUuid?.let { put("autoEntry", it) }
            })
        }
        preferences.edit { putString("candidates", array.toString()) }
    }

    private fun fingerprint(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun paymentToJson(payment: NotificationPayment): JSONObject = JSONObject().apply {
        put("direction", payment.direction.name)
        put("amount", payment.amountCents)
        put("label", payment.label ?: JSONObject.NULL)
        put("confident", payment.confident)
    }

    private fun paymentFromJson(json: JSONObject): NotificationPayment? = runCatching {
        val direction = NotificationPaymentDirection.valueOf(json.getString("direction"))
        NotificationPayment(
            direction = direction,
            amountCents = json.getLong("amount"),
            label = if (json.isNull("label")) null else {
                json.optString("label").takeIf { it.isNotBlank() }
            },
            confident = json.optBoolean("confident"),
        )
    }.getOrNull()

    internal companion object {
        val lock = Any()
        const val MAX_CANDIDATES = 30
        const val MAX_IGNORED = 15
        const val MAX_FIELD_CHARS = 800
        const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1_000
        const val REPOST_WINDOW_MILLIS = 10_000L

        /**
         * Extracts the fields the listener needs, on the thread that received
         * the callback. Group summaries are skipped because their children
         * carry the actual text.
         */
        internal fun inputOf(notification: StatusBarNotification): NotificationLedgerInput? {
            if (notification.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
            val extras = notification.notification.extras
            val title = listOfNotNull(
                extras?.getCharSequence(Notification.EXTRA_TITLE),
                extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE),
                extras?.getCharSequence(Notification.EXTRA_TITLE_BIG),
            ).map { it.toString() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" ")
                .take(MAX_FIELD_CHARS)
            val text = listOfNotNull(
                extras?.getCharSequence(Notification.EXTRA_TEXT),
                extras?.getCharSequence(Notification.EXTRA_BIG_TEXT),
                extras?.getCharSequence(Notification.EXTRA_SUB_TEXT),
                extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" "),
            ).joinToString(" ").take(MAX_FIELD_CHARS)
            return NotificationLedgerInput(
                packageName = notification.packageName,
                key = notification.key,
                occurredAtMillis = notification.postTime,
                title = title,
                text = text,
            )
        }
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
