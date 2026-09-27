package com.example.toolbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.toolbox.fx.FxRateStore
import com.example.toolbox.fx.FxRates
import com.example.toolbox.fx.LEDGER_CURRENCIES
import com.example.toolbox.ledger.DISPOSAL_SCRAPPED
import com.example.toolbox.ledger.DISPOSAL_SOLD
import com.example.toolbox.ledger.FX_SOURCE_MANUAL
import com.example.toolbox.ledger.LedgerAccount
import com.example.toolbox.ledger.LedgerCalculator
import com.example.toolbox.ledger.LedgerEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Sell / scrap dialog for a one-time asset: picks the disposal type and date,
 * and for a sale the amount/currency/rate (with the fallback chain on
 * confirm). [onConfirm] receives (disposalType, date, saleAmountCents,
 * currency, fxRateToCny, fxRateDate, fxRateSource) — the sale fields are only
 * meaningful for DISPOSAL_SOLD.
 */
@Composable
internal fun LedgerDisposalDialog(
    strings: ToolboxStrings,
    asset: LedgerEntry,
    accounts: List<LedgerAccount> = emptyList(),
    onConfirm: (
        disposalType: String,
        date: LocalDate,
        saleAmountCents: Long,
        currency: String,
        fxRateToCny: Double,
        fxRateDate: String?,
        fxRateSource: String?,
        accountUuid: String?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val context = LocalContext.current
    val fxStore = remember { FxRateStore(context.applicationContext) }
    val today = LocalDate.now(zone)
    val purchaseDate = Instant.ofEpochMilli(asset.occurredAtMillis)
        .atZone(zone).toLocalDate()

    var isSale by rememberSaveable { mutableStateOf(true) }
    var dateEpochDay by rememberSaveable {
        mutableLongStateOf(today.toEpochDay())
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(asset.currency) }
    var useManualRate by rememberSaveable { mutableStateOf(false) }
    var manualRateText by rememberSaveable { mutableStateOf("") }
    var cachedPreview by remember { mutableStateOf<FxRates?>(null) }
    var nearestCached by remember { mutableStateOf<FxRates?>(null) }
    var confirmBusy by remember { mutableStateOf(false) }
    var rateError by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var saleAccountUuid by rememberSaveable {
        mutableStateOf(asset.accountUuid ?: "")
    }

    val disposalDate = LocalDate.ofEpochDay(dateEpochDay)
    val saleCents = ((amountText.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100.0)
        .roundToLong().coerceAtLeast(0L)
    val manualRate = if (useManualRate) {
        manualRateText.replace(',', '.').toDoubleOrNull()
    } else {
        null
    }
    val daysHeld = (ChronoUnit.DAYS.between(purchaseDate, disposalDate) + 1)
        .toInt().coerceAtLeast(1)
    val saleBase = ((saleCents * (
        if (currency == "CNY") 1.0 else (manualRate ?: cachedPreview?.cnyPerUnit(currency) ?: 1.0)
        )).roundToLong())
    val finalCents = if (isSale) {
        (asset.baseAmountCents - saleBase).coerceAtLeast(0L)
    } else {
        asset.baseAmountCents
    }

    LaunchedEffect(currency, dateEpochDay, useManualRate, isSale) {
        if (isSale && currency != "CNY" && !useManualRate) {
            cachedPreview = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fxStore.rateForDate(disposalDate, fetchIfMissing = false)
                }
            }.getOrNull()
        } else {
            cachedPreview = null
        }
        nearestCached = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                fxStore.nearestCached(disposalDate)
            }
        }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.endUseAction.removeSuffix("…").removeSuffix("...")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        true to strings.sellOption,
                        false to strings.scrapOption,
                    ).forEachIndexed { index, (sale, label) ->
                        SegmentedButton(
                            selected = isSale == sale,
                            onClick = { isSale = sale },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        ) {
                            Text(label)
                        }
                    }
                }

                Box {
                    OutlinedTextField(
                        value = disposalDate.toString(),
                        onValueChange = {},
                        label = { Text(strings.disposalDateLabel) },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true },
                    )
                }

                if (accounts.isNotEmpty()) {
                    Text(
                        text = strings.accountFieldLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AccountChips(
                        accounts = accounts,
                        selected = saleAccountUuid,
                        onSelect = { saleAccountUuid = it },
                    )
                }

                if (isSale) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        LEDGER_CURRENCIES.forEachIndexed { index, code ->
                            SegmentedButton(
                                selected = currency == code,
                                onClick = { currency = code },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = LEDGER_CURRENCIES.size,
                                ),
                            ) {
                                Text(code)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(strings.saleAmountLabel) },
                        prefix = { Text(LedgerCalculator.currencySymbol(currency)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = attempted && saleCents <= 0L,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (currency != "CNY") {
                        Text(
                            text = when {
                                useManualRate -> manualRate?.takeIf { it > 0 }?.let {
                                    strings.rateLineManual(
                                        currency,
                                        String.format(Locale.US, "%.6f", it)
                                            .trimEnd('0').trimEnd('.'),
                                    )
                                } ?: strings.rateFetchOnSave
                                cachedPreview != null -> cachedPreview!!
                                    .cnyPerUnit(currency)
                                    ?.let {
                                        strings.rateLineCached(
                                            currency,
                                            String.format(Locale.US, "%.6f", it)
                                                .trimEnd('0').trimEnd('.'),
                                            cachedPreview!!.date,
                                        ) + " · " +
                                            strings.fxSourceName(cachedPreview!!.source.id)
                                    }
                                    ?: strings.rateFetchOnSave
                                else -> strings.rateFetchOnSave
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = strings.manualRateSwitch,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(checked = useManualRate, onCheckedChange = { useManualRate = it })
                        }
                        if (useManualRate) {
                            OutlinedTextField(
                                value = manualRateText,
                                onValueChange = { manualRateText = it },
                                label = { Text(strings.manualRateFieldLabel(currency)) },
                                suffix = { Text("CNY") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                ),
                                singleLine = true,
                                isError = attempted && (manualRate == null || manualRate <= 0),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (rateError) {
                            Text(
                                text = strings.fxAllSourcesFailed,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            nearestCached?.let { cached ->
                                TextButton(
                                    onClick = {
                                        rateError = false
                                        onConfirm(
                                            DISPOSAL_SOLD,
                                            disposalDate,
                                            saleCents,
                                            currency,
                                            cached.cnyPerUnit(currency) ?: return@TextButton,
                                            cached.date,
                                            cached.source.id,
                                            saleAccountUuid.ifBlank { null },
                                        )
                                    },
                                ) {
                                    Text(
                                        strings.useNearestCachedRate(
                                            cached.date,
                                            strings.fxSourceName(cached.source.id),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = strings.disposalPreview(
                        daysHeld,
                        LedgerCalculator.formatCurrency(finalCents / 100.0),
                        LedgerCalculator.formatCurrency(finalCents / 100.0 / daysHeld),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    attempted = true
                    if (!isSale) {
                        onConfirm(DISPOSAL_SCRAPPED, disposalDate, 0L, "CNY", 1.0, null, null,
                            saleAccountUuid.ifBlank { null })
                    } else if (currency == "CNY") {
                        if (saleCents > 0L) {
                            onConfirm(DISPOSAL_SOLD, disposalDate, saleCents, "CNY", 1.0, null, null,
                                saleAccountUuid.ifBlank { null })
                        }
                    } else if (useManualRate) {
                        manualRate?.takeIf { it > 0 }?.let {
                            onConfirm(
                                DISPOSAL_SOLD, disposalDate, saleCents, currency,
                                it, null, FX_SOURCE_MANUAL,
                                saleAccountUuid.ifBlank { null },
                            )
                        }
                    } else if (saleCents > 0L) {
                        cachedPreview?.cnyPerUnit(currency)?.let { rate ->
                            onConfirm(
                                DISPOSAL_SOLD, disposalDate, saleCents, currency,
                                rate, cachedPreview!!.date, cachedPreview!!.source.id,
                                saleAccountUuid.ifBlank { null },
                            )
                        } ?: run {
                            confirmBusy = true
                            rateError = false
                        }
                    }
                },
                enabled = !confirmBusy && (!isSale || saleCents > 0L),
            ) {
                if (confirmBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(strings.confirmAction)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
        },
    )

    if (showDatePicker) {
        ToolboxDatePickerDialog(
            initial = disposalDate,
            minDate = purchaseDate,
            maxDate = today,
            strings = strings,
            onConfirm = { dateEpochDay = it.toEpochDay(); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }

    // Fetch the disposal date's rate through the fallback chain when needed.
    if (confirmBusy) {
        LaunchedEffect(Unit) {
            val resolved = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fxStore.ratesForDates(setOf(disposalDate), fetchIfMissing = true)
                }
            }.getOrNull()
            confirmBusy = false
            val rates = resolved?.get(disposalDate)
            val rate = rates?.cnyPerUnit(currency)
            if (rates != null && rate != null && rate > 0) {
                onConfirm(
                    DISPOSAL_SOLD, disposalDate, saleCents, currency,
                    rate, rates.date, rates.source.id,
                    saleAccountUuid.ifBlank { null },
                )
            } else {
                rateError = true
            }
        }
    }
}
