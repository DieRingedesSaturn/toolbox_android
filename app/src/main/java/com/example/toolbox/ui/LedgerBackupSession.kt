package com.example.toolbox.ui

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.toolbox.ledger.BackupException
import com.example.toolbox.ledger.BackupFailure
import com.example.toolbox.totp.TotpStore
import com.example.toolbox.ledger.CostTrackingMode
import com.example.toolbox.ledger.LedgerBackup
import com.example.toolbox.ledger.LedgerBackupVault
import com.example.toolbox.ledger.BackupRecoveryStore
import com.example.toolbox.ledger.LedgerBackupStatusStore
import com.example.toolbox.ledger.LedgerCsv
import com.example.toolbox.ledger.LedgerDbSnapshots
import com.example.toolbox.ledger.LedgerEntryType
import com.example.toolbox.ledger.LedgerStore
import com.example.toolbox.ledger.LedgerSyncPayload
import com.example.toolbox.ledger.LedgerSyncSerializer
import com.example.toolbox.ledger.BackupTool
import com.example.toolbox.totp.TotpVaultException
import com.example.toolbox.ledger.ReplacePlan
import com.example.toolbox.ledger.LedgerWebDavSync
import com.example.toolbox.ledger.WebDavConfigStore
import com.example.toolbox.ledger.WebDavException
import com.example.toolbox.ledger.WebDavFailure
import com.example.toolbox.ledger.WebDavServiceProfile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class WebDavBusyAction { SYNC }

/** Screen-owned backup/import session; no permanent scope or background work. */
internal class LedgerBackupSession(
    private val context: Context,
    private val store: LedgerStore,
    private val scope: CoroutineScope,
    val strings: ToolboxStrings,
    private val onChanged: () -> Unit,
    private val onOpenSettings: () -> Unit,
) {
    var pendingImport by mutableStateOf<PendingImport?>(null)
    var pendingReplace by mutableStateOf<PendingReplace?>(null)
    var ledgerPassphrase by mutableStateOf("")
    private var passphraseRevision = 0
    var pendingBackupAction by mutableStateOf<((String) -> Unit)?>(null)
    var showBackupHistory by mutableStateOf(false)
    private val webDavStore by lazy { WebDavConfigStore(context) }
    var webDavConfigVersion by mutableIntStateOf(0)
    var webDavTargets by mutableStateOf<List<WebDavServiceProfile>>(emptyList())
    var webDavLastSyncAt by mutableStateOf<Long?>(null)
    var webDavStatusLoaded by mutableStateOf(false)
    val backupStatusStore by lazy { LedgerBackupStatusStore(context) }
    var lastFileExportAt by mutableStateOf<Long?>(null)
    var backupSnoozedUntil by mutableStateOf<Long?>(null)
    var backupStatusLoaded by mutableStateOf(false)
    var webDavBusyAction by mutableStateOf<WebDavBusyAction?>(null)
    var webDavLastResult by mutableStateOf<String?>(null)

    var snapshots by mutableStateOf<List<LedgerDbSnapshots.Snapshot>?>(null)
    var selectedSnapshot by mutableStateOf<LedgerDbSnapshots.Snapshot?>(null)
    var restoreBusy by mutableStateOf(false)
    private val snapshotDir get() = File(context.noBackupFilesDir, LedgerDbSnapshots.DIR_NAME)
    val snapshotTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    suspend fun loadSnapshots() {
        selectedSnapshot = null
        snapshots = null
        snapshots = withContext(Dispatchers.IO) { LedgerDbSnapshots.list(snapshotDir) }
    }

    fun restoreSnapshot(snapshot: LedgerDbSnapshots.Snapshot, close: () -> Unit) {
        if (restoreBusy) return
        restoreBusy = true
        scope.launch {
            val restored = withContext(Dispatchers.IO) {
                runCatching {
                    store.restoreSnapshot(snapshot.file)
                }.isSuccess
            }
            restoreBusy = false
            if (restored) {
                selectedSnapshot = null
                close()
                Toast.makeText(
                    context,
                    strings.snapshotRestored,
                    Toast.LENGTH_LONG,
                ).show()
                onChanged()
            } else {
                Toast.makeText(
                    context,
                    strings.snapshotRestoreFailed,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    suspend fun initialize() {
        val revision = passphraseRevision
        val saved = withContext(Dispatchers.IO) { TotpStore(context).savedDefaultBackupPassphrase().orEmpty() }
        if (revision == passphraseRevision) ledgerPassphrase = saved
        val status = withContext(Dispatchers.IO) {
            backupStatusStore.lastFileExportAtMillis() to backupStatusStore.snoozedUntilMillis()
        }
        lastFileExportAt = status.first
        backupSnoozedUntil = status.second
        backupStatusLoaded = true
    }

    suspend fun reloadStatus() {
        val status = withContext(Dispatchers.IO) {
            webDavStore.selectedServices(BackupTool.LEDGER) to webDavStore.lastSyncAtMillis()
        }
        webDavTargets = status.first
        webDavLastSyncAt = status.second
        webDavStatusLoaded = true
    }

    fun confirmPassphrase(pass: String, action: (String) -> Unit) {
        passphraseRevision++
        pendingBackupAction = null
        scope.launch {
            withContext(Dispatchers.IO) { TotpStore(context).saveDefaultBackupPassphrase(pass) }
            ledgerPassphrase = pass
            action(pass)
        }
    }

    fun restored() { showBackupHistory = false; onChanged() }

    fun webDavErrorMessage(error: Throwable): String {
        if (error is TotpVaultException || error.cause is TotpVaultException) return strings.backupDecryptFailed
        val webDavError = error as? WebDavException
        return strings.webDavError(
            webDavError?.failure ?: WebDavFailure.NETWORK,
            webDavError?.httpCode,
        )
    }

    fun runSelectedWebDavSync(toastResult: Boolean) {
        if (ledgerPassphrase.isEmpty()) {
            pendingBackupAction = { pass ->
                ledgerPassphrase = pass
                runSelectedWebDavSync(toastResult)
            }
            return
        }
        val targets = webDavTargets.filter { it.isComplete }
        if (targets.isEmpty()) {
            Toast.makeText(context, strings.webDavNoTargetSelectedToast, Toast.LENGTH_LONG).show()
            onOpenSettings()
            return
        }
        if (webDavBusyAction != null) return
        webDavBusyAction = WebDavBusyAction.SYNC
        webDavLastResult = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                var okCount = 0
                val messages = mutableListOf<String>()
                val now = System.currentTimeMillis()
                val results = LedgerWebDavSync.syncTargets(
                    services = targets,
                    passphrase = ledgerPassphrase,
                    loadLocal = { LedgerSyncPayload(
                        entries = store.queryAllEntriesForSync(),
                        tags = store.queryTags(includeDeleted = true),
                        accounts = store.queryAccounts(includeDeleted = true),
                    ) },
                    applyMerged = { store.importSyncPayload(it) },
                    nowMillis = now,
                )
                for ((service, res) in targets.zip(results)) {
                    res.onSuccess { r ->
                        okCount++
                        messages += "${service.name}: " + strings.webDavSyncSuccess(
                            total = r.totalEntries,
                            pulled = r.pulledCount,
                            pushed = r.pushedCount,
                            snapshotName = r.snapshotName,
                        )
                    }.onFailure { err ->
                        messages += "${service.name}: ${webDavErrorMessage(err)}"
                    }
                }
                if (okCount > 0) {
                    webDavStore.saveLastSync(now, BackupTool.LEDGER)
                }
                Triple(okCount, targets.size, messages.joinToString("\n"))
            }
            webDavConfigVersion++
            webDavBusyAction = null
            val summaryMsg = if (outcome.second == 1) {
                outcome.third
            } else {
                strings.webDavMultiSyncSummary(
                    outcome.first,
                    outcome.second,
                    outcome.third,
                )
            }
            webDavLastResult = summaryMsg
            if (outcome.first > 0) onChanged()
            if (toastResult) {
                Toast.makeText(context, summaryMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun backupFileName(pattern: String, suffix: String): String =
        "toolbox-ledger-" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern)) +
            suffix

    fun writeTextToUri(uri: Uri, text: String) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("openOutputStream returned null")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    fun exportJsonBackup(uri: Uri) {
        if (ledgerPassphrase.isEmpty()) {
            pendingBackupAction = { pass -> ledgerPassphrase = pass; exportJsonBackup(uri) }
            return
        }
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val exportedAt = System.currentTimeMillis()
                    val allRows = store.queryAllEntriesForSync()
                    writeTextToUri(
                        uri,
                        LedgerBackupVault(ledgerPassphrase).encode(LedgerSyncSerializer.toJsonString(
                            LedgerSyncPayload(
                                entries = allRows,
                                tags = store.queryTags(includeDeleted = true),
                                accounts = store.queryAccounts(includeDeleted = true),
                            ),
                        )),
                    )
                    backupStatusStore.saveFileExport(exportedAt)
                    exportedAt to allRows.count { it.deletedAtMillis == null }
                }
            }
            outcome.onSuccess { lastFileExportAt = it.first }
            Toast.makeText(
                context,
                outcome.fold(
                    onSuccess = { strings.backupExported(it.second) },
                    onFailure = { strings.backupExportFailed },
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun exportCsvBackup(uri: Uri) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val visible = store.queryVisibleEntries()
                    val liveTagNames = store.queryTags(includeDeleted = false)
                        .associate { it.uuid to it.name }
                    val accountNames = store.queryAccounts(includeDeleted = false)
                        .associate { it.uuid to it.name }
                    writeTextToUri(
                        uri,
                        LedgerCsv.build(
                            entries = visible,
                            headers = strings.csvHeaders,
                            typeLabel = { type ->
                                when (type) {
                                    LedgerEntryType.INCOME -> strings.incomeTypeLabel
                                    LedgerEntryType.EXPENSE -> strings.expenseTypeLabel
                                    LedgerEntryType.TRANSFER -> strings.transferTypeLabel
                                    LedgerEntryType.ADJUSTMENT ->
                                        strings.adjustmentTypeLabel
                                }
                            },
                            tagsLabel = { entry ->
                                entry.tagUuids
                                    .mapNotNull { liveTagNames[it] }
                                    .joinToString("、")
                            },
                            costModeLabel = { mode ->
                                when (mode) {
                                    CostTrackingMode.NONE -> strings.costModeNone
                                    CostTrackingMode.ONE_TIME_AMORTIZED ->
                                        strings.costModeOneTime
                                    CostTrackingMode.PERIODIC_SUBSCRIPTION ->
                                        strings.costModePeriodic
                                }
                            },
                            accountLabel = { entry ->
                                entry.accountUuid?.let { accountNames[it] } ?: ""
                            },
                            toAccountLabel = { entry ->
                                entry.toAccountUuid?.let { accountNames[it] } ?: ""
                            },
                            zone = ZoneId.systemDefault(),
                        ),
                    )
                    visible.size
                }
            }
            Toast.makeText(
                context,
                outcome.fold(
                    onSuccess = { strings.backupExported(it) },
                    onFailure = { strings.backupExportFailed },
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun startImport(uri: Uri) {
        if (ledgerPassphrase.isEmpty()) {
            pendingBackupAction = { pass -> ledgerPassphrase = pass; startImport(uri) }
            return
        }
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val declaredSize = context.contentResolver
                        .openAssetFileDescriptor(uri, "r")
                        ?.use { it.length } ?: -1L
                    if (declaredSize > LedgerBackupVault.MAX_BYTES) {
                        throw BackupException(BackupFailure.TOO_LARGE)
                    }
                    val text = context.contentResolver.openInputStream(uri)
                        ?.use { stream ->
                            val out = ByteArrayOutputStream()
                            val buffer = ByteArray(16 * 1024)
                            var total = 0L
                            while (true) {
                                val read = stream.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > LedgerBackupVault.MAX_BYTES) {
                                    throw BackupException(BackupFailure.TOO_LARGE)
                                }
                                out.write(buffer, 0, read)
                            }
                            String(out.toByteArray(), Charsets.UTF_8)
                        }
                        ?: throw IOException("openInputStream returned null")
                    val payload = LedgerBackup.parse(LedgerBackupVault(ledgerPassphrase).decode(text))
                    PendingImport(
                        payload = payload,
                        preview = LedgerBackup.previewMerge(
                            store.queryAllEntriesForSync(),
                            payload.entries,
                        ),
                    )
                }
            }
            outcome.onSuccess { pendingImport = it }
            outcome.onFailure { error ->
                val message = (error as? BackupException)
                    ?.let { strings.backupError(it.failure) }
                    ?: if (error is TotpVaultException) strings.backupDecryptFailed else strings.backupReadFailed
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun applyMergeImport(pending: PendingImport) {
        scope.launch {
            withContext(Dispatchers.IO) {
                store.importSyncPayload(pending.payload)
            }
            pendingImport = null
            onChanged()
            Toast.makeText(
                context,
                strings.backupMergedDone(
                    pending.preview.added,
                    pending.preview.updated,
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun currentBackupPayload() = LedgerSyncPayload(
        entries = store.queryAllEntriesForSync(),
        tags = store.queryTags(includeDeleted = true),
        accounts = store.queryAccounts(includeDeleted = true),
    )

    fun replacementPlan(current: LedgerSyncPayload, incoming: LedgerSyncPayload) = LedgerBackup.planReplace(
        current.entries, incoming.entries,
        localTags = current.tags, incomingTags = incoming.tags,
        localAccounts = current.accounts, incomingAccounts = incoming.accounts,
        nowMillis = System.currentTimeMillis(),
    )

    fun restoreBackupPayload(payload: LedgerSyncPayload, pass: String): ReplacePlan {
        val current = currentBackupPayload()
        val plan = replacementPlan(current, payload)
        BackupRecoveryStore(context, BackupTool.LEDGER).save(
            LedgerBackupVault(pass).encode(LedgerSyncSerializer.toJsonString(current)),
        )
        store.applyReplace(plan)
        return plan
    }

    fun requestReplace(pending: PendingImport) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { replacementPlan(currentBackupPayload(), pending.payload) }
            }
            outcome.onSuccess { pendingReplace = PendingReplace(pending.payload, it) }
            outcome.onFailure { Toast.makeText(context, strings.backupReadFailed, Toast.LENGTH_LONG).show() }
        }
    }

    fun applyReplaceImport(pending: PendingReplace) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                // Recompute and checkpoint immediately before the deliberate replacement.
                runCatching { restoreBackupPayload(pending.payload, ledgerPassphrase) }
            }
            outcome.onSuccess { plan ->
                pendingReplace = null
                pendingImport = null
                onChanged()
                Toast.makeText(context, strings.backupReplacedDone(plan.keptCount, plan.deletedCount),
                    Toast.LENGTH_LONG).show()
            }
            outcome.onFailure { Toast.makeText(context, strings.backupReadFailed, Toast.LENGTH_LONG).show() }
        }
    }

}

@Composable
internal fun LedgerBackupHost(session: LedgerBackupSession) = with(session) {
    pendingBackupAction?.let { action ->
        BackupPassphraseDialog(strings, ledgerPassphrase,
            onDismiss = { pendingBackupAction = null },
            onConfirm = { pass ->
                confirmPassphrase(pass, action)
            })
    }
    if (showBackupHistory) {
        BackupHistoryDialog(
            strings, BackupTool.LEDGER, webDavTargets, ledgerPassphrase,
            preview = { body, pass ->
                LedgerBackup.parse(LedgerBackupVault(pass).decode(body)).entries.count { it.deletedAtMillis == null }
            },
            restore = { body, pass ->
                val payload = LedgerBackup.parse(LedgerBackupVault(pass).decode(body))
                restoreBackupPayload(payload, pass)
            },
            onRestored = ::restored,
            onDismiss = { showBackupHistory = false },
        )
    }

    pendingImport?.let { pending ->
        LedgerImportDialog(
            strings = strings,
            pending = pending,
            onMerge = { applyMergeImport(pending) },
            onReplace = { requestReplace(pending) },
            onDismiss = { pendingImport = null },
        )
    }

    pendingReplace?.let { pending ->
        LedgerReplaceConfirmDialog(
            strings = strings,
            plan = pending.plan,
            onConfirm = { applyReplaceImport(pending) },
            onDismiss = { pendingReplace = null },
        )
    }

}

@Composable
internal fun LedgerSnapshotHost(session: LedgerBackupSession, onDismiss: () -> Unit) = with(session) {
    val zone = ZoneId.systemDefault()
    LaunchedEffect(session) { loadSnapshots() }
    AlertDialog(
        onDismissRequest = { if (!restoreBusy) onDismiss() },
        title = { Text(strings.snapshotRestoreTitle) },
        text = {
            Column {
                Text(
                    strings.snapshotRestoreHint,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                val current = snapshots
                when {
                    current == null -> Text(
                        strings.loading,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    current.isEmpty() -> Text(
                        strings.snapshotRestoreEmpty,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        current.forEach { snapshot ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedSnapshot = snapshot }
                                    .padding(vertical = 4.dp),
                            ) {
                                RadioButton(
                                    selected = selectedSnapshot == snapshot,
                                    onClick = { selectedSnapshot = snapshot },
                                )
                                Column {
                                    Text(
                                        LocalDateTime.ofInstant(
                                            Instant.ofEpochMilli(
                                                snapshot.takenAtMillis,
                                            ),
                                            zone,
                                        ).format(snapshotTimeFormat),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        strings.snapshotSchemaVersion(
                                            snapshot.schemaVersion,
                                        ) + " · " + formatBytes(snapshot.file.length()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme
                                            .onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedSnapshot != null && !restoreBusy,
                onClick = { selectedSnapshot?.let { restoreSnapshot(it, onDismiss) } },
            ) {
                Text(strings.snapshotRestoreAction)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !restoreBusy,
                onClick = { onDismiss() },
            ) {
                Text(strings.cancelAction)
            }
        },
    )
}
