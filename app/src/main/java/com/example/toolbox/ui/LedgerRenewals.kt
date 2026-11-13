package com.example.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.toolbox.R
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
    ToolboxCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
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
@OptIn(ExperimentalLayoutApi::class)
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
    var query by rememberSaveable { mutableStateOf("") }
    val liveTags = tags.filter { it.deletedAtMillis == null }
    val visibleTags = liveTags.filter {
        it.name.contains(query.trim(), ignoreCase = true) || it.emoji.contains(query.trim())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .acrylicSurface(
                shape = MaterialTheme.shapes.extraLarge,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        title = { Text(strings.tagFilterAction) },
        text = {
            Column(
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
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(strings.searchTagsHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (visibleTags.isEmpty()) {
                    Text(
                        text = if (liveTags.isEmpty()) strings.tagsEmptyHint else strings.noMatchingTags,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visibleTags.forEach { tag ->
                        ToolboxFilterChip(
                            selected = tag.uuid in picked,
                            onClick = {
                                picked = if (tag.uuid in picked) picked - tag.uuid else picked + tag.uuid
                            },
                            leadingIcon = {
                                // Reserve the same slot in both states so selection never changes chip width.
                                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                                    if (tag.uuid in picked) {
                                        Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                                    } else {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(tag.colorArgb)))
                                    }
                                }
                            },
                            label = {
                                Text(
                                    text = (if (tag.emoji.isNotBlank()) "${tag.emoji} " else "") + tag.name,
                                    modifier = Modifier.widthIn(max = 220.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
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

/** A single compact action row; individual selections are reviewed in the filter dialog. */
@Composable
internal fun LedgerTagFilterBar(
    strings: ToolboxStrings,
    selectedCount: Int,
    onOpenFilter: () -> Unit,
    onClearFilter: () -> Unit,
    onManageTags: () -> Unit,
) {
    ToolboxCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolboxFilterChip(
                selected = selectedCount > 0,
                onClick = onOpenFilter,
                modifier = Modifier.weight(1f, fill = false),
                label = {
                    Text(
                        text = strings.tagFilterCount(selectedCount),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            if (selectedCount > 0) {
                IconButton(onClick = onClearFilter) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = strings.clearFilter)
                }
            }
            TextButton(onClick = onManageTags) {
                Text(strings.manageTagsChip, maxLines = 1)
            }
        }
    }
}
