package com.example.toolbox.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.toolbox.R
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
internal fun LedgerCostTab(
    strings: ToolboxStrings,
    costItems: List<Pair<LedgerEntry, CostBreakdown>>,
    summary: LedgerSummary,
    tags: List<LedgerTag>,
    pendingRenewals: Map<LedgerEntry, List<DueRenewal>>,
    renewalBusy: Boolean,
    filterSelectedCount: Int,
    onOpenFilter: () -> Unit,
    onClearFilter: () -> Unit,
    onRecordRenewals: (List<DueRenewal>) -> Unit,
    onSkipRenewals: (List<DueRenewal>) -> Unit,
    onDispose: (LedgerEntry, startAsSale: Boolean) -> Unit,
    onUndoDisposal: (LedgerEntry) -> Unit,
    onStopSubscription: (LedgerEntry) -> Unit,
    onResume: (LedgerEntry) -> Unit,
    onOpenEntry: (LedgerEntry) -> Unit,
    onCopyEntry: (LedgerEntry) -> Unit,
    modifier: Modifier = Modifier,
    onManageTags: () -> Unit = {},
) {
    val context = LocalContext.current
    var expandedEntryUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val liveTagsByUuid = remember(tags) {
        tags.filter { it.deletedAtMillis == null }.associateBy { it.uuid }
    }
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
            LedgerTagFilterBar(
                strings = strings,
                selectedCount = filterSelectedCount,
                onOpenFilter = onOpenFilter,
                onClearFilter = onClearFilter,
                onManageTags = onManageTags,
            )
        }

        item {
            InfoCard(title = strings.dailyBurnRateTitle) {
                listOf(
                    strings.totalDailyCostLabel to
                        "${LedgerCalculator.formatCurrency(burnDaily)}${strings.perDayUnit}",
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
                Text(
                    text = strings.dailyHoldingCostRule,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (costItems.isEmpty()) {
            item {
                ToolboxCard(
                    modifier = Modifier.fillMaxWidth(),
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
                CostSectionHeader(
                    title = strings.costSectionAssets,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            items(assets, key = { "asset-${it.first.uuid}" }) { (entry, breakdown) ->
                CostAssetCard(
                    strings = strings,
                    entry = entry,
                    breakdown = breakdown,
                    entryTags = entry.tagUuids.mapNotNull(liveTagsByUuid::get),
                    expanded = expandedEntryUuid == entry.uuid,
                    onToggleDetails = {
                        expandedEntryUuid = entry.uuid.takeUnless { expandedEntryUuid == it }
                    },
                    onDispose = { startAsSale -> onDispose(entry, startAsSale) },
                    onUndoDisposal = { onUndoDisposal(entry) },
                    onResume = { onResume(entry) },
                    onOpen = { onOpenEntry(entry) },
                    onCopy = { onCopyEntry(entry) },
                )
            }
        }

        if (subscriptions.isNotEmpty()) {
            item(key = "subs-header") {
                CostSectionHeader(
                    title = strings.costSectionSubscriptions,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            items(subscriptions, key = { "sub-${it.first.uuid}" }) { (entry, breakdown) ->
                CostSubscriptionCard(
                    strings = strings,
                    entry = entry,
                    breakdown = breakdown,
                    entryTags = entry.tagUuids.mapNotNull(liveTagsByUuid::get),
                    expanded = expandedEntryUuid == entry.uuid,
                    onToggleDetails = {
                        expandedEntryUuid = entry.uuid.takeUnless { expandedEntryUuid == it }
                    },
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
                CostSectionHeader(
                    title = strings.fixedIncomeSection,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            items(fixedIncomes, key = { "inc-${it.first.uuid}" }) { (entry, breakdown) ->
                CostSubscriptionCard(
                    strings = strings,
                    entry = entry,
                    breakdown = breakdown,
                    income = true,
                    entryTags = entry.tagUuids.mapNotNull(liveTagsByUuid::get),
                    expanded = expandedEntryUuid == entry.uuid,
                    onToggleDetails = {
                        expandedEntryUuid = entry.uuid.takeUnless { expandedEntryUuid == it }
                    },
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

@Composable
private fun CompactMetricGrid(
    cells: List<Pair<String, String>>,
    onCopy: (label: String, value: String) -> Unit,
    columns: Int = 3,
) {
    if (cells.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCopy(label, value) }
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CostSectionHeader(title: String, containerColor: Color, contentColor: Color) {
    ToolboxCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CostEntryTags(strings: ToolboxStrings, tags: List<LedgerTag>, expanded: Boolean) {
    if (tags.isEmpty()) {
        if (expanded) {
            Text(
                text = strings.untaggedLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    if (expanded) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tags.forEach { CostTagLabel(it) }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tags.take(2).forEach { CostTagLabel(it) }
            if (tags.size > 2) {
                Text(
                    text = "+${tags.size - 2}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CostTagLabel(tag: LedgerTag) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(tag.colorArgb)))
            Text(
                text = (if (tag.emoji.isNotBlank()) "${tag.emoji} " else "") + tag.name,
                modifier = Modifier.widthIn(max = 160.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CostCardHeader(
    entry: LedgerEntry,
    breakdown: CostBreakdown,
    status: String,
    primaryRate: String,
    secondaryRate: String? = null,
    expanded: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = primaryRate,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (breakdown.isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (secondaryRate != null) {
                Text(
                    text = secondaryRate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp).rotate(if (expanded) 270f else 90f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CostAssetCard(
    strings: ToolboxStrings,
    entry: LedgerEntry,
    breakdown: CostBreakdown,
    entryTags: List<LedgerTag>,
    expanded: Boolean,
    onToggleDetails: () -> Unit,
    onDispose: (startAsSale: Boolean) -> Unit,
    onUndoDisposal: () -> Unit,
    onResume: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val dailyStr = "${LedgerCalculator.formatCurrency(breakdown.dailyCostYuan)}${strings.perDayUnit}"
    val purchased = Instant.ofEpochMilli(entry.occurredAtMillis)
        .atZone(zone).toLocalDate().toString()
    val metricCells = buildList {
        add(
            strings.assetPriceLabel to
                LedgerCalculator.formatMoney(entry.amountCents, entry.currency),
        )
        if (entry.currency != "CNY") {
            add(
                strings.assetCnyLabel to
                    LedgerCalculator.formatCurrency(entry.baseAmountCents / 100.0),
            )
        }
        if (entry.salvageValueCents > 0L) {
            add(
                strings.assetSalvageLabel to
                    LedgerCalculator.formatMoney(entry.salvageValueCents, entry.currency),
            )
        }
        add(
            strings.assetNetCostLabel to
                LedgerCalculator.formatCurrency(breakdown.netCostYuan),
        )
        add(strings.assetHeldLabel to strings.daysCount(breakdown.daysHeld))
        val planned = breakdown.targetDays
        if (planned != null && entry.disposalType == null) {
            add(
                strings.assetRemainingDaysLabel to
                    strings.daysCount((planned - breakdown.daysHeld).coerceAtLeast(0)),
            )
            LedgerCalculator.remainingBookValueYuan(
                breakdown.netCostYuan,
                breakdown.daysHeld,
                planned,
            )?.let { book ->
                add(
                    strings.assetRemainingBookLabel to
                        LedgerCalculator.formatCurrency(book),
                )
            }
        }
        breakdown.targetDailyCostYuan
            ?.takeIf { breakdown.targetDays != null }
            ?.let { actual ->
                add(
                    strings.assetActualDailyLabel to
                        "${LedgerCalculator.formatCurrency(actual)}${strings.perDayUnit}",
                )
            }
        add(
            strings.assetYearlyLabel to
                "${LedgerCalculator.formatCurrency(breakdown.yearlyCostYuan)}${strings.perYearUnit}",
        )
    }

    CostExpandableCard(
        expanded = expanded,
        showDetailsLabel = strings.costShowDetails,
        hideDetailsLabel = strings.costHideDetails,
        copyLabel = strings.copyAction,
        onToggle = onToggleDetails,
        onCopy = onCopy,
    ) {
            CostCardHeader(
                entry = entry,
                breakdown = breakdown,
                status = when {
                    entry.isActiveCost && !breakdown.isActive -> strings.statusAmortized
                    entry.isActiveCost -> strings.statusActiveInUse
                    else -> strings.statusRetired
                },
                primaryRate = dailyStr,
                secondaryRate = if (expanded) {
                    "${strings.totalYearlyCostLabel} ${LedgerCalculator.formatCurrency(breakdown.yearlyCostYuan)}${strings.perYearUnit}"
                } else {
                    null
                },
                expanded = expanded,
            )

            CostEntryTags(strings = strings, tags = entryTags, expanded = expanded)

            if (expanded) {
                Text(
                    text = buildString {
                        append(strings.purchasedOn(purchased))
                        if (entry.isActiveCost && entry.disposalType == null) {
                            entry.costEndsAtMillis?.let { endMillis ->
                                val endDate = Instant.ofEpochMilli(endMillis)
                                    .atZone(zone).toLocalDate().toString()
                                append(" · ")
                                append(
                                    if (breakdown.isActive) {
                                        strings.amortizesUntil(endDate)
                                    } else {
                                        strings.amortizedOn(endDate)
                                    },
                                )
                            }
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                CompactMetricGrid(
                    cells = metricCells,
                    onCopy = { label, value ->
                        copyToClipboard(
                            context = context,
                            label = label,
                            value = value,
                            copiedMessage = strings.copied(label),
                        )
                    },
                )

                breakdown.targetDays?.let { target ->
                    val progress = (breakdown.daysHeld.toFloat() / target).coerceIn(0f, 1f)
                    val percent = (progress * 100).toInt()
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Text(
                        text = "${strings.usedDaysTarget(breakdown.daysHeld, target)} · $percent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = onOpen) { Text(strings.editAction) }
                    when {
                        entry.isActiveCost -> {
                            TextButton(onClick = { onDispose(true) }) {
                                Text(strings.sellOption)
                            }
                            TextButton(onClick = { onDispose(false) }) {
                                Text(strings.scrapOption)
                            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CostSubscriptionCard(
    strings: ToolboxStrings,
    entry: LedgerEntry,
    breakdown: CostBreakdown,
    entryTags: List<LedgerTag>,
    income: Boolean = false,
    expanded: Boolean,
    onToggleDetails: () -> Unit,
    onToggleActive: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val dailyStr = "${LedgerCalculator.formatCurrency(breakdown.dailyCostYuan)}${strings.perDayUnit}"
    val cycleAmount = LedgerCalculator.formatMoney(entry.amountCents, entry.currency)
    val cycleLabel = strings.cycleName(
        entry.billingCycle,
        entry.customCycleDays,
        entry.customCycleUnit,
    )

    CostExpandableCard(
        expanded = expanded,
        showDetailsLabel = strings.costShowDetails,
        hideDetailsLabel = strings.costHideDetails,
        copyLabel = strings.copyAction,
        onToggle = onToggleDetails,
        onCopy = onCopy,
    ) {
            CostCardHeader(
                entry = entry,
                breakdown = breakdown,
                status = when {
                    entry.isActiveCost && !breakdown.isActive -> strings.statusEnded
                    entry.isActiveCost -> strings.statusSubActive
                    else -> strings.statusSubStopped
                },
                primaryRate = dailyStr,
                secondaryRate = if (expanded) {
                    "$cycleLabel $cycleAmount · ${strings.cycleCashInPaymentMonth}"
                } else {
                    null
                },
                expanded = expanded,
            )

            CostEntryTags(strings = strings, tags = entryTags, expanded = expanded)

            if (expanded) {
                Text(
                    text = "$cycleLabel · $cycleAmount · ${strings.cycleCashInPaymentMonth}",
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = onOpen) { Text(strings.editAction) }
                    if (!(entry.isActiveCost && !breakdown.isActive)) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CostExpandableCard(
    expanded: Boolean,
    showDetailsLabel: String,
    hideDetailsLabel: String,
    copyLabel: String,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ToolboxCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onToggle,
                onClickLabel = if (expanded) hideDetailsLabel else showDetailsLabel,
                role = Role.Button,
                onLongClick = onCopy,
                onLongClickLabel = copyLabel,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}
