package com.example.toolbox.ledger

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NotificationLedgerDefaultsTest {
    private lateinit var context: Context
    private val account = LedgerAccounts.defaultAccount("Cash")
    private val tag = LedgerTag("food", "Food", 0, sortOrder = 0, createdAtMillis = 0, updatedAtMillis = 0)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("notification_ledger", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun noDefaultsKeepExpenseFollowLastAccountAndNoTags() {
        assertEquals(NotificationLedgerDefaults(), NotificationLedgerStore(context).defaultsFor("bank"))
    }

    @Test
    fun persistsIndependentDefaultsForEachSource() {
        val bank = NotificationLedgerDefaults(LedgerEntryType.EXPENSE, account.uuid, listOf(tag.uuid))
        val wallet = NotificationLedgerDefaults(LedgerEntryType.INCOME)
        NotificationLedgerStore(context).apply {
            saveDefaults("bank", bank)
            saveDefaults("wallet", wallet)
        }
        val reopened = NotificationLedgerStore(context)
        assertEquals(bank, reopened.defaultsFor("bank"))
        assertEquals(wallet, reopened.defaultsFor("wallet"))
        reopened.saveDefaults("bank", NotificationLedgerDefaults())
        assertEquals(NotificationLedgerDefaults(), reopened.defaultsFor("bank"))
        assertEquals(wallet, reopened.defaultsFor("wallet"))
    }

    @Test
    fun removingSourceOrTurningOffDoesNotLoseItsDefaults() {
        val store = NotificationLedgerStore(context)
        val defaults = NotificationLedgerDefaults(LedgerEntryType.INCOME, account.uuid, listOf(tag.uuid))
        store.saveDefaults("bank", defaults)
        store.setAllowedPackages(setOf("bank"))
        store.setAllowedPackages(emptySet())
        store.setEnabled(false)
        assertEquals(defaults, store.defaultsFor("bank"))
    }

    @Test
    fun resolvesDeletedArchivedAndMissingReferences() {
        val defaults = NotificationLedgerDefaults(LedgerEntryType.INCOME, account.uuid, listOf("food", "gone", "food"))
        assertEquals(listOf("food"), defaults.resolve(listOf(account), listOf(tag)).tagUuids)
        assertEquals(account.uuid, defaults.resolve(listOf(account), listOf(tag)).accountUuid)
        assertNull(defaults.resolve(listOf(account.copy(isArchived = true)), listOf(tag)).accountUuid)
        assertNull(defaults.resolve(listOf(account.copy(deletedAtMillis = 1)), listOf(tag)).accountUuid)
        assertEquals(NotificationLedgerDefaults(LedgerEntryType.INCOME), defaults.resolve(emptyList(), listOf(tag.copy(deletedAtMillis = 1))))
    }

    @Test
    fun onlyIncomeAndExpenseAreValidDefaults() {
        assertEquals(LedgerEntryType.EXPENSE, NotificationLedgerDefaults(LedgerEntryType.TRANSFER).resolve(emptyList(), emptyList()).type)
        assertEquals(LedgerEntryType.EXPENSE, NotificationLedgerDefaults.fromJson("{\"type\":\"ADJUSTMENT\"}").type)
    }

    @Test
    fun malformedSettingsFallBackAndInvalidTagItemsAreSkipped() {
        assertEquals(NotificationLedgerDefaults(), NotificationLedgerDefaults.fromJson("broken"))
        assertEquals(NotificationLedgerDefaults(), NotificationLedgerDefaults.fromJson(null))
        val defaults = NotificationLedgerDefaults.fromJson("{\"tags\":[\"food\",123,null,\"\",\"food\"]}")
        assertEquals(listOf("food"), defaults.tagUuids)
        assertNull(defaults.accountUuid)
    }

    @Test
    fun accountFallbackUsesValidLastDefaultThenFirstAvailable() {
        val other = account.copy(uuid = "other")
        val defaults = NotificationLedgerDefaults(accountUuid = "missing")
        assertEquals("other", defaults.accountFor(listOf(account, other), "other"))
        assertEquals(account.uuid, defaults.accountFor(listOf(account, other), "deleted"))
        assertEquals("other", defaults.accountFor(listOf(other), "deleted"))
        assertEquals(account.uuid, NotificationLedgerDefaults(accountUuid = account.uuid).accountFor(listOf(account, other), "other"))
        assertEquals("other", NotificationLedgerDefaults(accountUuid = account.uuid).accountFor(listOf(account.copy(isArchived = true), other), "other"))
    }

    @Test
    fun consumingAnOlderNotificationDoesNotDiscardItsNewerUpdate() {
        val time = System.currentTimeMillis() - 1_000L
        val store = NotificationLedgerStore(context)
        store.setAllowedPackages(setOf("bank"))
        context.getSharedPreferences("notification_ledger", Context.MODE_PRIVATE).edit().putString(
            "candidates", "[{\"id\":\"same-key\",\"package\":\"bank\",\"time\":$time,\"amounts\":[2500]}]",
        ).commit()
        store.removeCandidate("same-key", time - 1)
        assertEquals(1, store.candidates().size)
        store.removeCandidate("same-key", time, listOf(2_600L))
        assertEquals(1, store.candidates().size)
        store.removeCandidate("same-key", time, listOf(2_500L))
        assertTrue(store.candidates().isEmpty())
    }
}
