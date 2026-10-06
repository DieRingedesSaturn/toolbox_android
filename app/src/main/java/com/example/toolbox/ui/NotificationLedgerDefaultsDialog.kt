package com.example.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.toolbox.ledger.LedgerAccount
import com.example.toolbox.ledger.LedgerEntryType
import com.example.toolbox.ledger.LedgerTag
import com.example.toolbox.ledger.NotificationLedgerDefaults

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotificationLedgerDefaultsDialog(
    strings: ToolboxStrings,
    sourceLabel: String,
    initialDefaults: NotificationLedgerDefaults,
    accounts: List<LedgerAccount>,
    tags: List<LedgerTag>,
    busy: Boolean,
    onSave: (NotificationLedgerDefaults) -> Unit,
    onDismiss: () -> Unit,
) {
    val availableAccounts = accounts.filter { it.deletedAtMillis == null && !it.isArchived }
    val availableTags = tags.filter { it.deletedAtMillis == null }
    val resolved = initialDefaults.resolve(accounts, tags)
    var type by rememberSaveable { mutableStateOf(resolved.type) }
    var accountUuid by rememberSaveable { mutableStateOf(resolved.accountUuid) }
    var tagUuidsText by rememberSaveable { mutableStateOf(resolved.tagUuids.joinToString(",")) }
    val selectedTags = tagUuidsText.split(',').filter { it.isNotBlank() }.toSet()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("${strings.notificationLedgerDefaults} · $sourceLabel") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(strings.notificationLedgerDefaultsHint)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(LedgerEntryType.EXPENSE, LedgerEntryType.INCOME).forEach { item ->
                        FilterChip(
                            selected = type == item,
                            enabled = !busy,
                            onClick = { type = item },
                            label = {
                                Text(
                                    if (item == LedgerEntryType.INCOME) strings.incomeTypeLabel
                                    else strings.expenseTypeLabel,
                                )
                            },
                        )
                    }
                }
                Text(strings.accountFieldLabel)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = accountUuid == null,
                        enabled = !busy,
                        onClick = { accountUuid = null },
                        label = { Text(strings.notificationLedgerFollowLastAccount) },
                    )
                    availableAccounts.forEach { account ->
                        FilterChip(
                            selected = accountUuid == account.uuid,
                            enabled = !busy,
                            onClick = { accountUuid = account.uuid },
                            label = { Text("${account.emoji} ${account.name} (${account.currency})".trim()) },
                        )
                    }
                }
                Text(strings.tagLabel)
                if (availableTags.isEmpty()) Text(strings.tagsEmptyHint)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableTags.forEach { tag ->
                        FilterChip(
                            selected = tag.uuid in selectedTags,
                            enabled = !busy,
                            onClick = {
                                tagUuidsText = (if (tag.uuid in selectedTags) {
                                    selectedTags - tag.uuid
                                } else selectedTags + tag.uuid).joinToString(",")
                            },
                            label = { Text("${tag.emoji} ${tag.name}".trim()) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    onSave(
                        NotificationLedgerDefaults(type, accountUuid, selectedTags.toList())
                            .resolve(accounts, tags),
                    )
                },
            ) { Text(strings.saveAction) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(strings.cancelAction) }
        },
    )
}
