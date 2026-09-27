package com.example.toolbox.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolbox.R
import com.example.toolbox.ledger.CostTrackingMode
import com.example.toolbox.ledger.LINK_TYPE_RENEWAL
import com.example.toolbox.ledger.LINK_TYPE_SALE
import com.example.toolbox.ledger.LedgerAccount
import com.example.toolbox.ledger.LedgerCalculator
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerEntryType
import com.example.toolbox.ledger.LedgerTag
import com.example.toolbox.ledger.TagFilterMode
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

private fun accountName(accounts: List<LedgerAccount>, uuid: String?): String =
    uuid?.let { id -> accounts.firstOrNull { it.uuid == id }?.name } ?: "?"

internal fun ledgerEntryLine(
    strings: ToolboxStrings,
    entry: LedgerEntry,
    tagNames: List<String>,
    accountNames: Map<String, String> = emptyMap(),
): String {
    val sign = when (entry.type) {
        LedgerEntryType.EXPENSE -> "-"
        LedgerEntryType.INCOME -> "+"
        // Transfers/adjustments are balance moves, not spend/income.
        LedgerEntryType.TRANSFER, LedgerEntryType.ADJUSTMENT -> ""
    }
    val amount = "$sign${LedgerCalculator.formatMoney(entry.amountCents, entry.currency)}"
    val cnySuffix = if (entry.currency != "CNY") {
        " (≈ ${LedgerCalculator.formatCurrency(entry.baseAmountCents / 100.0)})"
    } else {
        ""
    }
    val date = strings.formatLedgerDay(
        Instant.ofEpochMilli(entry.occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate(),
    )
    val linkTag = when (entry.linkType) {
        LINK_TYPE_RENEWAL -> " · ${strings.renewalTagLabel}"
        LINK_TYPE_SALE -> " · ${strings.saleTagLabel}"
        else -> ""
    }
    val kindTag = when (entry.type) {
        LedgerEntryType.TRANSFER -> " · " + strings.transferLine(
            accountNames[entry.accountUuid] ?: "",
            accountNames[entry.toAccountUuid] ?: "",
        )
        LedgerEntryType.ADJUSTMENT -> " · ${strings.adjustmentTypeLabel}"
        else -> ""
    }
    return "${entry.title}: $amount$cnySuffix · " +
        (tagNames.joinToString("、").ifBlank { strings.untaggedLabel }) +
        linkTag + kindTag + " · $date"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LedgerEntryRow(
    strings: ToolboxStrings,
    entry: LedgerEntry,
    tags: List<LedgerTag>,
    accounts: List<LedgerAccount>,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val liveTags = entry.tagUuids.mapNotNull { uuid ->
        tags.firstOrNull { it.uuid == uuid && it.deletedAtMillis == null }
    }
    val avatarTag = liveTags.firstOrNull()
    val isExpense = entry.type == LedgerEntryType.EXPENSE
    val isNeutral = entry.type == LedgerEntryType.TRANSFER ||
        entry.type == LedgerEntryType.ADJUSTMENT
    val amountStr = when (entry.type) {
        LedgerEntryType.TRANSFER -> "→ ${LedgerCalculator.formatMoney(
            entry.amountCents,
            entry.currency,
        )}"
        LedgerEntryType.ADJUSTMENT -> strings.signedAmount(
            LedgerCalculator.formatMoney(
                kotlin.math.abs(entry.amountCents),
                entry.currency,
            ),
            entry.amountCents < 0,
        )
        else -> (if (isExpense) "-" else "+") +
            LedgerCalculator.formatMoney(entry.amountCents, entry.currency)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                } else {
                    Color.Transparent
                },
            )
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onCopy,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Color(avatarTag?.colorArgb ?: 0xFF9DA9A0.toInt()).copy(alpha = 0.16f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = avatarTag?.emoji ?: "🏷", fontSize = 17.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val tag = buildString {
                when (entry.type) {
                    LedgerEntryType.TRANSFER -> append(
                        " · " + strings.transferLine(
                            accountName(accounts, entry.accountUuid),
                            accountName(accounts, entry.toAccountUuid),
                        ),
                    )
                    LedgerEntryType.ADJUSTMENT -> append(" · ${strings.adjustmentTypeLabel}")
                    else -> {
                        when (entry.linkType) {
                            LINK_TYPE_RENEWAL -> append(" · ${strings.renewalTagLabel}")
                            LINK_TYPE_SALE -> append(" · ${strings.saleTagLabel}")
                            else -> when (entry.costTrackingMode) {
                                CostTrackingMode.ONE_TIME_AMORTIZED ->
                                    append(" · ${strings.assetTag}")
                                CostTrackingMode.PERIODIC_SUBSCRIPTION ->
                                    append(" · ${strings.subTag}")
                                CostTrackingMode.NONE -> {}
                            }
                        }
                    }
                }
            }
            val tagNames = liveTags.take(2).joinToString("、") { it.name } +
                if (liveTags.size > 2) " +${liveTags.size - 2}" else ""
            val noteSuffix = if (entry.note.isNotBlank()) " · ${entry.note}" else ""
            Text(
                text = tagNames.ifBlank { strings.untaggedLabel } + tag + noteSuffix,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amountStr,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    isNeutral -> MaterialTheme.colorScheme.onSurfaceVariant
                    isExpense -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.primary
                },
            )
            if (entry.currency != "CNY") {
                Text(
                    text = "≈ " + LedgerCalculator.formatCurrency(
                        entry.baseAmountCents / 100.0,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun LedgerTransactionsTab(
    strings: ToolboxStrings,
    entries: List<LedgerEntry>,
    tags: List<LedgerTag>,
    accounts: List<LedgerAccount>,
    selectedMonth: YearMonth,
    filterSelected: Set<String>,
    filterMode: TagFilterMode,
    onOpenFilter: () -> Unit,
    onClearFilter: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onOpenEntry: (LedgerEntry) -> Unit,
    onCopyEntry: (LedgerEntry) -> Unit,
    modifier: Modifier = Modifier,
    selectedUuids: Set<String> = emptySet(),
    onToggleSelect: (LedgerEntry) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onBulkAddTag: () -> Unit = {},
    onBulkRemoveTag: () -> Unit = {},
    onBulkCopy: () -> Unit = {},
    onManageTags: () -> Unit = {},
) {
    val selecting = selectedUuids.isNotEmpty()
    val zone = ZoneId.systemDefault()
    val monthSummary = remember(entries, selectedMonth) {
        LedgerCalculator.monthSummary(entries, selectedMonth, zone)
    }
    val dayGroups = remember(entries, selectedMonth) {
        LedgerCalculator.groupByDay(entries, selectedMonth, zone)
    }
    val currentMonth = YearMonth.now(zone)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            if (selecting) {
                // Contextual bar while entries are multi-selected.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClearSelection) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = strings.cancelAction,
                        )
                    }
                    Text(
                        text = strings.selectedCountLabel(selectedUuids.size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onBulkAddTag) {
                        Text(strings.addTagsAction)
                    }
                    TextButton(onClick = onBulkRemoveTag) {
                        Text(strings.removeTagsAction)
                    }
                    TextButton(onClick = onBulkCopy) {
                        Text(strings.copyAction)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = filterSelected.isNotEmpty(),
                        onClick = onOpenFilter,
                        label = { Text(strings.tagFilterAction) },
                    )
                    val selectedTags = filterSelected.mapNotNull { uuid ->
                        tags.firstOrNull { it.uuid == uuid }
                    }
                    selectedTags.take(3).forEach { tag ->
                        Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                        FilterChip(
                            selected = true,
                            onClick = onOpenFilter,
                            label = {
                                Text(
                                    (if (tag.emoji.isNotBlank()) "${tag.emoji} " else "") +
                                        tag.name,
                                )
                            },
                        )
                    }
                    if (selectedTags.size > 3) {
                        Text(
                            text = " +${selectedTags.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (filterSelected.isNotEmpty()) {
                        TextButton(onClick = onClearFilter) {
                            Text(strings.clearFilter)
                        }
                    }
                    TextButton(onClick = onManageTags) {
                        Text(strings.manageTagsChip)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onMonthChange(selectedMonth.minusMonths(1)) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = strings.prevMonth,
                    )
                }
                Text(
                    text = strings.formatLedgerMonth(selectedMonth),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                IconButton(
                    onClick = { onMonthChange(selectedMonth.plusMonths(1)) },
                    enabled = selectedMonth < currentMonth,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = strings.nextMonth,
                    )
                }
            }
            Text(
                text = "${strings.monthExpenseLabel} ${LedgerCalculator.formatCurrency(monthSummary.expenseCents / 100.0)}" +
                    " · ${strings.monthIncomeLabel} ${LedgerCalculator.formatCurrency(monthSummary.incomeCents / 100.0)}" +
                    " · ${strings.monthNetLabel} ${LedgerCalculator.formatCurrency(monthSummary.netCents / 100.0)}",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (dayGroups.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Text(
                        text = strings.emptyMonthEntries,
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            dayGroups.forEach { group ->
                item(key = "day-${group.date}") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = strings.formatLedgerDay(group.date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = buildString {
                                if (group.expenseCents > 0L) {
                                    append("-${LedgerCalculator.formatCurrency(group.expenseCents / 100.0)}")
                                }
                                if (group.incomeCents > 0L) {
                                    if (isNotEmpty()) append("  ")
                                    append("+${LedgerCalculator.formatCurrency(group.incomeCents / 100.0)}")
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "card-${group.date}") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Column {
                            group.entries.forEachIndexed { index, entry ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    )
                                }
                                val selectable = entry.type == LedgerEntryType.EXPENSE ||
                                    entry.type == LedgerEntryType.INCOME
                                LedgerEntryRow(
                                    strings = strings,
                                    entry = entry,
                                    tags = tags,
                                    accounts = accounts,
                                    selected = entry.uuid in selectedUuids,
                                    onOpen = {
                                        if (selecting) {
                                            if (selectable) onToggleSelect(entry)
                                        } else {
                                            onOpenEntry(entry)
                                        }
                                    },
                                    // Long-press toggles selectable rows,
                                    // copies transfers/adjustments as before.
                                    onCopy = {
                                        if (selectable) {
                                            onToggleSelect(entry)
                                        } else {
                                            onCopyEntry(entry)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
