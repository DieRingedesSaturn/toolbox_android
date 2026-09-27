package com.example.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.toolbox.fx.FxFailure
import com.example.toolbox.fx.FxRates
import com.example.toolbox.fx.FxSource
import com.example.toolbox.ledger.DueRenewal
import com.example.toolbox.ledger.LedgerCalculator
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerEntryType
import com.example.toolbox.ledger.LedgerTag
import com.example.toolbox.ledger.TagFilterMode
import java.time.format.DateTimeFormatter

/** The outcome of a record attempt that couldn't resolve every day's rate. */
internal class RenewalRateIssue(
    val dues: List<DueRenewal>,
    /** requested date -> rates resolved through the chain. */
    val resolved: Map<java.time.LocalDate, FxRates>,
    val unresolvedDates: List<java.time.LocalDate>,
    /** (source, failure) pairs from the chain's last all-sources run. */
    val attempts: List<Pair<FxSource, FxFailure>> = emptyList(),
)

/**
 * Card listing subscriptions/incomes with renewals due. "记录" writes real
 * transaction rows; "跳过" writes tombstone markers so they're never asked
 * again.
 */
@Composable
internal fun PendingRenewalsCard(
    strings: ToolboxStrings,
    pending: Map<LedgerEntry, List<DueRenewal>>,
    busy: Boolean,
    onRecord: (List<DueRenewal>) -> Unit,
    onSkip: (List<DueRenewal>) -> Unit,
    onRecordAll: (List<DueRenewal>) -> Unit,
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("MM-dd") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = strings.pendingRenewalsTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (pending.size > 1) {
                    TextButton(
                        onClick = { onRecordAll(pending.values.flatten()) },
                    ) {
                        Text(strings.recordAllAction)
                    }
                }
            }
            pending.forEach { (sub, dues) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sub.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = listOf(
                                if (sub.type == LedgerEntryType.INCOME) {
                                    strings.incomeTypeLabel
                                } else {
                                    strings.expenseTypeLabel
                                },
                                strings.renewalDatesSummary(
                                    dues.size,
                                    dues.take(3).joinToString("、") {
                                        it.date.format(dateFormat)
                                    } + if (dues.size > 3) " …" else "",
                                ),
                                LedgerCalculator.formatMoney(
                                    sub.amountCents,
                                    sub.currency,
                                ),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    TextButton(
                        enabled = !busy,
                        onClick = { onRecord(dues) },
                    ) {
                        Text(strings.recordAction)
                    }
                    TextButton(
                        enabled = !busy,
                        onClick = { onSkip(dues) },
                    ) {
                        Text(strings.skipAction)
                    }
                }
            }
        }
    }
}

/**
 * Shown when some due dates' rates couldn't be resolved: let the user apply
 * the nearest cached rate (per date) or one manual rate to the unresolved
 * ones. Nothing is written until a choice succeeds.
 */
@Composable
internal fun RenewalRateIssueDialog(
    strings: ToolboxStrings,
    issue: RenewalRateIssue,
    nearest: Map<java.time.LocalDate, FxRates>,
    onUseCached: (Map<java.time.LocalDate, FxRates>) -> Unit,
    onManual: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var manualText by remember { mutableStateOf("") }
    val manualRate = manualText.replace(',', '.').toDoubleOrNull()
    val code = issue.dues
        .firstOrNull { it.subscription.currency != "CNY" }
        ?.subscription?.currency ?: "JPY"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.renewalRateMissing(issue.unresolvedDates.size)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = issue.unresolvedDates.joinToString("、") { it.toString() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                issue.attempts.forEach { (source, failure) ->
                    Text(
                        text = strings.fxSourceError(source.id, failure, null),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (nearest.isNotEmpty()) {
                    TextButton(
                        onClick = { onUseCached(nearest) },
                    ) {
                        Text(
                            strings.useNearestCachedRate(
                                nearest.values.maxByOrNull { it.date }?.date ?: "",
                                nearest.values.firstOrNull()?.source?.id
                                    ?.let { strings.fxSourceName(it) } ?: "",
                            ),
                        )
                    }
                }
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    label = { Text(strings.manualRateFieldLabel(code)) },
                    suffix = { Text("CNY") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { manualRate?.let { if (it > 0) onManual(it) } },
                enabled = manualRate != null && manualRate > 0,
            ) {
                Text(strings.manualRateSwitch)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
        },
    )
}

/** Multi-select tag filter for the Transactions/Costs tabs. */
@Composable
internal fun TagFilterDialog(
    strings: ToolboxStrings,
    tags: List<LedgerTag>,
    selected: Set<String>,
    mode: TagFilterMode,
    onApply: (Set<String>, TagFilterMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(selected) }
    var pickedMode by remember { mutableStateOf(mode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.tagFilterAction) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        TagFilterMode.ANY to strings.filterMatchAny,
                        TagFilterMode.ALL to strings.filterMatchAll,
                    ).forEachIndexed { index, (m, label) ->
                        SegmentedButton(
                            selected = pickedMode == m,
                            onClick = { pickedMode = m },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        ) {
                            Text(label)
                        }
                    }
                }
                tags.filter { it.deletedAtMillis == null }.forEach { tag ->
                    FilterChip(
                        selected = tag.uuid in picked,
                        onClick = {
                            picked = picked.toMutableSet().also {
                                if (!it.add(tag.uuid)) it.remove(tag.uuid)
                            }
                        },
                        label = {
                            Text(
                                (if (tag.emoji.isNotBlank()) "${tag.emoji} " else "") + tag.name,
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(picked, pickedMode) }) {
                Text(strings.confirmAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
        },
    )
}
