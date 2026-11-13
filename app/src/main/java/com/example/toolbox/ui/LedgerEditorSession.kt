package com.example.toolbox.ui

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.example.toolbox.ledger.LedgerAccounts
import com.example.toolbox.ledger.LedgerDuplicateMatcher
import com.example.toolbox.ledger.NotificationBookkeeping
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerEntryType
import com.example.toolbox.ledger.LedgerQuickPicks
import com.example.toolbox.ledger.LedgerStore
import com.example.toolbox.ledger.NotificationLedgerCandidate
import com.example.toolbox.ledger.NotificationLedgerDefaults
import com.example.toolbox.ledger.NotificationLedgerStore
import com.example.toolbox.ledger.NotificationPayment
import com.example.toolbox.ledger.NotificationPaymentDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val notificationDraftSaver = listSaver<Pair<NotificationLedgerCandidate, Long>?, Any>(
    save = { draft ->
        draft?.let {
            listOf(
                it.first.id,
                it.first.packageName,
                it.first.occurredAtMillis,
                it.second,
                it.first.amountsCents.joinToString(","),
                it.first.payment?.direction?.name ?: "",
                it.first.payment?.amountCents ?: 0L,
                it.first.payment?.label ?: "",
                it.first.payment?.confident ?: false,
            )
        } ?: emptyList()
    },
    restore = { values ->
        if (values.size != 9) null else {
            val amount = values[3] as Long
            val payment = (values[5] as String).takeIf { it.isNotEmpty() }?.let { name ->
                runCatching {
                    NotificationPayment(
                        direction = NotificationPaymentDirection.valueOf(name),
                        amountCents = values[6] as Long,
                        label = (values[7] as String).takeIf { it.isNotEmpty() },
                        confident = values[8] as Boolean,
                    )
                }.getOrNull()
            }
            NotificationLedgerCandidate(
                id = values[0] as String,
                packageName = values[1] as String,
                occurredAtMillis = values[2] as Long,
                amountsCents = (values[4] as String).split(',').mapNotNull { it.toLongOrNull() },
                payment = payment,
            ) to amount
        }
    },
)

/** Entry editing and the optional notification draft share one screen-scoped save session. */
internal class LedgerEditorSession(
    private val context: Context,
    private val store: LedgerStore,
    private val scope: CoroutineScope,
    private val strings: ToolboxStrings,
    private val snapshot: () -> LedgerSnapshot,
    private val loaded: () -> Boolean,
    private val onChanged: () -> Unit,
    private val onManageTags: () -> Unit,
) {
    var editingUuid by mutableStateOf<String?>(null)
    var pendingDeleteUuid by mutableStateOf<String?>(null)
    var showEditor by mutableStateOf(false)
    var showNotificationLedgerDialog by mutableStateOf(false)
    private var notificationDraft by mutableStateOf<Pair<NotificationLedgerCandidate, Long>?>(null)
    private var notificationDefaults by mutableStateOf<NotificationLedgerDefaults?>(null)
    private var notificationLastAccount by mutableStateOf<String?>(null)
    private var entrySaveBusy by mutableStateOf(false)
    private var pendingDuplicateEntry by mutableStateOf<LedgerEntry?>(null)
    private var duplicateMatches by mutableStateOf<List<LedgerEntry>>(emptyList())
    private val entries get() = snapshot().entries
    private val allEntries get() = snapshot().allEntries
    private val tags get() = snapshot().tags
    private val accounts get() = snapshot().accounts
    private val entriesLoaded get() = loaded()
    val editingEntry get() = entries.firstOrNull { it.uuid == editingUuid }
    val pendingDeleteEntry get() = entries.firstOrNull { it.uuid == pendingDeleteUuid }

    fun openEditor(entry: LedgerEntry?) {
        editingUuid = entry?.uuid
        notificationDraft = null
        showEditor = true
    }

    fun copyEntry(entry: LedgerEntry) {
        copyToClipboard(
            context = context,
            label = entry.title,
            value = ledgerEntryLine(
                strings,
                entry,
                entry.tagUuids.mapNotNull { uuid ->
                    tags.firstOrNull {
                        it.uuid == uuid && it.deletedAtMillis == null
                    }?.name
                },
                accounts.associate { it.uuid to it.name },
            ),
            copiedMessage = strings.copied(entry.title),
        )
    }

    fun persistEntry(
        newEntry: LedgerEntry,
        candidate: NotificationLedgerCandidate? = null,
    ) {
        entrySaveBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    store.upsert(newEntry)
                    newEntry.accountUuid?.let { AppPreferences(context).saveLedgerLastAccount(it) }
                    if (NotificationBookkeeping.ENABLED) {
                        candidate?.let {
                            NotificationLedgerStore(context).removeCandidate(
                                it.id,
                                it.occurredAtMillis,
                                it.amountsCents,
                            )
                        }
                    }
                }
            }
            entrySaveBusy = false
            if (result.isSuccess) {
                pendingDuplicateEntry = null
                duplicateMatches = emptyList()
                showEditor = false
                editingUuid = null
                notificationDraft = null
                onChanged()
            } else {
                Toast.makeText(context, strings.ledgerSaveFailed, Toast.LENGTH_LONG).show()
            }
        }
    }

    @Composable
    fun Host() {
        val nowMillis = remember(entries) { System.currentTimeMillis() }
        val quickPicks = remember(entries) { LedgerQuickPicks.from(entries, nowMillis) }
        LaunchedEffect(notificationDraft) {
            if (!NotificationBookkeeping.ENABLED) return@LaunchedEffect
            notificationDefaults = null
            notificationDraft?.first?.let { candidate ->
                val loaded = withContext(Dispatchers.IO) {
                    NotificationLedgerStore(context).defaultsFor(candidate.packageName) to
                        AppPreferences(context).ledgerLastAccount()
                }
                notificationLastAccount = loaded.second
                notificationDefaults = loaded.first
            }
        }

        if (showEditor && (editingUuid == null || editingEntry != null) &&
            (notificationDraft == null || (notificationDefaults != null && entriesLoaded))
        ) {
            key(editingUuid ?: notificationDraft?.let { "${it.first.id}:${it.second}" } ?: "new") {
                LedgerEntryEditor(
                    strings = strings,
                    initialEntry = editingEntry,
                    draftAmountCents = notificationDraft?.second,
                    draftOccurredAtMillis = notificationDraft?.first?.occurredAtMillis,
                    draftTitle = notificationDraft?.first?.payment?.label,
                    draftDefaults = notificationDefaults?.takeIf { notificationDraft != null }
                        ?.resolve(accounts, tags)
                        ?.let { resolved ->
                            when (notificationDraft?.first?.payment?.direction) {
                                NotificationPaymentDirection.EXPENSE ->
                                    resolved.copy(type = LedgerEntryType.EXPENSE)
                                NotificationPaymentDirection.INCOME ->
                                    resolved.copy(type = LedgerEntryType.INCOME)
                                else -> resolved
                            }
                        },
                    saveBusy = entrySaveBusy,
                    tags = tags,
                    entries = entries,
                    accounts = accounts,
                    defaultAccountUuid = if (notificationDraft != null) {
                        notificationDefaults?.accountFor(accounts, notificationLastAccount)
                            ?: LedgerAccounts.DEFAULT_ACCOUNT_UUID
                    } else {
                        AppPreferences(context).ledgerLastAccount()
                            .ifBlank { LedgerAccounts.DEFAULT_ACCOUNT_UUID }
                    },
                    quickPicks = quickPicks,
                    parentTitle = editingEntry?.parentUuid?.let { parent ->
                        allEntries.firstOrNull { it.uuid == parent }?.title
                    },
                    onCreateTag = { tag ->
                        scope.launch {
                            withContext(Dispatchers.IO) { store.upsertTag(tag) }
                            onChanged()
                        }
                    },
                    // From inside the editor: manage tags without entry nav.
                    onManageTags = onManageTags,
                    onDismiss = {
                        if (!entrySaveBusy) {
                            showEditor = false
                            editingUuid = null
                            notificationDraft = null
                        }
                    },
                    onSave = { newEntry ->
                        if (entrySaveBusy) return@LedgerEntryEditor
                        val candidate = notificationDraft?.first
                        if (!NotificationBookkeeping.ENABLED || candidate == null) {
                            persistEntry(newEntry)
                            return@LedgerEntryEditor
                        }
                        entrySaveBusy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    LedgerDuplicateMatcher.find(newEntry, store.queryVisibleEntries())
                                }
                            }
                            val matches = result.getOrNull()
                            when {
                                matches == null -> {
                                    entrySaveBusy = false
                                    Toast.makeText(context, strings.ledgerSaveFailed, Toast.LENGTH_LONG).show()
                                }
                                matches.isEmpty() -> persistEntry(newEntry, candidate)
                                else -> {
                                    entrySaveBusy = false
                                    duplicateMatches = matches
                                    pendingDuplicateEntry = newEntry
                                }
                            }
                        }
                    },
                    onDelete = { entry ->
                        if (!entrySaveBusy) pendingDeleteUuid = entry.uuid
                    },
                )
            }
        }

        if (NotificationBookkeeping.ENABLED && showNotificationLedgerDialog && entriesLoaded) {
            NotificationLedgerDialog(
                strings = strings,
                accounts = accounts,
                tags = tags,
                onSelectAmount = { candidate, amount ->
                    showNotificationLedgerDialog = false
                    editingUuid = null
                    notificationDefaults = null
                    notificationDraft = candidate to amount
                    showEditor = true
                },
                onDismiss = { showNotificationLedgerDialog = false },
            )
        }

        pendingDuplicateEntry?.let { pending ->
            NotificationLedgerDuplicateDialog(
                strings = strings,
                matches = duplicateMatches,
                accounts = accounts,
                tags = tags,
                busy = entrySaveBusy,
                onSaveAnyway = {
                    if (!entrySaveBusy) persistEntry(pending, notificationDraft?.first)
                },
                onDismiss = {
                    if (!entrySaveBusy) {
                        pendingDuplicateEntry = null
                        duplicateMatches = emptyList()
                    }
                },
                onAlreadyRecorded = {
                    if (!entrySaveBusy) {
                        val candidate = notificationDraft?.first
                        entrySaveBusy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    candidate?.let {
                                        NotificationLedgerStore(context).removeCandidate(
                                            it.id,
                                            it.occurredAtMillis,
                                            it.amountsCents,
                                        )
                                    }
                                }
                            }
                            entrySaveBusy = false
                            if (result.isSuccess) {
                                pendingDuplicateEntry = null
                                showEditor = false
                                editingUuid = null
                                notificationDraft = null
                            } else {
                                Toast.makeText(context, strings.ledgerSaveFailed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
            )
        }

        pendingDeleteEntry?.let { entry ->
            AlertDialog(
                onDismissRequest = { pendingDeleteUuid = null },
                title = { Text(entry.title) },
                text = { Text(strings.deleteEntryConfirm) },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingDeleteUuid = null
                            showEditor = false
                            editingUuid = null
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    store.softDelete(entry.uuid)
                                }
                                onChanged()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(strings.deleteAction)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteUuid = null }) {
                        Text(strings.cancelAction)
                    }
                },
            )
        }

    }

    companion object {
        fun saver(
            context: Context,
            store: LedgerStore,
            scope: CoroutineScope,
            strings: ToolboxStrings,
            snapshot: () -> LedgerSnapshot,
            loaded: () -> Boolean,
            onChanged: () -> Unit,
            onManageTags: () -> Unit,
        ) = listSaver<LedgerEditorSession, Any>(
            save = { session ->
                listOf(
                    session.editingUuid.orEmpty(), session.pendingDeleteUuid.orEmpty(),
                    session.showEditor, session.showNotificationLedgerDialog,
                    with(notificationDraftSaver) { save(session.notificationDraft) } ?: emptyList<Any>(),
                )
            },
            restore = { values ->
                LedgerEditorSession(context, store, scope, strings, snapshot, loaded, onChanged, onManageTags).apply {
                    editingUuid = (values[0] as String).ifBlank { null }
                    pendingDeleteUuid = (values[1] as String).ifBlank { null }
                    showEditor = values[2] as Boolean
                    showNotificationLedgerDialog = values[3] as Boolean
                    notificationDraft = notificationDraftSaver.restore(values[4])
                }
            },
        )
    }
}
