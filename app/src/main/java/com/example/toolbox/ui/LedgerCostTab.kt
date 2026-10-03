package com.example.toolbox.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.FilterChip
import com.example.toolbox.ledger.CostBreakdown
import com.example.toolbox.ledger.CostTrackingMode
import com.example.toolbox.ledger.DISPOSAL_SOLD
import com.example.toolbox.ledger.DueRenewal
import com.example.toolbox.ledger.LedgerCalculator
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerEntryType
import com.example.toolbox.ledger.LedgerSummary
import com.example.toolbox.ledger.LedgerTag
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
private fun LedgerStatusChip(text: String, active: Boolean) {
    Surface(
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
internal fun LedgerCostTab(
    strings: ToolboxStrings,
    costItems: List<Pair<LedgerEntry, CostBreakdown>>,
    summary: LedgerSummary,
    tags: List<LedgerTag>,
    pendingRenewals: Map<LedgerEntry, List<DueRenewal>>,
    renewalBusy: Boolean,
    isFiltered: Boolean,
    onOpenFilter: () -> Unit,
    onClearFilter: () -> Unit,
    onRecordRenewals: (List<DueRenewal>) -> Unit,
    onSkipRenewals: (List<DueRenewal>) -> Unit,
    onDispose: (LedgerEntry) -> Unit,
    onUndoDisposal: (LedgerEntry) -> Unit,
    onStopSubscription: (LedgerEntry) -> Unit,
    onResume: (LedgerEntry) -> Unit,
    onOpenEntry: (LedgerEntry) -> Unit,
    onCopyEntry: (LedgerEntry) -> Unit,
    modifier: Modifier = Modifier,
    onManageTags: () -> Unit = {},
) {
    val context = LocalContext.current
    val assets = costItems.filter {
        it.first.costTrackingMode == CostTrackingMode.ONE_TIME_AMORTIZED
    }
    val subscriptions = costItems.filter {
        it.first.costTrackingMode == CostTrackingMode.PERIODIC_SUBSCRIPTION &&
            it.first.type == LedgerEntryType.EXPENSE
    }
    val fixedIncomes = costItems.filter {
        it.first.costTrackingMode == CostTrackingMode.PERIODIC_SUBSCRIPTION &&
            it.first.type == LedgerEntryType.INCOME
    }
    val burnDaily = costItems
        .filter { it.second.isActive && it.first.type == LedgerEntryType.EXPENSE }
        .sumOf { it.second.dailyCostYuan }
    val burnMonthly = costItems
        .filter { it.second.isActive && it.first.type == LedgerEntryType.EXPENSE }
        .sumOf { it.second.monthlyCostYuan }
    val incomeMonthly = costItems
        .filter { it.second.isActive && it.first.type == LedgerEntryType.INCOME }
        .sumOf { it.second.monthlyCostYuan }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (pendingRenewals.isNotEmpty()) {
            item {
                PendingRenewalsCard(
                    strings = strings,
                    pending = pendingRenewals,
                    busy = renewalBusy,
                    onRecord = onRecordRenewals,
                    onSkip = onSkipRenewals,
                    onRecordAll = onRecordRenewals,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = isFiltered,
                    onClick = onOpenFilter,
                    label = { Text(strings.tagFilterAction) },
                )
                if (isFiltered) {
                    Spacer(modifier = Modifier.width(8.dp))
                    LedgerStatusChip(text = strings.filteredBadge, active = false)
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onClearFilter) {
                        Text(strings.clearFilter)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onManageTags) {
                    Text(strings.manageTagsChip)
                }
            }
        }

        item {
            InfoCard(title = strings.dailyBurnRateTitle) {
                listOf(
                    strings.totalDailyCostLabel to
                        "${LedgerCalculator.formatCurrency(burnDaily)}${strings.perDayUnit}",
                    strings.totalMonthlyCostLabel to
                        "${LedgerCalculator.formatCurrency(burnMonthly)}${strings.perMonthUnit}",
                    strings.totalYearlyCostLabel to
                        "${LedgerCalculator.formatCurrency(burnDaily * 365.25)}${strings.perYearUnit}",
                ).forEach { (label, value) ->
                    InfoRow(
                        label = label,
                        value = value,
                        onCopy = { text ->
                            copyToClipboard(
                                context = context,
                                label = label,
                                value = text,
                                copiedMessage = strings.copied(label),
                            )
                        },
                    )
                }
                if (fixedIncomes.isNotEmpty()) {
                    InfoRow(
                        label = strings.fixedIncomeSection,
                        value = strings.incomeMinusCosts(
                            LedgerCalculator.formatCurrency(incomeMonthly - burnMonthly),
                        ),
                        onCopy = {},
                    )
                }
            }
        }

        if (costItems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Text(
                        text = strings.emptyCostItemsHint,
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (assets.isNotEmpty()) {
            item(key = "assets-header") {
                Text(
                    text = strings.costSectionAssets,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(assets, key = { "asset-${it.first.uuid}" }) { (entry, breakdown) ->
                CostAssetCard(
                    strings = strings,
                    entry = entry,
                    breakdown = breakdown,
                    onDispose = { onDispose(entry) },
                    onUndoDisposal = { onUndoDisposal(entry) },
                    onResume = { onResume(entry) },
                    onOpen = { onOpenEntry(entry) },
                    onCopy = { onCopyEntry(entry) },
                )
            }
        }

        if (subscriptions.isNotEmpty()) {
            item(key = "subs-header") {
                Text(
                    text = strings.costSectionSubscriptions,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(subscriptions, key = { "sub-${it.first.uuid}" }) { (entry, breakdown) ->
                CostSubscriptionCard(
                    strings = strings,
                    entry = entry,
                    breakdown = breakdown,
                    onToggleActive = {
                        if (entry.isActiveCost) onStopSubscription(entry) else onResume(entry)
                    },
                    onOpen = { onOpenEntry(entry) },
                    onCopy = { onCopyEntry(entry) },
                )
            }
        }

        if (fixedIncomes.isNotEmpty()) {
            item(key = "income-header") {
                Text(
                    text = strings.fixedIncomeSection,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(fixedIncomes, key = { "inc-${it.first.uuid}" }) { (entry, breakdown) ->
                CostSubscriptionCard(
                    strings = strings,
                    entry = entry,
                    breakdown = breakdown,
                    income = true,
                    onToggleActive = {
                        if (entry.isActiveCost) onStopSubscription(entry) else onResume(entry)
                    },
                    onOpen = { onOpenEntry(entry) },
                    onCopy = { onCopyEntry(entry) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CostAssetCard(
    strings: ToolboxStrings,
    entry: LedgerEntry,
    breakdown: CostBreakdown,
    onDispose: () -> Unit,
    onUndoDisposal: () -> Unit,
    onResume: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val dailyStr = "${LedgerCalculator.formatCurrency(breakdown.dailyCostYuan)}${strings.perDayUnit}"
    val monthlyStr = "${LedgerCalculator.formatCurrency(breakdown.monthlyCostYuan)}${strings.perMonthUnit}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onCopy),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                LedgerStatusChip(
                    text = when {
                        entry.isActiveCost && !breakdown.isActive -> strings.statusEnded
                        entry.isActiveCost -> strings.statusActiveInUse
                        else -> strings.statusRetired
                    },
                    active = breakdown.isActive,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = dailyStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (breakdown.isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = monthlyStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = strings.oneTimeHeldInfo(
                    price = LedgerCalculator.formatMoney(
                        entry.amountCents,
                        entry.currency,
                    ),
                    salvage = entry.salvageValueCents.takeIf { it > 0 }
                        ?.let {
                            LedgerCalculator.formatMoney(
                                entry.salvageValueCents,
                                entry.currency,
                            )
                        },
                    daysHeld = breakdown.daysHeld,
                    targetDays = null,
                    actualDaily = null,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            breakdown.targetDays?.let { target ->
                val progress = (breakdown.daysHeld.toFloat() / target).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                )
                Text(
                    text = strings.usedDaysTarget(breakdown.daysHeld, target),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (entry.isActiveCost && entry.disposalType == null) {
                entry.costEndsAtMillis?.let { endMillis ->
                    val endDate = Instant.ofEpochMilli(endMillis)
                        .atZone(zone).toLocalDate().toString()
                    Text(
                        text = if (breakdown.isActive) {
                            strings.costEndsOn(endDate)
                        } else {
                            strings.costEndedOn(endDate)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            entry.disposalType?.let { disposal ->
                val disposedDate = entry.retiredAtMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString()
                } ?: ""
                Text(
                    text = when (disposal) {
                        DISPOSAL_SOLD -> strings.soldLine(
                            disposedDate,
                            LedgerCalculator.formatCurrency(
                                (entry.baseAmountCents - breakdown.netCostYuan * 100).toLong() / 100.0,
                            ),
                        )
                        else -> strings.scrappedLine(disposedDate)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                when {
                    entry.isActiveCost -> TextButton(onClick = onDispose) {
                        Text(strings.endUseAction)
                    }
                    entry.disposalType != null -> TextButton(onClick = onUndoDisposal) {
                        Text(strings.undoDisposalAction)
                    }
                    else -> TextButton(onClick = onResume) {
                        Text(strings.reactivateAction)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CostSubscriptionCard(
    strings: ToolboxStrings,
    entry: LedgerEntry,
    breakdown: CostBreakdown,
    income: Boolean = false,
    onToggleActive: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val dailyStr = "${LedgerCalculator.formatCurrency(breakdown.dailyCostYuan)}${strings.perDayUnit}"
    val monthlyStr = "${LedgerCalculator.formatCurrency(breakdown.monthlyCostYuan)}${strings.perMonthUnit}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onCopy),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                LedgerStatusChip(
                    text = when {
                        entry.isActiveCost && !breakdown.isActive -> strings.statusEnded
                        entry.isActiveCost -> strings.statusSubActive
                        else -> strings.statusSubStopped
                    },
                    active = breakdown.isActive,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = dailyStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (breakdown.isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = monthlyStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "${strings.cycleName(entry.billingCycle, entry.customCycleDays, entry.customCycleUnit)} · " +
                    LedgerCalculator.formatMoney(entry.amountCents, entry.currency),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildString {
                    append(
                        if (income) {
                            strings.accumulatedIncome(
                                breakdown.accumulatedCyclesCount,
                                LedgerCalculator.formatCurrency(breakdown.accumulatedTotalYuan),
                            )
                        } else {
                            strings.accumulatedPayments(
                                breakdown.accumulatedCyclesCount,
                                LedgerCalculator.formatCurrency(breakdown.accumulatedTotalYuan),
                            )
                        },
                    )
                    breakdown.nextRenewalMillis?.let {
                        val dateText = dateFormatter.format(Date(it))
                        append(
                            if (income) {
                                " · ${strings.nextPaydayLabel} $dateText（${breakdown.daysUntilRenewal ?: 0}d）"
                            } else {
                                " · ${strings.nextRenewalLabel(dateText, breakdown.daysUntilRenewal ?: 0)}"
                            },
                        )
                    }
                    entry.costEndsAtMillis?.let {
                        val endText = dateFormatter.format(Date(it))
                        append(
                            if (breakdown.isActive) {
                                " · ${strings.costEndsOn(endText)}"
                            } else {
                                " · ${strings.costEndedOn(endText)}"
                            },
                        )
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // An item ended by its scheduled date has no manual toggle —
            // editing the entry moves or clears the date.
            if (!(entry.isActiveCost && !breakdown.isActive)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onToggleActive) {
                        Text(
                            if (entry.isActiveCost) {
                                strings.stopSubscriptionAction
                            } else {
                                strings.resumeSubscriptionAction
                            },
                        )
                    }
                }
            }
        }
    }
}
