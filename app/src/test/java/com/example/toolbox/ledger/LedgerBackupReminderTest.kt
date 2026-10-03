package com.example.toolbox.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerBackupReminderTest {

    private val day = LedgerCalculator.MILLIS_PER_DAY
    private val now = 1_000 * day

    private fun due(
        lastBackup: Long?,
        earliest: Long?,
        latest: Long?,
        snoozed: Long? = null,
    ) = LedgerBackupReminder.isDue(lastBackup, earliest, latest, snoozed, now)

    @Test
    fun testNothingToBackUpNeverReminds() {
        assertFalse(due(lastBackup = null, earliest = null, latest = null))
        assertFalse(due(lastBackup = now - 30 * day, earliest = null, latest = null))
    }

    @Test
    fun testNeverBackedUpRemindsOnceDataIsAWeekOld() {
        assertFalse(due(lastBackup = null, earliest = now - 3 * day, latest = now - day))
        assertTrue(due(lastBackup = null, earliest = now - 7 * day, latest = now - day))
    }

    @Test
    fun testRemindsOnlyWhenChangedSinceAStaleBackup() {
        assertFalse(due(lastBackup = now - 10 * day, earliest = now - 90 * day, latest = now - 11 * day))
        assertFalse(due(lastBackup = now - 10 * day, earliest = now - 90 * day, latest = now - 10 * day))
        assertTrue(due(lastBackup = now - 10 * day, earliest = now - 90 * day, latest = now - day))
        assertFalse(due(lastBackup = now - 2 * day, earliest = now - 90 * day, latest = now - day))
    }

    @Test
    fun testSnoozeHidesUntilItExpires() {
        assertFalse(due(null, now - 30 * day, now - day, snoozed = now + 1))
        assertTrue(due(null, now - 30 * day, now - day, snoozed = now))
        assertEquals(now + 7 * day, LedgerBackupReminder.snoozeUntil(now))
    }

    @Test
    fun testDaysSinceRoundsDownAndNeverNegative() {
        assertEquals(9L, LedgerBackupReminder.daysSince(now - 9 * day - 5, now))
        assertEquals(0L, LedgerBackupReminder.daysSince(now + day, now))
    }
}
