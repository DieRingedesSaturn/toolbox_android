package com.example.toolbox.ui

import androidx.compose.runtime.saveable.SaverScope
import com.example.toolbox.ledger.BillingCycle
import com.example.toolbox.ledger.CostTrackingMode
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerStore
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LedgerEditorStateTest {
    private val saveScope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = when (value) {
            is String, is Boolean, is Int, is Long -> true
            is List<*> -> value.all { it != null && canBeSaved(it) }
            else -> false
        }
    }

    @Test
    fun restoredEditorKeepsItsIdentityAndUsesTheFreshSnapshot() {
        val context = RuntimeEnvironment.getApplication()
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val store = LedgerStore(context)
        val entry = LedgerEntry(uuid = "entry", title = "Before", amountCents = 100L, occurredAtMillis = 100L)
        var snapshot = LedgerSnapshot(listOf(entry), listOf(entry), emptyList(), emptyList(), null)
        try {
            val strings = ToolboxStrings(AppLanguage.ENGLISH)
            val session = LedgerEditorSession(context, store, scope, strings, { snapshot }, { true }, {}, {})
            session.openEditor(entry)
            session.pendingDeleteUuid = entry.uuid
            val saver = LedgerEditorSession.saver(context, store, scope, strings, { snapshot }, { true }, {}, {})
            val saved = with(saver) { saveScope.save(session) }!!
            assertTrue(saveScope.canBeSaved(saved))
            val updated = entry.copy(title = "After reload")
            snapshot = snapshot.copy(entries = listOf(updated), allEntries = listOf(updated))
            val restored = saver.restore(saved)!!
            assertTrue(restored.showEditor)
            assertEquals("entry", restored.editingUuid)
            assertEquals("After reload", restored.editingEntry?.title)
            assertEquals("entry", restored.pendingDeleteUuid)
        } finally {
            scope.cancel()
            store.close()
        }
    }

    @Test
    fun costDraftRestoresUnsavedCycleAndEndDate() {
        val draft = LedgerCostDraft(null, ZoneId.of("UTC")).apply {
            trackCost = true
            selectedCostMode = CostTrackingMode.PERIODIC_SUBSCRIPTION
            billingCycle = BillingCycle.YEARLY
            costEndsEpochDay = 25000L
            showCostEndPicker = true
        }
        val saved = with(LedgerCostDraft.Saver) { saveScope.save(draft) }!!
        val restored = LedgerCostDraft.Saver.restore(saved)!!
        assertTrue(restored.trackCost)
        assertEquals(CostTrackingMode.PERIODIC_SUBSCRIPTION, restored.selectedCostMode)
        assertEquals(BillingCycle.YEARLY, restored.billingCycle)
        assertEquals(25000L, restored.costEndsEpochDay)
        assertTrue(restored.showCostEndPicker)
    }
}
