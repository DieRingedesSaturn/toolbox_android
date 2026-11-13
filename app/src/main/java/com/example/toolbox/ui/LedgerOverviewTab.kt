package com.example.toolbox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolbox.ledger.BudgetProgress
import com.example.toolbox.ledger.DueRenewal
import com.example.toolbox.ledger.LedgerAccount
import com.example.toolbox.ledger.LedgerBudgets
import com.example.toolbox.ledger.LedgerCalculator
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerEntryType
import com.example.toolbox.ledger.LedgerSummary
import com.example.toolbox.ledger.LedgerTag
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

@Composable
internal fun LedgerOverviewTab(
    strings: ToolboxStrings,
    entries: List<LedgerEntry>,
    summary: LedgerSummary,
    monthSummary: LedgerCalculator.MonthSummary,
    tags: List<LedgerTag>,
    accounts: List<LedgerAccount>,
    pendingRenewals: Map<LedgerEntry, List<DueRenewal>>,
    renewalBusy: Boolean,
    onRecordRenewals: (List<DueRenewal>) -> Unit,
    onSkipRenewals: (List<DueRenewal>) -> Unit,
    onOpenEntry: (LedgerEntry) -> Unit,
    onCopyEntry: (LedgerEntry) -> Unit,
    onSeeAll: () -> Unit,
    onShowCosts: () -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    budgets: LedgerBudgets,
    onEditBudgets: () -> Unit,
    cardOrder: List<LedgerOverviewCard>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Reconcile adjustments stay out of record lists (account detail only).
    val recent = entries.filter { it.type != LedgerEntryType.ADJUSTMENT }.take(5)
    val zone = ZoneId.systemDefault()
    val trend = remember(entries) {
        LedgerCalculator.monthlyTrend(entries, YearMonth.now(zone), TREND_MONTHS, zone)
    }
    val tagRows = remember(entries, tags) {
        LedgerCalculator.tagBreakdown(
            entries,
            YearMonth.now(zone),
            zone = zone,
            tags = tags,
        )
    }
    val anyMultiTagged = remember(entries) {
        val month = YearMonth.now(zone)
        entries.any { entry ->
            entry.tagUuids.size > 1 &&
                entry.deletedAtMillis == null &&
                YearMonth.from(
                    Instant.ofEpochMilli(entry.occurredAtMillis).atZone(zone),
                ) == month
        }
    }

    val listState = rememberLazyListState()
    // Reordering keeps the previous first card anchored, which can push a newly
    // promoted card above the viewport; show the new order from the top.
    LaunchedEffect(cardOrder) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Pending renewal confirmations stay pinned above the dashboard cards.
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

        cardOrder.forEach { card ->
            when (card) {
                LedgerOverviewCard.MONTH -> item {
                    MonthSummaryCard(
                        strings = strings,
                        monthSummary = monthSummary,
                        budgets = budgets,
                        onEditBudgets = onEditBudgets,
                    )
                }

                LedgerOverviewCard.RECENT -> item {
                    RecentEntriesCard(
                        strings = strings,
                        recent = recent,
                        tags = tags,
                        accounts = accounts,
                        onOpenEntry = onOpenEntry,
                        onCopyEntry = onCopyEntry,
                        onSeeAll = onSeeAll,
                    )
                }

                LedgerOverviewCard.TAGS -> item {
                    TagBreakdownCard(
                        strings = strings,
                        tags = tags,
                        tagRows = tagRows,
                        budgets = budgets,
                        anyMultiTagged = anyMultiTagged,
                    )
                }

                LedgerOverviewCard.TREND -> item {
                    LedgerTrendCard(
                        strings = strings,
                        trend = trend,
                        onOpenMonth = onOpenMonth,
                    )
                }

                LedgerOverviewCard.COST -> item {
                    DailyCostCard(
                        strings = strings,
                        summary = summary,
                        onShowCosts = onShowCosts,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(
    strings: ToolboxStrings,
    monthSummary: LedgerCalculator.MonthSummary,
    budgets: LedgerBudgets,
    onEditBudgets: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    ToolboxCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = strings.formatLedgerMonth(monthSummary.month),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val tabularHeadline = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum")
            val netText = (if (monthSummary.regularNetCents > 0) "+" else "") +
                LedgerCalculator.formatCurrency(monthSummary.regularNetCents / 100.0)
            MonthAmountCell(
                label = strings.monthNetLabel,
                amountText = netText,
                copiedMessage = strings.copied(strings.monthNetLabel),
                amountStyle = tabularHeadline,
                amountWeight = FontWeight.Bold,
                amountColor = if (monthSummary.regularNetCents >= 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            val incomeText = "+" + LedgerCalculator.formatCurrency(monthSummary.incomeCents / 100.0)
            val ordinaryText = LedgerCalculator.formatCurrency(monthSummary.ordinaryExpenseCents / 100.0)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MonthAmountCell(
                    label = strings.monthIncomeLabel,
                    amountText = incomeText,
                    copiedMessage = strings.copied(strings.monthIncomeLabel),
                    modifier = Modifier.weight(1f),
                    amountColor = MaterialTheme.colorScheme.primary,
                )
                MonthAmountCell(
                    label = strings.ordinaryExpenseLabel,
                    amountText = ordinaryText,
                    copiedMessage = strings.copied(strings.ordinaryExpenseLabel),
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                )
            }
            val hasMonthly = monthSummary.monthlySubscriptionCents > 0L
            val hasOneTime = monthSummary.oneTimeOutlayCents > 0L
            if (hasMonthly || hasOneTime) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (hasMonthly) {
                        MonthAmountCell(
                            label = strings.monthlySubscriptionExpenseLabel,
                            amountText = LedgerCalculator.formatCurrency(
                                monthSummary.monthlySubscriptionCents / 100.0,
                            ),
                            copiedMessage = strings.copied(strings.monthlySubscriptionExpenseLabel),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (hasOneTime) {
                        MonthAmountCell(
                            label = strings.oneTimeOutlayLabel,
                            amountText = LedgerCalculator.formatCurrency(
                                monthSummary.oneTimeOutlayCents / 100.0,
                            ),
                            copiedMessage = strings.copied(strings.oneTimeOutlayLabel),
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End,
                            caption = strings.oneTimeOutlayCaption,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            val monthlyBudget = budgets.monthlyCents
            if (monthlyBudget != null) {
                val actualExpenseText = LedgerCalculator.formatCurrency(monthSummary.expenseCents / 100.0)
                Text(
                    text = strings.actualMonthExpenseLine(
                        actualExpenseText,
                        forBudget = true,
                    ),
                    modifier = Modifier.clickable {
                        copyToClipboard(
                            context = context,
                            label = strings.actualMonthExpenseLabel,
                            value = actualExpenseText,
                            copiedMessage = strings.copied(strings.actualMonthExpenseLabel),
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val progress = BudgetProgress(monthlyBudget, monthSummary.expenseCents)
                val budgetText = LedgerCalculator.formatCurrency(monthlyBudget / 100.0)
                BudgetBar(progress, Modifier.fillMaxWidth())
                Text(
                    text = if (progress.isOver) {
                        strings.budgetOver(
                            budgetText,
                            LedgerCalculator.formatCurrency(-progress.remainingCents / 100.0),
                        )
                    } else {
                        strings.budgetRemaining(
                            budgetText,
                            LedgerCalculator.formatCurrency(progress.remainingCents / 100.0),
                            progress.dailyAllowanceCents(LocalDate.now(zone), monthSummary.month)
                                ?.let { LedgerCalculator.formatCurrency(it / 100.0) },
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onEditBudgets)
                        .padding(vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (progress.isOver) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            } else {
                Text(
                    text = strings.budgetSetAction,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onEditBudgets)
                        .padding(vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = strings.monthlyRegularSpendingRule,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthAmountCell(
    label: String,
    amountText: String,
    copiedMessage: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    amountStyle: TextStyle = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
    amountWeight: FontWeight = FontWeight.SemiBold,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
    caption: String? = null,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.clickable {
            copyToClipboard(
                context = context,
                label = label,
                value = amountText,
                copiedMessage = copiedMessage,
            )
        },
        horizontalAlignment = horizontalAlignment,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = amountText,
            style = amountStyle,
            fontWeight = amountWeight,
            color = amountColor,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DailyCostCard(
    strings: ToolboxStrings,
    summary: LedgerSummary,
    onShowCosts: () -> Unit,
) {
    ToolboxCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowCosts),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = strings.dailyCostCaption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${LedgerCalculator.formatCurrency(summary.activeDailyBurnRateYuan)}${strings.perDayUnit}",
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${strings.totalYearlyCostLabel} ${LedgerCalculator.formatCurrency(summary.activeYearlyBurnRateYuan)}${strings.perYearUnit}",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = strings.costItemsCount(
                    summary.activeOneTimeCount,
                    summary.activeSubscriptionCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = strings.dailyHoldingCostRule,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TagBreakdownCard(
    strings: ToolboxStrings,
    tags: List<LedgerTag>,
    tagRows: List<Pair<LedgerTag?, Long>>,
    budgets: LedgerBudgets,
    anyMultiTagged: Boolean,
) {
    InfoCard(strings.breakdownByTags) {
        val budgetOnlyRows = tags
            .filter { tag ->
                tag.deletedAtMillis == null &&
                    tag.uuid in budgets.tagCents &&
                    tagRows.none { it.first?.uuid == tag.uuid }
            }
            .map { it to 0L }
        val displayRows = tagRows + budgetOnlyRows
        if (displayRows.isEmpty()) {
            Text(
                text = strings.noExpenseThisMonth,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val totalCents = tagRows.sumOf { it.second }.coerceAtLeast(1L)
            if (tagRows.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(10.dp)),
                ) {
                    tagRows.forEach { (tag, cents) ->
                        Box(
                            modifier = Modifier
                                .weight(cents.toFloat())
                                .fillMaxHeight()
                                .background(
                                    Color(tag?.colorArgb ?: 0xFF9DA9A0.toInt()),
                                ),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            displayRows.forEach { (tag, cents) ->
                val tagBudget = tag?.let { budgets.tagCents[it.uuid] }
                val percent = "%.0f%%".format(Locale.US, cents * 100.0 / totalCents)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                Color(tag?.colorArgb ?: 0xFF9DA9A0.toInt())
                                    .copy(alpha = 0.16f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = tag?.emoji ?: "", fontSize = 13.sp)
                    }
                    Text(
                        text = tag?.name ?: strings.untaggedLabel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = if (tagBudget != null) {
                            strings.budgetOfTotal(
                                LedgerCalculator.formatCurrency(cents / 100.0),
                                LedgerCalculator.formatCurrency(tagBudget / 100.0),
                            )
                        } else {
                            LedgerCalculator.formatCurrency(cents / 100.0)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (tagBudget != null && cents > tagBudget) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = percent,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (tagBudget != null) {
                    BudgetBar(
                        BudgetProgress(tagBudget, cents),
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 38.dp, bottom = 4.dp),
                    )
                }
            }
            if (anyMultiTagged) {
                Text(
                    text = strings.multiTagNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecentEntriesCard(
    strings: ToolboxStrings,
    recent: List<LedgerEntry>,
    tags: List<LedgerTag>,
    accounts: List<LedgerAccount>,
    onOpenEntry: (LedgerEntry) -> Unit,
    onCopyEntry: (LedgerEntry) -> Unit,
    onSeeAll: () -> Unit,
) {
    ToolboxCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.recentEntriesTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onSeeAll) {
                    Text(strings.seeAllAction)
                }
            }
            if (recent.isEmpty()) {
                Text(
                    text = strings.emptyOverviewHint,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                recent.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    LedgerEntryRow(
                        strings = strings,
                        entry = entry,
                        tags = tags,
                        accounts = accounts,
                        onOpen = { onOpenEntry(entry) },
                        onCopy = { onCopyEntry(entry) },
                        showDateInSubtitle = true,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

private const val TREND_MONTHS = 6

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LedgerTrendCard(
    strings: ToolboxStrings,
    trend: List<LedgerCalculator.MonthSummary>,
    onOpenMonth: (YearMonth) -> Unit,
) {
    val context = LocalContext.current
    val expenseColor = MaterialTheme.colorScheme.tertiary
    val incomeColor = MaterialTheme.colorScheme.primary
    val peak = trend.maxOfOrNull { maxOf(it.expenseCents, it.incomeCents) }
        ?.coerceAtLeast(1L) ?: 1L
    val average = LedgerCalculator.averageFullMonthExpense(trend)

    InfoCard(strings.trendTitle(trend.size)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TrendLegend(expenseColor, strings.trendExpenseLegend)
            TrendLegend(incomeColor, strings.monthIncomeLabel)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        ) {
            trend.forEach { month ->
                val detail = strings.trendMonthDetail(
                    month.month,
                    LedgerCalculator.formatCurrency(month.expenseCents / 100.0),
                    LedgerCalculator.formatCurrency(month.incomeCents / 100.0),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = { onOpenMonth(month.month) },
                            onLongClick = {
                                copyToClipboard(
                                    context = context,
                                    label = strings.trendTitle(trend.size),
                                    value = detail,
                                    copiedMessage = strings.copied(detail),
                                )
                            },
                        )
                        .semantics(mergeDescendants = true) { contentDescription = detail },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        TrendBar(month.expenseCents, peak, expenseColor)
                        TrendBar(month.incomeCents, peak, incomeColor)
                    }
                    Text(
                        text = strings.trendMonthLabel(month.month),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        average?.let { (months, cents) ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = strings.trendAverageExpense(
                    months,
                    LedgerCalculator.formatCurrency(cents / 100.0),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = strings.trendTapHint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrendBar(cents: Long, peak: Long, color: Color) {
    val target = if (cents > 0L) (cents.toFloat() / peak).coerceIn(0.02f, 1f) else 0f
    val fraction = remember { Animatable(0f) }
    LaunchedEffect(target) {
        fraction.animateTo(target, tween(durationMillis = 450))
    }
    Box(
        modifier = Modifier
            .width(10.dp)
            .fillMaxHeight(fraction.value)
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(color),
    )
}

@Composable
private fun TrendLegend(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BudgetBar(progress: BudgetProgress, modifier: Modifier) {
    LinearProgressIndicator(
        progress = { progress.fraction.coerceIn(0f, 1f) },
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = if (progress.isOver) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
