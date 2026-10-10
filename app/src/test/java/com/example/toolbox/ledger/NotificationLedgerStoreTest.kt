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
class NotificationLedgerStoreTest {
    private lateinit var context: Context
    private lateinit var store: NotificationLedgerStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("notification_ledger", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = NotificationLedgerStore(context)
        store.setEnabled(true)
        store.setAllowedPackages(setOf("com.pay"))
    }

    private fun record(
        key: String,
        title: String = "招商银行",
        text: String = "您尾号1234的储蓄卡消费25.00元",
        atMillis: Long = System.currentTimeMillis(),
    ) = store.recordParts("com.pay", key, atMillis, title, text)

    @Test
    fun keepsAmountsAndOriginalTextForCandidates() {
        record("k1")
        val candidate = store.candidates().single()
        assertEquals(listOf(2_500L), candidate.amountsCents)
        assertEquals("招商银行", candidate.title)
        assertEquals("您尾号1234的储蓄卡消费25.00元", candidate.text)
        assertNull(candidate.skipReason)
        assertEquals(1, store.records().size)
    }

    @Test
    fun keepsSkippedNotificationsWithTheirReasonAndText() {
        store.recordParts("com.pay", "k1", System.currentTimeMillis(), "天气", "今天晴，25 度")
        assertTrue(store.candidates().isEmpty())
        val record = store.records().single()
        assertEquals(NotificationSkipReason.NO_AMOUNT, record.skipReason)
        assertTrue(record.amountsCents.isEmpty())
        assertEquals("今天晴，25 度", record.text)
    }

    @Test
    fun marksVerificationNoticesAndEmptyOnes() {
        store.recordParts("com.pay", "k1", System.currentTimeMillis(), "银行", "验证码 482913")
        store.recordParts("com.pay", "k2", System.currentTimeMillis() - 30_000L, "", "")
        val reasons = store.records().map { it.skipReason }
        assertEquals(
            listOf(NotificationSkipReason.VERIFICATION_CODE, NotificationSkipReason.EMPTY_TEXT),
            reasons,
        )
    }

    @Test
    fun ignoresUnselectedSourcesAndDeduplicatesByKey() {
        store.recordParts("com.other", "k1", System.currentTimeMillis(), "x", "消费 ¥1")
        assertTrue(store.records().isEmpty())
        record("k1")
        record("k1")
        assertEquals(1, store.records().size)
    }

    @Test
    fun capsSuggestionsAndIgnoredRecordsSeparately() {
        repeat(35) { store.recordParts("com.pay", "c$it", System.currentTimeMillis() - 60_000L + it, "t", "消费 ¥1.00") }
        repeat(20) { store.recordParts("com.pay", "i$it", System.currentTimeMillis() - 60_000L + it, "t", "无金额") }
        assertEquals(30, store.candidates().size)
        assertEquals(15, store.records().count { it.amountsCents.isEmpty() })
    }

    @Test
    fun removeAndClearCoverWholeHistory() {
        record("k1")
        store.recordParts("com.pay", "k2", System.currentTimeMillis() - 30_000L, "天气", "无金额")
        store.removeCandidate(store.records().first { it.amountsCents.isEmpty() }.id)
        assertEquals(1, store.records().size)
        store.clearCandidates()
        assertTrue(store.records().isEmpty())
    }

    @Test
    fun prunesStaleRecordsAndDeselectedSources() {
        record("k1", atMillis = 0L)
        assertTrue(store.records().isEmpty())
        record("k2")
        store.setAllowedPackages(emptySet())
        assertTrue(store.records().isEmpty())
    }
}
