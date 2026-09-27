package com.example.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolbox.ledger.DueRenewal
import com.example.toolbox.ledger.LedgerAccount
import com.example.toolbox.ledger.LedgerCalculator
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerEntryType
import com.example.toolbox.ledger.LedgerSummary
import com.example.toolbox.ledger.LedgerTag
import java.time.Instant
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Reconcile adjustments stay out of record lists (account detail only).
    val recent = entries.filter { it.type != LedgerEntryType.ADJUSTMENT }.take(5)
    val zone = ZoneId.systemDefault()
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Current month hero card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
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
                    val expenseText = LedgerCalculator.formatCurrency(monthSummary.expenseCents / 100.0)
                    Text(
                        text = strings.monthExpenseLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = expenseText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable {
                            copyToClipboard(
                                context = context,
                                label = strings.monthExpenseLabel,
                                value = expenseText,
                                copiedMessage = strings.copied(strings.monthExpenseLabel),
                            )
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val incomeText = "+" + LedgerCalculator.formatCurrency(monthSummary.incomeCents / 100.0)
                        Column(
                            modifier = Modifier.clickable {
                                copyToClipboard(
                                    context = context,
                                    label = strings.monthIncomeLabel,
                                    value = incomeText,
                                    copiedMessage = strings.copied(strings.monthIncomeLabel),
                                )
                            },
                        ) {
                            Text(
                                text = strings.monthIncomeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = incomeText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        val netText = LedgerCalculator.formatCurrency(monthSummary.netCents / 100.0)
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.clickable {
                                copyToClipboard(
                                    context = context,
                                    label = strings.monthNetLabel,
                                    value = netText,
                                    copiedMessage = strings.copied(strings.monthNetLabel),
                                )
                            },
                        ) {
                            Text(
                                text = strings.monthNetLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = netText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (monthSummary.netCents >= 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                }
            }
        }

        // 1b. Pending renewal confirmations
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

        // 2. Tag breakdown for the current month's expenses
        item {
            InfoCard(strings.breakdownByTags) {
                if (tagRows.isEmpty()) {
                    Text(
                        text = strings.noExpenseThisMonth,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val totalCents = tagRows.sumOf { it.second }.coerceAtLeast(1L)
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
                    tagRows.forEach { (tag, cents) ->
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
                                text = LedgerCalculator.formatCurrency(cents / 100.0),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = percent,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        // 3. Daily cost card -> Costs tab
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowCosts),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
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
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${LedgerCalculator.formatCurrency(summary.activeMonthlyBurnRateYuan)}${strings.perMonthUnit}" +
                            " · ${LedgerCalculator.formatCurrency(summary.activeYearlyBurnRateYuan)}${strings.perYearUnit}",
                        style = MaterialTheme.typography.bodySmall,
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
                }
            }
        }

        // 4. Recent entries
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
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
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
