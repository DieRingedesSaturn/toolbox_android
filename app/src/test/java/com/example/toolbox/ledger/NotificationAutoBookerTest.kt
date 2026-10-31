package com.example.toolbox.ledger

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NotificationAutoBookerTest {
    private lateinit var context: Context
    private lateinit var notificationStore: NotificationLedgerStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(LedgerStore.DATABASE_NAME)
        context.getSharedPreferences("notification_ledger", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("toolbox_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()
        notificationStore = NotificationLedgerStore(context)
        notificationStore.setEnabled(true)
        notificationStore.setAllowedPackages(setOf("com.tencent.mm"))
    }

    private fun enableAutoBook() {
        notificationStore.saveDefaults(
            "com.tencent.mm",
            NotificationLedgerDefaults(autoBook = true),
        )
    }

    private fun record(
        text: String,
        title: String = "微信支付",
        key: String = "key-$text",
        atMillis: Long = System.currentTimeMillis(),
    ): NotificationLedgerCandidate = notificationStore.recordParts(
        packageName = "com.tencent.mm",
        key = key,
        occurredAtMillis = atMillis,
        title = title,
        text = text,
    )!!

    @Test
    fun testBooksConfidentPaymentIntoTheLedger() {
        enableAutoBook()
        val candidate = record("已支付¥16.00")
        assertEquals(NotificationAutoBooker.Outcome.BOOKED, NotificationAutoBooker.book(context, candidate))
        LedgerStore(context).use { ledger ->
            val entry = ledger.queryVisibleEntries().single()
            assertEquals("微信支付", entry.title)
            assertEquals(1_600L, entry.amountCents)
            assertEquals(LedgerEntryType.EXPENSE, entry.type)
            assertEquals(candidate.occurredAtMillis, entry.occurredAtMillis)
        }
        assertNotNull(notificationStore.records().single().autoBookedEntryUuid)
    }

    @Test
    fun testUsesTheMerchantAsTitleWhenNamed() {
        enableAutoBook()
        val candidate = record("向星巴克付款¥33.00")
        assertEquals(NotificationAutoBooker.Outcome.BOOKED, NotificationAutoBooker.book(context, candidate))
        LedgerStore(context).use { ledger ->
            assertEquals("星巴克", ledger.queryVisibleEntries().single().title)
        }
    }

    @Test
    fun testDoesNothingWhileTheSourceHasNotOptedIn() {
        val candidate = record("已支付¥16.00")
        assertEquals(NotificationAutoBooker.Outcome.DISABLED, NotificationAutoBooker.book(context, candidate))
        LedgerStore(context).use { assertTrue(it.queryVisibleEntries().isEmpty()) }
        assertNull(notificationStore.records().single().autoBookedEntryUuid)
    }

    @Test
    fun testLeavesTransfersAsSuggestions() {
        enableAutoBook()
        val candidate = record("你向张三转账 ¥50.00")
        assertEquals(NotificationAutoBooker.Outcome.TRANSFER, NotificationAutoBooker.book(context, candidate))
        LedgerStore(context).use { assertTrue(it.queryVisibleEntries().isEmpty()) }
    }

    @Test
    fun testChatLikeNotificationsStaySuggestions() {
        enableAutoBook()
        val candidate = record("已支付¥100.00", title = "张三")
        assertEquals(
            NotificationAutoBooker.Outcome.LOW_CONFIDENCE,
            NotificationAutoBooker.book(context, candidate),
        )
        LedgerStore(context).use { assertTrue(it.queryVisibleEntries().isEmpty()) }
    }

    @Test
    fun testKeepsPossibleDuplicatesAsSuggestions() {
        enableAutoBook()
        val now = System.currentTimeMillis()
        LedgerStore(context).use { ledger ->
            ledger.upsert(
                LedgerEntry(
                    title = "手动记的一笔",
                    amountCents = 1_600L,
                    type = LedgerEntryType.EXPENSE,
                    occurredAtMillis = now,
                ),
            )
        }
        val candidate = record("已支付¥16.00")
        assertEquals(
            NotificationAutoBooker.Outcome.DUPLICATE,
            NotificationAutoBooker.book(context, candidate),
        )
        LedgerStore(context).use { assertEquals(1, it.queryVisibleEntries().size) }
    }

    @Test
    fun testRepostedNotificationIsNeverBookedTwice() {
        enableAutoBook()
        val first = record("已支付¥16.00")
        assertEquals(NotificationAutoBooker.Outcome.BOOKED, NotificationAutoBooker.book(context, first))
        val second = record("已支付¥16.00")
        assertEquals(
            NotificationAutoBooker.Outcome.ALREADY_BOOKED,
            NotificationAutoBooker.book(context, second),
        )
        LedgerStore(context).use { assertEquals(1, it.queryVisibleEntries().size) }
    }

    @Test
    fun testReusedNotificationKeyBooksALaterDifferentPayment() {
        enableAutoBook()
        val now = System.currentTimeMillis()
        val first = record("已支付¥16.00", key = "reused", atMillis = now)
        assertEquals(NotificationAutoBooker.Outcome.BOOKED, NotificationAutoBooker.book(context, first))

        val second = record("已支付¥28.00", key = "reused", atMillis = now + 60_000L)
        assertNull(second.autoBookedEntryUuid)
        assertEquals(NotificationAutoBooker.Outcome.BOOKED, NotificationAutoBooker.book(context, second))
        LedgerStore(context).use { ledger ->
            assertEquals(listOf(1_600L, 2_800L), ledger.queryVisibleEntries().map { it.amountCents }.sorted())
        }
    }

    @Test
    fun testUndoRemovesTheEntryAndTheMarker() {
        enableAutoBook()
        val candidate = record("已支付¥16.00")
        assertEquals(NotificationAutoBooker.Outcome.BOOKED, NotificationAutoBooker.book(context, candidate))
        val entryUuid = notificationStore.records().single().autoBookedEntryUuid!!
        assertTrue(NotificationAutoBooker.undo(context, candidate.id, entryUuid))
        LedgerStore(context).use { assertTrue(it.queryVisibleEntries().isEmpty()) }
        assertNull(notificationStore.records().single().autoBookedEntryUuid)
        assertTrue(notificationStore.candidates().single().autoBookedEntryUuid == null)
    }
}
