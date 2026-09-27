package com.example.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.toolbox.ledger.LedgerAccount
import com.example.toolbox.ledger.LedgerAccounts
import com.example.toolbox.ledger.LedgerCalculator
import com.example.toolbox.ledger.LedgerEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Full-screen account detail: balance, reconcile, edit fields, archive /
 * delete, and the account's transaction list.
 */
@Composable
internal fun LedgerAccountDetail(
    strings: ToolboxStrings,
    account: LedgerAccount,
    entries: List<LedgerEntry>,
    tags: List<com.example.toolbox.ledger.LedgerTag>,
    accounts: List<LedgerAccount>,
    onReconcile: (LedgerAccount, Long) -> Unit,
    onSave: (LedgerAccount) -> Unit,
    onArchive: (LedgerAccount, Boolean) -> Unit,
    onDelete: (LedgerAccount) -> Unit,
    onOpenEntry: (LedgerEntry) -> Unit,
    onCopyEntry: (LedgerEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val balance = LedgerAccounts.balance(account, entries)
    val accountEntries = remember(entries, account.uuid) {
        entries.filter {
            it.deletedAtMillis == null &&
                (it.accountUuid == account.uuid || it.toAccountUuid == account.uuid)
        }.sortedByDescending { it.occurredAtMillis }
    }
    val hasEntries = accountEntries.isNotEmpty()

    var editing by rememberSaveable { mutableStateOf(false) }
    var nameText by rememberSaveable { mutableStateOf(account.name) }
    var currencyCode by rememberSaveable { mutableStateOf(account.currency) }
    var openingText by rememberSaveable {
        mutableStateOf("%.2f".format(account.openingBalanceCents / 100.0))
    }
    var openingDate by rememberSaveable {
        mutableStateOf(
            Instant.ofEpochMilli(account.openingAtMillis)
                .atZone(zone).toLocalDate().toString(),
        )
    }
    var emojiText by rememberSaveable { mutableStateOf(account.emoji) }
    var showOpeningPicker by remember { mutableStateOf(false) }
    var reconcileText by rememberSaveable { mutableStateOf("") }
    var showReconcile by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ToolboxTopBar(
                title = account.name,
                onBack = onDismiss,
                backLabel = strings.back,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoCard(title = strings.accountBalanceLabel) {
                InfoRow(
                    label = strings.accountBalanceLabel,
                    value = LedgerCalculator.formatMoney(balance, account.currency),
                    onCopy = {},
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showReconcile = true }) {
                    Text(strings.reconcileAction)
                }
                TextButton(onClick = { editing = !editing }) {
                    Text(strings.editAction)
                }
                TextButton(onClick = { onArchive(account, !account.isArchived) }) {
                    Text(
                        if (account.isArchived) {
                            strings.unarchiveAction
                        } else {
                            strings.archiveAction
                        },
                    )
                }
                if (!hasEntries) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(
                            strings.deleteAction,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (hasEntries) {
                Text(
                    text = strings.accountDeleteBlockedHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (editing) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text(strings.accountNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hasEntries) {
                    Text(
                        text = strings.currencyLockedHasEntries,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LedgerCurrencyChips(
                        strings = strings,
                        selected = currencyCode,
                        onSelect = { currencyCode = it },
                    )
                }
                OutlinedTextField(
                    value = openingText,
                    onValueChange = { openingText = it },
                    label = { Text("${strings.openingBalanceLabel}（$currencyCode）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = emojiText,
                    onValueChange = { emojiText = lastGrapheme(it) },
                    label = { Text(strings.tagEmojiLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    strings.accountEmojiPresets.forEach { glyph ->
                        FilterChip(
                            selected = emojiText == glyph,
                            onClick = { emojiText = glyph },
                            label = { Text(glyph) },
                        )
                    }
                }
                TextButton(onClick = { showOpeningPicker = true }) {
                    Text("${strings.openingDateLabel}：$openingDate")
                }
                Button(
                    onClick = {
                        val cents = openingText.replace(',', '.')
                            .toDoubleOrNull()?.times(100)?.toLong()
                        if (nameText.isNotBlank() && cents != null) {
                            val date = runCatching { LocalDate.parse(openingDate) }
                                .getOrDefault(LocalDate.now(zone))
                            onSave(
                                account.copy(
                                    name = nameText.trim(),
                                    currency = currencyCode,
                                    openingBalanceCents = cents,
                                    openingAtMillis = date.atStartOfDay(zone)
                                        .toInstant().toEpochMilli(),
                                    emoji = emojiText.trim(),
                                ),
                            )
                            editing = false
                        }
                    },
                ) {
                    Text(strings.saveAction)
                }
            }

            Text(
                text = strings.accountTransactionsLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            accountEntries.forEach { entry ->
                LedgerEntryRow(
                    strings = strings,
                    entry = entry,
                    tags = tags,
                    accounts = accounts,
                    onOpen = { onOpenEntry(entry) },
                    onCopy = { onCopyEntry(entry) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }

    if (showReconcile) {
        AlertDialog(
            onDismissRequest = { showReconcile = false },
            title = { Text(strings.reconcileAction) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.reconcilePrompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = reconcileText,
                        onValueChange = { reconcileText = it },
                        label = { Text(account.currency) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cents = reconcileText.replace(',', '.')
                            .toDoubleOrNull()?.times(100)?.toLong()
                        if (cents != null) {
                            onReconcile(account, cents)
                            showReconcile = false
                        }
                    },
                ) {
                    Text(strings.confirmAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReconcile = false }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(account.name) },
            text = { Text(strings.deleteAccountConfirm(account.name)) },
            confirmButton = {
                Button(onClick = { onDelete(account); confirmDelete = false }) {
                    Text(strings.deleteAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }

    if (showOpeningPicker) {
        ToolboxDatePickerDialog(
            initial = runCatching { LocalDate.parse(openingDate) }
                .getOrDefault(LocalDate.now(zone)),
            minDate = null,
            maxDate = LocalDate.now(zone),
            strings = strings,
            onConfirm = { openingDate = it.toString(); showOpeningPicker = false },
            onDismiss = { showOpeningPicker = false },
        )
    }
}
