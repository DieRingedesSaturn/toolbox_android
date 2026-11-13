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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import java.time.LocalDate
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
        LedgerEntryType.EXPENSE -> "−"
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
    showDateInSubtitle: Boolean = false,
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
        else -> (if (isExpense) "−" else "+") +
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
            val kindSuffix = when (entry.type) {
                LedgerEntryType.TRANSFER -> strings.transferLine(
                    accountName(accounts, entry.accountUuid),
                    accountName(accounts, entry.toAccountUuid),
                )
                LedgerEntryType.ADJUSTMENT -> strings.adjustmentTypeLabel
                else -> when (entry.linkType) {
                    LINK_TYPE_RENEWAL -> strings.renewalTagLabel
                    LINK_TYPE_SALE -> strings.saleTagLabel
                    else -> when (entry.costTrackingMode) {
                        CostTrackingMode.ONE_TIME_AMORTIZED -> strings.assetTag
                        CostTrackingMode.PERIODIC_SUBSCRIPTION -> strings.subTag
                        CostTrackingMode.NONE -> ""
                    }
                }
            }
            val tagNames = liveTags.take(2).joinToString("、") { it.name } +
                if (liveTags.size > 2) " +${liveTags.size - 2}" else ""
            val tagPart = tagNames.ifBlank {
                if (liveTags.isEmpty() && !showDateInSubtitle && kindSuffix.isEmpty() && entry.note.isBlank()) {
                    strings.untaggedLabel
                } else {
                    ""
                }
            }.let {
                if (it == entry.title.trim()) "" else it
            }
            val datePart = if (showDateInSubtitle) {
                strings.formatLedgerDay(
                    Instant.ofEpochMilli(entry.occurredAtMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate(),
                )
            } else {
                ""
            }
            val acctPart = if (entry.type != LedgerEntryType.TRANSFER) {
                entry.accountUuid?.let { id -> accounts.firstOrNull { it.uuid == id }?.name }.orEmpty()
            } else {
                ""
            }.let { if (it == entry.title.trim() || it == tagPart) "" else it }
            val subtitleText = listOf(datePart, tagPart, acctPart, kindSuffix, entry.note.trim())
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (subtitleText.isNotBlank()) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val tabularTitle = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum")
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amountStr,
                style = tabularTitle,
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
    referenceEntries: List<LedgerEntry> = entries,
) {
    val selecting = selectedUuids.isNotEmpty()
    val zone = ZoneId.systemDefault()
    val monthSummary = remember(entries, selectedMonth, referenceEntries) {
        LedgerCalculator.monthSummary(entries, selectedMonth, zone, referenceEntries)
    }
    val dayGroups = remember(entries, selectedMonth) {
        LedgerCalculator.groupByDay(entries, selectedMonth, zone)
    }
    val currentMonth = YearMonth.now(zone)
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    if (showMonthPicker) {
        LedgerMonthPickerDialog(
            initial = selectedMonth,
            latest = currentMonth,
            strings = strings,
            onConfirm = {
                onMonthChange(it)
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false },
        )
    }

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
                LedgerTagFilterBar(
                    strings = strings,
                    selectedCount = filterSelected.size,
                    onOpenFilter = onOpenFilter,
                    onClearFilter = onClearFilter,
                    onManageTags = onManageTags,
                )
            }
        }

        item {
            ToolboxCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
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
                        TextButton(
                            onClick = { showMonthPicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = strings.formatLedgerMonth(selectedMonth),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
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
                        text = "${strings.monthlyRegularExpenseLabel} ${LedgerCalculator.formatCurrency(monthSummary.regularExpenseCents / 100.0)}" +
                            " · ${strings.monthIncomeLabel} ${LedgerCalculator.formatCurrency(monthSummary.incomeCents / 100.0)}" +
                            " · ${strings.monthNetLabel} ${LedgerCalculator.formatCurrency(monthSummary.regularNetCents / 100.0)}",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (monthSummary.expenseCents != monthSummary.regularExpenseCents) {
                        Text(
                            text = strings.actualMonthExpenseLine(
                                LedgerCalculator.formatCurrency(monthSummary.expenseCents / 100.0),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        if (dayGroups.isEmpty()) {
            item {
                ToolboxCard(
                    modifier = Modifier.fillMaxWidth(),
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
                item(key = "card-${group.date}") {
                    ToolboxCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                LedgerDayHeader(
                                    strings = strings,
                                    date = group.date,
                                    expenseCents = group.expenseCents,
                                    incomeCents = group.incomeCents,
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
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

@Composable
private fun LedgerDayHeader(
    strings: ToolboxStrings,
    date: LocalDate,
    expenseCents: Long,
    incomeCents: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.formatLedgerDay(date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = buildString {
                if (expenseCents > 0L) {
                    append("−${LedgerCalculator.formatCurrency(expenseCents / 100.0)}")
                }
                if (incomeCents > 0L) {
                    if (isNotEmpty()) append("  ")
                    append("+${LedgerCalculator.formatCurrency(incomeCents / 100.0)}")
                }
            },
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Flat cross-month result list for the ledger search box. [results] must
 * already be matched and sorted newest-first (see
 * [LedgerCalculator.searchEntries]).
 */
@Composable
internal fun LedgerSearchResults(
    strings: ToolboxStrings,
    results: List<LedgerEntry>,
    tags: List<LedgerTag>,
    accounts: List<LedgerAccount>,
    onOpenEntry: (LedgerEntry) -> Unit,
    onCopyEntry: (LedgerEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val groups = remember(results) {
        results
            .groupBy {
                Instant.ofEpochMilli(it.occurredAtMillis).atZone(zone).toLocalDate()
            }
            .toList()
            .sortedByDescending { it.first }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (results.isEmpty()) {
            item {
                ToolboxCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = strings.searchEmptyResult,
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item(key = "result-count") {
                ToolboxCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = strings.searchResultCount(results.size),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            groups.forEach { (date, dayEntries) ->
                item(key = "scard-$date") {
                    ToolboxCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                LedgerDayHeader(
                                    strings = strings,
                                    date = date,
                                    expenseCents = dayEntries
                                        .filter { it.type == LedgerEntryType.EXPENSE }
                                        .sumOf { it.baseAmountCents },
                                    incomeCents = dayEntries
                                        .filter { it.type == LedgerEntryType.INCOME }
                                        .sumOf { it.baseAmountCents },
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                            dayEntries.forEachIndexed { index, entry ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant
                                            .copy(alpha = 0.5f),
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
                        }
                    }
                }
            }
        }
    }
}
