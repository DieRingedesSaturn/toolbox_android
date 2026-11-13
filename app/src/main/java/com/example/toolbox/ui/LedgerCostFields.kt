package com.example.toolbox.ui

import androidx.compose.runtime.saveable.listSaver
import com.example.toolbox.ledger.CostBreakdown
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.toolbox.ledger.BillingCycle
import com.example.toolbox.ledger.CostTrackingMode
import com.example.toolbox.ledger.DISPOSAL_SOLD
import com.example.toolbox.ledger.CycleUnit
import com.example.toolbox.ledger.LINK_TYPE_SALE
import com.example.toolbox.ledger.LedgerCalculator
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerEntryType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal class LedgerCostDraft(initial: LedgerEntry?, zone: ZoneId) {
    var trackCost by mutableStateOf(initial != null && initial.costTrackingMode != CostTrackingMode.NONE)
    var selectedCostMode by mutableStateOf(
        if (initial?.costTrackingMode == CostTrackingMode.PERIODIC_SUBSCRIPTION) {
            CostTrackingMode.PERIODIC_SUBSCRIPTION
        } else {
            CostTrackingMode.ONE_TIME_AMORTIZED
        },
    )
    var salvageText by mutableStateOf(
        initial?.takeIf { it.salvageValueCents > 0 }
            ?.let { "%.2f".format(Locale.US, it.salvageValue).removeSuffix(".00") }.orEmpty(),
    )
    var targetDaysText by mutableStateOf(initial?.targetDays?.toString().orEmpty())
    var billingCycle by mutableStateOf(initial?.billingCycle ?: BillingCycle.MONTHLY)
    var customCycleDaysText by mutableStateOf((initial?.customCycleDays ?: 30).toString())
    var customCycleUnit by mutableStateOf(initial?.customCycleUnit?.name ?: CycleUnit.DAYS.name)
    var costEndsEpochDay by mutableLongStateOf(
        initial?.costEndsAtMillis
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() } ?: -1L,
    )
    var showCostEndPicker by mutableStateOf(false)

    companion object {
        val Saver = listSaver<LedgerCostDraft, Any>(
            save = {
                listOf(
                    it.trackCost, it.selectedCostMode.name, it.salvageText, it.targetDaysText,
                    it.billingCycle.name, it.customCycleDaysText, it.customCycleUnit,
                    it.costEndsEpochDay, it.showCostEndPicker,
                )
            },
            restore = { values ->
                LedgerCostDraft(null, ZoneId.systemDefault()).apply {
                    trackCost = values[0] as Boolean
                    selectedCostMode = CostTrackingMode.valueOf(values[1] as String)
                    salvageText = values[2] as String
                    targetDaysText = values[3] as String
                    billingCycle = BillingCycle.valueOf(values[4] as String)
                    customCycleDaysText = values[5] as String
                    customCycleUnit = values[6] as String
                    costEndsEpochDay = values[7] as Long
                    showCostEndPicker = values[8] as Boolean
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LedgerCostFields(
    strings: ToolboxStrings,
    initialEntry: LedgerEntry?,
    parentTitle: String?,
    entryType: LedgerEntryType,
    zone: ZoneId,
    draft: LedgerCostDraft,
    previewBreakdown: CostBreakdown?,
    costEndDate: LocalDate?,
    costEndError: String?,
    endDateLifespanDays: Long?,
) = with(draft) {
    val isLinked = initialEntry?.parentUuid != null
    if (isLinked) {
        Text(
            text = if (initialEntry.linkType == LINK_TYPE_SALE) {
                strings.saleLinkedInfo(parentTitle ?: "")
            } else {
                strings.renewalOrdinalInfo(
                    initialEntry.renewalIndex ?: 0,
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (initialEntry?.disposalType != null) {
        val disposedDate = initialEntry.retiredAtMillis
            ?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString()
            } ?: ""
        Text(
            text = if (initialEntry.disposalType == DISPOSAL_SOLD) {
                strings.soldLine(disposedDate, "")
            } else {
                strings.scrappedLine(disposedDate)
            }.trimEnd(' ', '·'),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (entryType == LedgerEntryType.INCOME) {
        // Recurring income switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { trackCost = !trackCost }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.recurringIncomeOption,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Switch(
                checked = trackCost,
                onCheckedChange = { trackCost = it },
            )
        }
        if (trackCost) {
            Text(
                text = strings.billingCycleLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LedgerCycleChips(
                strings = strings,
                billingCycle = billingCycle,
                customCycleDaysText = customCycleDaysText,
                customCycleUnit = customCycleUnit,
                onCycle = { billingCycle = it },
            )
            if (billingCycle == BillingCycle.CUSTOM_DAYS) {
                CustomCycleInputs(
                    strings = strings,
                    countText = customCycleDaysText,
                    onCount = { customCycleDaysText = it },
                    unit = customCycleUnit,
                    onUnit = { customCycleUnit = it },
                )
            }
            CostEndDateRow(
                strings = strings,
                label = strings.costEndDateLabel,
                endDate = costEndDate,
                error = costEndError,
                onPick = { showCostEndPicker = true },
                onClear = { costEndsEpochDay = -1L },
            )
        }
    } else if (entryType == LedgerEntryType.EXPENSE) {
        // Average cost tracking switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { trackCost = !trackCost }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.trackAverageCostOption,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = strings.trackAverageCostHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = trackCost,
                onCheckedChange = { trackCost = it },
            )
        }

        if (trackCost) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    CostTrackingMode.ONE_TIME_AMORTIZED to strings.costModeOneTime,
                    CostTrackingMode.PERIODIC_SUBSCRIPTION to strings.costModePeriodic,
                ).forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = selectedCostMode == mode,
                        onClick = { selectedCostMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = 2,
                        ),
                    ) {
                        Text(label)
                    }
                }
            }

            if (selectedCostMode == CostTrackingMode.ONE_TIME_AMORTIZED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val presets = listOf(
                        "" to strings.lifespanActualDays,
                        "365" to strings.lifespanYearsPreset(1, 365),
                        "730" to strings.lifespanYearsPreset(2, 730),
                        "1095" to strings.lifespanYearsPreset(3, 1095),
                        "1825" to strings.lifespanYearsPreset(5, 1825),
                    )
                    presets.forEach { (daysVal, label) ->
                        FilterChip(
                            selected = endDateLifespanDays == null &&
                                targetDaysText == daysVal,
                            onClick = { targetDaysText = daysVal },
                            label = { Text(label) },
                            enabled = endDateLifespanDays == null,
                        )
                    }
                }

                OutlinedTextField(
                    value = endDateLifespanDays?.toString() ?: targetDaysText,
                    onValueChange = { targetDaysText = it },
                    label = { Text(strings.targetDaysLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = endDateLifespanDays == null,
                    supportingText = endDateLifespanDays?.let {
                        { Text(strings.lifespanFromEndDate) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                CostEndDateRow(
                    strings = strings,
                    label = strings.amortizeUntilLabel,
                    endDate = costEndDate,
                    error = costEndError,
                    onPick = { showCostEndPicker = true },
                    onClear = { costEndsEpochDay = -1L },
                )

                OutlinedTextField(
                    value = salvageText,
                    onValueChange = { salvageText = it },
                    label = { Text(strings.salvageValueLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = strings.billingCycleLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LedgerCycleChips(
                    strings = strings,
                    billingCycle = billingCycle,
                    customCycleDaysText = customCycleDaysText,
                    customCycleUnit = customCycleUnit,
                    onCycle = { billingCycle = it },
                )
                if (billingCycle == BillingCycle.CUSTOM_DAYS) {
                    CustomCycleInputs(
                        strings = strings,
                        countText = customCycleDaysText,
                        onCount = { customCycleDaysText = it },
                        unit = customCycleUnit,
                        onUnit = { customCycleUnit = it },
                    )
                }

                CostEndDateRow(
                    strings = strings,
                    label = strings.costEndDateLabel,
                    endDate = costEndDate,
                    error = costEndError,
                    onPick = { showCostEndPicker = true },
                    onClear = { costEndsEpochDay = -1L },
                )
            }

            if (previewBreakdown != null) {
                val daily = LedgerCalculator.formatCurrency(previewBreakdown.dailyCostYuan)
                val yearly = LedgerCalculator.formatCurrency(previewBreakdown.yearlyCostYuan)
                val previewText = strings.holdingCostPreview(daily, yearly)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(12.dp),
                ) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }

}
