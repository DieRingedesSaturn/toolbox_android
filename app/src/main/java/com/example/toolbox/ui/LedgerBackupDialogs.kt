package com.example.toolbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.toolbox.ledger.LedgerSyncPayload
import com.example.toolbox.ledger.MergePreview
import com.example.toolbox.ledger.ReplacePlan
import java.time.Instant
import java.time.ZoneId

internal data class PendingImport(
    val payload: LedgerSyncPayload,
    val preview: MergePreview,
)

internal data class PendingReplace(
    val payload: LedgerSyncPayload,
    val plan: ReplacePlan,
)

@Composable
internal fun LedgerImportDialog(
    strings: ToolboxStrings,
    pending: PendingImport,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val liveEntries = pending.payload.entries.filter { it.deletedAtMillis == null }
    val dateRange = if (liveEntries.isEmpty()) {
        strings.backupNoRecords
    } else {
        val zone = ZoneId.systemDefault()
        fun dateOf(millis: Long) =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()
        "${dateOf(liveEntries.minOf { it.occurredAtMillis })} – " +
            dateOf(liveEntries.maxOf { it.occurredAtMillis })
    }
    val exportedAt = strings.formatLedgerDateTime(pending.payload.clientTimestampMillis)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.backupImportTitle) },
        text = {
            Column {
                InfoRow(
                    label = strings.backupFileEntriesLabel,
                    value = liveEntries.size.toString(),
                    onCopy = {
                        copyToClipboard(
                            context = context,
                            label = strings.backupFileEntriesLabel,
                            value = it,
                        )
                    },
                )
                InfoRow(
                    label = strings.backupDateRangeLabel,
                    value = dateRange,
                    onCopy = {
                        copyToClipboard(
                            context = context,
                            label = strings.backupDateRangeLabel,
                            value = it,
                        )
                    },
                )
                InfoRow(
                    label = strings.backupExportedAtLabel,
                    value = exportedAt,
                    onCopy = {
                        copyToClipboard(
                            context = context,
                            label = strings.backupExportedAtLabel,
                            value = it,
                        )
                    },
                )
                Text(
                    text = strings.backupMergePreview(
                        pending.preview.added,
                        pending.preview.updated,
                        pending.preview.unchanged,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = onMerge) {
                Text(strings.backupMergeAction)
            }
        },
        dismissButton = {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onReplace) {
                    Text(
                        text = strings.backupReplaceAction,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(strings.cancelAction)
                }
            }
        },
    )
}

@Composable
internal fun LedgerReplaceConfirmDialog(
    strings: ToolboxStrings,
    plan: ReplacePlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.backupReplaceConfirmTitle) },
        text = {
            Text(strings.backupReplaceConfirmBody(plan.keptCount, plan.deletedCount))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(strings.backupReplaceAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction)
            }
        },
    )
}
