package com.example.toolbox.ledger

import android.content.Context
import androidx.core.content.edit

object LedgerBackupReminder {
    const val REMIND_AFTER_DAYS = 7L
    const val SNOOZE_DAYS = 7L

    fun isDue(
        lastBackupAtMillis: Long?,
        earliestDataAtMillis: Long?,
        latestChangeAtMillis: Long?,
        snoozedUntilMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        if (latestChangeAtMillis == null) return false
        if (snoozedUntilMillis != null && nowMillis < snoozedUntilMillis) return false
        if (lastBackupAtMillis != null && latestChangeAtMillis <= lastBackupAtMillis) return false
        val since = lastBackupAtMillis ?: earliestDataAtMillis ?: return false
        return nowMillis - since >= REMIND_AFTER_DAYS * LedgerCalculator.MILLIS_PER_DAY
    }

    fun daysSince(millis: Long, nowMillis: Long): Long =
        ((nowMillis - millis) / LedgerCalculator.MILLIS_PER_DAY).coerceAtLeast(0L)

    fun snoozeUntil(nowMillis: Long): Long =
        nowMillis + SNOOZE_DAYS * LedgerCalculator.MILLIS_PER_DAY
}

class LedgerBackupStatusStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastFileExportAtMillis(): Long? =
        if (prefs.contains(KEY_FILE_EXPORT_AT)) prefs.getLong(KEY_FILE_EXPORT_AT, 0L) else null

    fun saveFileExport(millis: Long) {
        prefs.edit { putLong(KEY_FILE_EXPORT_AT, millis) }
    }

    fun snoozedUntilMillis(): Long? =
        if (prefs.contains(KEY_SNOOZED_UNTIL)) prefs.getLong(KEY_SNOOZED_UNTIL, 0L) else null

    fun saveSnoozedUntil(millis: Long) {
        prefs.edit { putLong(KEY_SNOOZED_UNTIL, millis) }
    }

    companion object {
        private const val PREFS_NAME = "ledger_backup_status"
        private const val KEY_FILE_EXPORT_AT = "file_export_at"
        private const val KEY_SNOOZED_UNTIL = "snoozed_until"
    }
}
