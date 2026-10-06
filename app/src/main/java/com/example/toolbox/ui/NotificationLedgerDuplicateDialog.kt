package com.example.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.toolbox.ledger.LedgerAccount
import com.example.toolbox.ledger.LedgerAccounts
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerTag
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun NotificationLedgerDuplicateDialog(
    strings: ToolboxStrings,
    matches: List<LedgerEntry>,
    accounts: List<LedgerAccount>,
    tags: List<LedgerTag>,
    busy: Boolean,
    onSaveAnyway: () -> Unit,
    onAlreadyRecorded: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(strings.notificationLedgerDuplicateTitle) },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(strings.notificationLedgerDuplicateHint)
                    if (matches.size > 5) Text(strings.notificationLedgerDuplicateCount(matches.size))
                    matches.take(5).forEach { entry ->
                        val account = accounts.firstOrNull {
                            it.uuid == (entry.accountUuid ?: LedgerAccounts.DEFAULT_ACCOUNT_UUID)
                        }
                        val time = Instant.ofEpochMilli(entry.occurredAtMillis)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        val tagNames = tags.filter { it.deletedAtMillis == null && it.uuid in entry.tagUuids }
                            .map { it.name }
                        Text(ledgerEntryLine(strings, entry, tagNames))
                        Text("${account?.name ?: strings.unknown} · $time")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onSaveAnyway) { Text(strings.notificationLedgerSaveAnyway) }
        },
        dismissButton = {
            Column {
                TextButton(enabled = !busy, onClick = onDismiss) {
                    Text(strings.notificationLedgerReviewEntry)
                }
                TextButton(enabled = !busy, onClick = onAlreadyRecorded) {
                    Text(strings.notificationLedgerAlreadyRecorded)
                }
            }
        },
    )
}
