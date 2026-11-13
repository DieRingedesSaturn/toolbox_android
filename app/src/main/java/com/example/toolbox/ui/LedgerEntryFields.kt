package com.example.toolbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.toolbox.fx.LEDGER_CURRENCIES
import com.example.toolbox.ledger.BillingCycle
import com.example.toolbox.ledger.CycleUnit
import com.example.toolbox.ledger.LedgerAccount
import com.example.toolbox.ledger.LedgerEntryType
import java.time.LocalDate

/** Chip row for picking a ledger account. */
@Composable
internal fun AccountChips(
    accounts: List<LedgerAccount>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        accounts.filter { !it.isArchived }.forEach { account ->
            FilterChip(
                selected = selected == account.uuid,
                onClick = { onSelect(account.uuid) },
                label = {
                    Text(
                        (if (account.emoji.isNotBlank()) "${account.emoji} " else "") +
                            account.name,
                    )
                },
            )
        }
    }
}

/** Shared 4-currency segmented selector. */
@Composable
internal fun LedgerCurrencyChips(
    strings: ToolboxStrings,
    selected: String,
    onSelect: (String) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        LEDGER_CURRENCIES.forEachIndexed { index, code ->
            SegmentedButton(
                selected = selected == code,
                onClick = { onSelect(code) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = LEDGER_CURRENCIES.size,
                ),
            ) {
                Text(code)
            }
        }
    }
}

@Composable
internal fun LedgerCycleChips(
    strings: ToolboxStrings,
    billingCycle: BillingCycle,
    customCycleDaysText: String,
    customCycleUnit: String,
    onCycle: (BillingCycle) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            BillingCycle.MONTHLY,
            BillingCycle.QUARTERLY,
            BillingCycle.SEMI_ANNUAL,
            BillingCycle.YEARLY,
            BillingCycle.WEEKLY,
            BillingCycle.CUSTOM_DAYS,
        ).forEach { cycle ->
            FilterChip(
                selected = billingCycle == cycle,
                onClick = { onCycle(cycle) },
                label = {
                    Text(
                        if (cycle == BillingCycle.CUSTOM_DAYS) {
                            // Reads "自定义" up front — the configured
                            // interval shows below once selected.
                            buildString {
                                append(strings.cycleCustomChip)
                                if (billingCycle == BillingCycle.CUSTOM_DAYS) {
                                    append(" · ")
                                    append(
                                        strings.cycleName(
                                            cycle,
                                            customCycleDaysText.toIntOrNull() ?: 30,
                                            runCatching {
                                                CycleUnit.valueOf(customCycleUnit)
                                            }.getOrDefault(CycleUnit.DAYS),
                                        ),
                                    )
                                }
                            }
                        } else {
                            strings.cycleName(
                                cycle,
                                customCycleDaysText.toIntOrNull() ?: 30,
                                runCatching { CycleUnit.valueOf(customCycleUnit) }
                                    .getOrDefault(CycleUnit.DAYS),
                            )
                        },
                    )
                },
            )
        }
    }
}

/** Read-only end-date field with a clear action; opens the shared picker. */
@Composable
internal fun CostEndDateRow(
    strings: ToolboxStrings,
    label: String,
    endDate: LocalDate?,
    error: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = endDate?.toString() ?: strings.costEndDateNone,
                onValueChange = {},
                label = { Text(label) },
                readOnly = true,
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClick = onPick),
            )
        }
        if (endDate != null) {
            TextButton(onClick = onClear) {
                Text(strings.clearFilter)
            }
        }
    }
}

@Composable
internal fun CustomCycleInputs(
    strings: ToolboxStrings,
    countText: String,
    onCount: (String) -> Unit,
    unit: String,
    onUnit: (String) -> Unit,
) {
    OutlinedTextField(
        value = countText,
        onValueChange = onCount,
        label = { Text(strings.customDaysInputLabel) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        CycleUnit.entries.forEachIndexed { index, cycleUnit ->
            SegmentedButton(
                selected = unit == cycleUnit.name,
                onClick = { onUnit(cycleUnit.name) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = CycleUnit.entries.size,
                ),
            ) {
                Text(strings.cycleUnitName(cycleUnit))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerEntryTypeFields(strings: ToolboxStrings, entryType: LedgerEntryType, isLinked: Boolean,
    onType: (LedgerEntryType) -> Unit,
) {
    val isAdjustment = entryType == LedgerEntryType.ADJUSTMENT
    if (isAdjustment) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = strings.adjustmentTypeLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = strings.adjustmentEditorHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(
                LedgerEntryType.EXPENSE to strings.expenseTypeLabel,
                LedgerEntryType.INCOME to strings.incomeTypeLabel,
                LedgerEntryType.TRANSFER to strings.transferTypeLabel,
            ).forEachIndexed { index, (type, label) ->
                SegmentedButton(
                    selected = entryType == type,
                    onClick = { onType(type) },
                    enabled = !isLinked,
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = 3,
                    ),
                ) {
                    Text(label)
                }
            }
        }
    }

}

@Composable
internal fun LedgerAccountFields(strings: ToolboxStrings, isTransfer: Boolean, liveAccounts: List<LedgerAccount>,
    accountUuid: String, toAccountUuid: String, onAccount: (String) -> Unit, onToAccount: (String) -> Unit,
) {
    if (isTransfer) {
        Text(
            text = strings.transferFromLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AccountChips(
            accounts = liveAccounts,
            selected = accountUuid,
            onSelect = onAccount,
        )
        Text(
            text = strings.transferToLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AccountChips(
            accounts = liveAccounts.filter { it.uuid != accountUuid },
            selected = toAccountUuid,
            onSelect = onToAccount,
        )
        if (toAccountUuid == accountUuid && toAccountUuid.isNotBlank()) {
            Text(
                text = strings.sameAccountError,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    } else if (liveAccounts.isNotEmpty()) {
        Text(
            text = strings.accountFieldLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AccountChips(
            accounts = liveAccounts,
            selected = accountUuid,
            onSelect = onAccount,
        )
    }

}
