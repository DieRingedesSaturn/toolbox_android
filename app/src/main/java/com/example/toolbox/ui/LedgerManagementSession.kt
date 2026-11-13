package com.example.toolbox.ui

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.toolbox.ledger.LedgerAccount
import com.example.toolbox.ledger.LedgerAccounts
import com.example.toolbox.ledger.LedgerBudgetStore
import com.example.toolbox.ledger.LedgerBudgets
import com.example.toolbox.ledger.LedgerCategory
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerEntryType
import com.example.toolbox.ledger.LedgerStore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tags, account maintenance, budgets and selection operations for the ledger workspace. */
internal class LedgerManagementSession(
    private val context: Context,
    private val store: LedgerStore,
    private val scope: CoroutineScope,
    private val strings: ToolboxStrings,
    private val snapshot: () -> LedgerSnapshot,
    initialOrder: List<LedgerOverviewCard>,
    private val onChanged: () -> Unit,
) {
    private val preferences by lazy { AppPreferences(context) }
    private val budgetStore by lazy { LedgerBudgetStore(context) }
    private val entries get() = snapshot().entries
    private val allEntries get() = snapshot().allEntries
    private val tags get() = snapshot().tags
    private val accounts get() = snapshot().accounts
    var overviewOrder by mutableStateOf(initialOrder)
    var showOverviewOrderDialog by mutableStateOf(false)
    var detailAccount by mutableStateOf<LedgerAccount?>(null)
    var selectedEntryUuids by mutableStateOf<Set<String>>(emptySet())
    var bulkTagPickAdd by mutableStateOf(false)
    var bulkTagPickRemove by mutableStateOf(false)
    var tagsDialogEntryNav by mutableStateOf(true)
    var showTagsDialog by mutableStateOf(false)
    var budgets by mutableStateOf(LedgerBudgets())
    var showBudgetDialog by mutableStateOf(false)

    suspend fun initialize() { budgets = withContext(Dispatchers.IO) { budgetStore.load() } }

    @Composable
    fun Host(onOpenEntry: (LedgerEntry) -> Unit, onCopyEntry: (LedgerEntry) -> Unit) {
        if (showBudgetDialog) {
            LedgerBudgetDialog(
                strings = strings,
                budgets = budgets,
                tags = tags.filter { it.deletedAtMillis == null },
                onSave = { updated ->
                    budgets = updated
                    showBudgetDialog = false
                    scope.launch(Dispatchers.IO) { budgetStore.save(updated) }
                },
                onDismiss = { showBudgetDialog = false },
            )
        }

        if (showOverviewOrderDialog) {
            LedgerOverviewOrderDialog(
                strings = strings,
                order = overviewOrder,
                onMove = { index, offset ->
                    val target = index + offset
                    if (target in overviewOrder.indices) {
                        val next = overviewOrder.toMutableList()
                        next.add(target, next.removeAt(index))
                        overviewOrder = next
                        scope.launch(Dispatchers.IO) {
                            preferences.saveLedgerOverviewOrder(next)
                        }
                    }
                },
                onReset = {
                    overviewOrder = LedgerOverviewOrder.DEFAULT
                    scope.launch(Dispatchers.IO) {
                        preferences.saveLedgerOverviewOrder(LedgerOverviewOrder.DEFAULT)
                    }
                },
                onDismiss = { showOverviewOrderDialog = false },
            )
        }

        // Back clears an active multi-select before the screen reacts.
        if (selectedEntryUuids.isNotEmpty()) {
            BackHandler { selectedEntryUuids = emptySet() }
        }

        // Bulk-tag pickers for the transactions selection mode.
        if (bulkTagPickAdd || bulkTagPickRemove) {
            val pickerTags = tags
                .filter { it.deletedAtMillis == null }
                .sortedWith(compareBy({ it.sortOrder }, { it.name }))
            val candidateUuids = if (bulkTagPickAdd) {
                pickerTags.map { it.uuid }.toSet()
            } else {
                // Removing offers only live tags present on the selection.
                entries.filter { it.uuid in selectedEntryUuids }
                    .flatMapTo(HashSet()) { it.tagUuids }
                    .filterTo(HashSet()) { uuid ->
                        pickerTags.any { it.uuid == uuid }
                    }
            }
            AlertDialog(
                onDismissRequest = {
                    bulkTagPickAdd = false
                    bulkTagPickRemove = false
                },
                title = {
                    Text(
                        if (bulkTagPickAdd) {
                            strings.addTagsAction
                        } else {
                            strings.removeTagsAction
                        },
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        pickerTags
                            .filter { it.uuid in candidateUuids }
                            .forEach { tag ->
                                Text(
                                    text = "${tag.emoji.ifBlank { "🏷" }} ${tag.name}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val isAdd = bulkTagPickAdd
                                            val uuids = selectedEntryUuids
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    store.updateEntryTags(
                                                        uuids,
                                                        add = if (isAdd) tag.uuid else null,
                                                        remove = if (isAdd) null else tag.uuid,
                                                    )
                                                }
                                                onChanged()
                                            }
                                            bulkTagPickAdd = false
                                            bulkTagPickRemove = false
                                            selectedEntryUuids = emptySet()
                                        }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = {
                            bulkTagPickAdd = false
                            bulkTagPickRemove = false
                        },
                    ) {
                        Text(strings.cancelAction)
                    }
                },
            )
        }

        if (showTagsDialog) {
            LedgerTagsDialog(
                strings = strings,
                tags = tags,
                entries = entries,
                accounts = accounts.filter { it.deletedAtMillis == null },
                onUpsert = { tag ->
                    scope.launch {
                        withContext(Dispatchers.IO) { store.upsertTag(tag) }
                        onChanged()
                    }
                },
                onDelete = { tag ->
                    scope.launch {
                        withContext(Dispatchers.IO) { store.softDeleteTag(tag.uuid) }
                        onChanged()
                    }
                },
                onReorder = { ordered ->
                    scope.launch {
                        withContext(Dispatchers.IO) { store.reorderTags(ordered) }
                        onChanged()
                    }
                },
                onMerge = { from, to ->
                    scope.launch {
                        withContext(Dispatchers.IO) { store.mergeTag(from, to) }
                        onChanged()
                    }
                },
                onOpenEntry = if (tagsDialogEntryNav) { entry ->
                    showTagsDialog = false
                    onOpenEntry(entry)
                } else {
                    null
                },
                onDismiss = { showTagsDialog = false },
            )
        }

        detailAccount?.let { account ->
            LedgerAccountDetail(
                strings = strings,
                account = account,
                entries = allEntries,
                tags = tags,
                accounts = accounts.filter { it.deletedAtMillis == null },
                onReconcile = { acc, actualCents ->
                    scope.launch {
                        val diff = withContext(Dispatchers.IO) {
                            val all = store.queryAllEntriesForSync()
                            actualCents - LedgerAccounts.balance(acc, all)
                        }
                        if (diff == 0L) {
                            Toast.makeText(
                                context,
                                strings.balanceMatchesToast,
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            withContext(Dispatchers.IO) {
                                store.upsert(
                                    LedgerEntry(
                                        uuid = UUID.randomUUID().toString(),
                                        title = strings.adjustmentTitleText,
                                        amountCents = diff,
                                        currency = acc.currency,
                                        fxRateToCny = 1.0,
                                        type = LedgerEntryType.ADJUSTMENT,
                                        category = LedgerCategory.OTHER.name,
                                        occurredAtMillis = System.currentTimeMillis(),
                                        note = "",
                                        accountUuid = acc.uuid,
                                    ),
                                )
                            }
                            Toast.makeText(
                                context,
                                strings.adjustmentTitleText,
                                Toast.LENGTH_SHORT,
                            ).show()
                            onChanged()
                        }
                    }
                },
                onSave = { updated ->
                    scope.launch {
                        withContext(Dispatchers.IO) { store.upsertAccount(updated) }
                        detailAccount = updated
                        onChanged()
                    }
                },
                onArchive = { acc, archived ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            store.upsertAccount(acc.copy(isArchived = archived))
                        }
                        detailAccount = null
                        onChanged()
                    }
                },
                onDelete = { acc ->
                    scope.launch {
                        withContext(Dispatchers.IO) { store.softDeleteAccount(acc.uuid) }
                        detailAccount = null
                        onChanged()
                    }
                },
                onOpenEntry = onOpenEntry,
                onCopyEntry = onCopyEntry,
                onDismiss = { detailAccount = null },
            )
        }

    }
}
