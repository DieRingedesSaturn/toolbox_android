package com.example.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.toolbox.R
import com.example.toolbox.fx.FxException
import com.example.toolbox.fx.FxFailure
import com.example.toolbox.fx.FxRateStore
import com.example.toolbox.fx.FxRates
import com.example.toolbox.fx.LEDGER_CURRENCIES
import com.example.toolbox.ledger.BillingCycle
import com.example.toolbox.ledger.CostTrackingMode
import com.example.toolbox.ledger.DISPOSAL_SOLD
import com.example.toolbox.ledger.CycleUnit
import com.example.toolbox.ledger.FX_SOURCE_MANUAL
import com.example.toolbox.ledger.LINK_TYPE_SALE
import com.example.toolbox.ledger.LedgerAccount
import com.example.toolbox.ledger.LedgerAccounts
import com.example.toolbox.ledger.LedgerTag
import com.example.toolbox.ledger.LedgerCalculator
import com.example.toolbox.ledger.LedgerCategory
import com.example.toolbox.ledger.LedgerEntry
import com.example.toolbox.ledger.LedgerEntryType
import com.example.toolbox.ledger.LedgerSyncStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LedgerEntryEditor(
    strings: ToolboxStrings,
    initialEntry: LedgerEntry?,
    draftAmountCents: Long? = null,
    draftOccurredAtMillis: Long? = null,
    tags: List<LedgerTag>,
    accounts: List<LedgerAccount> = emptyList(),
    defaultAccountUuid: String = LedgerAccounts.DEFAULT_ACCOUNT_UUID,
    parentTitle: String?,
    onCreateTag: (LedgerTag) -> Unit,
    onManageTags: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val nowMillis = remember { System.currentTimeMillis() }
    var attemptedSave by remember { mutableStateOf(false) }
    val isLinked = initialEntry?.parentUuid != null

    var entryType by rememberSaveable {
        mutableStateOf(initialEntry?.type ?: LedgerEntryType.EXPENSE)
    }
    var title by rememberSaveable {
        mutableStateOf(initialEntry?.title ?: "")
    }
    var amountText by rememberSaveable {
        mutableStateOf(
            initialEntry?.let { "%.2f".format(Locale.US, it.amountValue).removeSuffix(".00") }
                ?: draftAmountCents?.let {
                    "%.2f".format(Locale.US, it / 100.0).removeSuffix(".00")
                } ?: "",
        )
    }
    var tagUuidsText by rememberSaveable {
        mutableStateOf(initialEntry?.tagUuids?.joinToString(",") ?: "")
    }
    val selectedTagUuids = tagUuidsText.split(',')
        .filter { it.isNotBlank() }
        .toSet()
    val liveTags = tags.filter { it.deletedAtMillis == null }
    val selectedTags = liveTags.filter { it.uuid in selectedTagUuids }
    var showTagCreate by rememberSaveable { mutableStateOf(false) }

    val initialDate = remember(initialEntry, draftOccurredAtMillis) {
        (initialEntry?.occurredAtMillis ?: draftOccurredAtMillis)
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    }
    var selectedEpochDay by rememberSaveable {
        mutableLongStateOf(
            (initialDate ?: LocalDate.now(zone)).toEpochDay(),
        )
    }
    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable {
        mutableStateOf(initialEntry?.note ?: "")
    }

    var trackCost by rememberSaveable {
        mutableStateOf(
            initialEntry != null && initialEntry.costTrackingMode != CostTrackingMode.NONE,
        )
    }
    var selectedCostMode by rememberSaveable {
        mutableStateOf(
            when (initialEntry?.costTrackingMode) {
                CostTrackingMode.PERIODIC_SUBSCRIPTION -> CostTrackingMode.PERIODIC_SUBSCRIPTION
                else -> CostTrackingMode.ONE_TIME_AMORTIZED
            },
        )
    }
    var salvageText by rememberSaveable {
        mutableStateOf(
            initialEntry?.takeIf { it.salvageValueCents > 0 }
                ?.let { "%.2f".format(Locale.US, it.salvageValue).removeSuffix(".00") } ?: "",
        )
    }
    var targetDaysText by rememberSaveable {
        mutableStateOf(initialEntry?.targetDays?.toString() ?: "")
    }
    var billingCycle by rememberSaveable {
        mutableStateOf(initialEntry?.billingCycle ?: BillingCycle.MONTHLY)
    }
    var customCycleDaysText by rememberSaveable {
        mutableStateOf((initialEntry?.customCycleDays ?: 30).toString())
    }
    var customCycleUnit by rememberSaveable {
        mutableStateOf(initialEntry?.customCycleUnit?.name ?: CycleUnit.DAYS.name)
    }
    var accountUuid by rememberSaveable {
        mutableStateOf(initialEntry?.accountUuid ?: defaultAccountUuid)
    }
    var toAccountUuid by rememberSaveable {
        mutableStateOf(initialEntry?.toAccountUuid ?: "")
    }
    var accountAmountText by rememberSaveable {
        mutableStateOf(
            initialEntry?.accountAmountCents
                ?.let { "%.2f".format(Locale.US, it / 100.0) } ?: "",
        )
    }
    var toAmountText by rememberSaveable {
        mutableStateOf(
            initialEntry?.toAmountCents
                ?.let { "%.2f".format(Locale.US, it / 100.0) } ?: "",
        )
    }
    var accountAmountEdited by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fxStore = remember { FxRateStore(context.applicationContext) }

    var currency by rememberSaveable {
        mutableStateOf(initialEntry?.currency ?: "CNY")
    }
    var lockedRate by rememberSaveable {
        mutableStateOf(
            initialEntry?.takeIf { it.currency != "CNY" }?.fxRateToCny,
        )
    }
    var lockedRateDate by rememberSaveable {
        mutableStateOf(
            initialEntry?.takeIf { it.currency != "CNY" }?.fxRateDate,
        )
    }
    var lockedRateSource by rememberSaveable {
        mutableStateOf(
            initialEntry?.takeIf { it.currency != "CNY" }?.fxRateSource,
        )
    }
    var useManualRate by rememberSaveable { mutableStateOf(false) }
    var manualRateText by rememberSaveable { mutableStateOf("") }
    var rateFetching by remember { mutableStateOf(false) }
    var rateError by remember { mutableStateOf<FxException?>(null) }
    var cachedRatePreview by remember { mutableStateOf<FxRates?>(null) }
    var latestCachedRate by remember { mutableStateOf<FxRates?>(null) }

    LaunchedEffect(currency, selectedEpochDay, useManualRate) {
        if (currency == "CNY" || lockedRate != null || useManualRate) {
            cachedRatePreview = null
        } else {
            cachedRatePreview = withContext(Dispatchers.IO) {
                fxStore.rateForDate(selectedDate, fetchIfMissing = false)
            }
        }
        latestCachedRate = withContext(Dispatchers.IO) {
            fxStore.nearestCached(selectedDate)
        }
    }

    val currencyFractionDigits = runCatching {
        Currency.getInstance(currency).defaultFractionDigits
    }.getOrDefault(2)
    val decimalsForbidden = currencyFractionDigits <= 0 &&
        amountText.replace(',', '.').contains('.')
    val manualRate = if (useManualRate) {
        manualRateText.replace(',', '.').toDoubleOrNull()
    } else {
        null
    }
    val previewFxRate = when {
        currency == "CNY" -> 1.0
        useManualRate -> manualRate?.takeIf { it > 0 } ?: 1.0
        lockedRate != null -> lockedRate ?: 1.0
        else -> cachedRatePreview?.cnyPerUnit(currency) ?: 1.0
    }

    fun formatRate(rate: Double): String =
        String.format(Locale.US, "%.6f", rate).trimEnd('0').trimEnd('.')

    val isTransfer = entryType == LedgerEntryType.TRANSFER
    val isAdjustment = entryType == LedgerEntryType.ADJUSTMENT
    // Adjustments are signed corrections; income/expense amounts stay positive.
    val previewAmountCents = ((amountText.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100.0)
        .roundToLong().let { if (isAdjustment) it else it.coerceAtLeast(0L) }
    val amountValid = if (isAdjustment) previewAmountCents != 0L else previewAmountCents > 0L
    val previewSalvageCents = ((salvageText.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100.0)
        .roundToLong().coerceAtLeast(0L)
    val originalOccurredAtMillis = initialEntry?.occurredAtMillis ?: draftOccurredAtMillis
    val occurredAtMillis = if (originalOccurredAtMillis != null && selectedDate == initialDate) {
        originalOccurredAtMillis
    } else {
        val timeOfDay = Instant.ofEpochMilli(originalOccurredAtMillis ?: nowMillis)
            .atZone(zone).toLocalTime()
        selectedDate.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli()
    }
    val effectiveMode = when {
        isLinked -> initialEntry.costTrackingMode
        entryType == LedgerEntryType.INCOME ->
            if (trackCost) CostTrackingMode.PERIODIC_SUBSCRIPTION else CostTrackingMode.NONE
        trackCost -> selectedCostMode
        else -> CostTrackingMode.NONE
    }

    val liveAccounts = accounts.filter { it.deletedAtMillis == null }
    val pickedAccount = liveAccounts.firstOrNull { it.uuid == accountUuid }
    val pickedToAccount = liveAccounts.firstOrNull { it.uuid == toAccountUuid }
    val effectiveCurrency = if (isTransfer) {
        pickedAccount?.currency ?: "CNY"
    } else {
        currency
    }
    val entryRateForAccount = when (effectiveCurrency) {
        "CNY" -> 1.0
        else -> lockedRate ?: cachedRatePreview?.cnyPerUnit(effectiveCurrency)
    }

    // Auto-fill the account-currency amount when it differs from the entry's
    // currency, using the same rate table once resolved. User edits win.
    LaunchedEffect(accountUuid, currency, amountText, cachedRatePreview, lockedRate) {
        if (accountAmountEdited || isTransfer) return@LaunchedEffect
        val acc = pickedAccount ?: return@LaunchedEffect
        if (acc.currency == currency) {
            if (accountAmountText.isNotEmpty()) accountAmountText = ""
            return@LaunchedEffect
        }
        val cents = amountText.replace(',', '.').toDoubleOrNull()
            ?.times(100)?.toLong() ?: return@LaunchedEffect
        val accRate = if (acc.currency == "CNY") {
            1.0
        } else {
            cachedRatePreview?.cnyPerUnit(acc.currency) ?: return@LaunchedEffect
        }
        val eRate = entryRateForAccount ?: return@LaunchedEffect
        val suggested = LedgerAccounts.accountAmountForEntry(cents, eRate, accRate)
        accountAmountText = "%.2f".format(Locale.US, suggested / 100.0)
    }

    val previewEntry = LedgerEntry(
        uuid = initialEntry?.uuid ?: UUID.randomUUID().toString(),
        title = title.ifBlank {
            when {
                isAdjustment -> strings.adjustmentTitleText
                isTransfer -> strings.transferTypeLabel
                else -> selectedTags.firstOrNull()?.name
                    ?: strings.ledgerCategory(LedgerCategory.OTHER)
            }
        },
        amountCents = previewAmountCents,
        currency = effectiveCurrency,
        fxRateToCny = previewFxRate,
        fxRateDate = if (currency == "CNY" || useManualRate) {
            null
        } else {
            lockedRateDate ?: cachedRatePreview?.date
        },
        fxRateSource = if (currency == "CNY") {
            null
        } else if (useManualRate) {
            FX_SOURCE_MANUAL
        } else {
            lockedRateSource ?: cachedRatePreview?.source?.id
        },
        type = entryType,
        category = initialEntry?.category ?: LedgerCategory.OTHER.name,
        occurredAtMillis = occurredAtMillis,
        note = note,
        costTrackingMode = if (isTransfer || isAdjustment) {
            CostTrackingMode.NONE
        } else {
            effectiveMode
        },
        salvageValueCents = previewSalvageCents,
        targetDays = targetDaysText.toIntOrNull()?.takeIf { it > 0 },
        retiredAtMillis = initialEntry?.retiredAtMillis,
        billingCycle = billingCycle,
        customCycleDays = (customCycleDaysText.toIntOrNull() ?: 30).coerceAtLeast(1),
        customCycleUnit = runCatching { CycleUnit.valueOf(customCycleUnit) }
            .getOrDefault(CycleUnit.DAYS),
        isActiveCost = initialEntry?.isActiveCost ?: true,
        parentUuid = initialEntry?.parentUuid,
        linkType = initialEntry?.linkType,
        renewalIndex = initialEntry?.renewalIndex,
        disposalType = initialEntry?.disposalType,
        tagUuids = if (isTransfer || isAdjustment) {
            emptyList()
        } else {
            selectedTags.map { it.uuid }
        },
        accountUuid = accountUuid.ifBlank { defaultAccountUuid },
        accountAmountCents = if (
            !isTransfer && !isAdjustment && pickedAccount != null &&
            pickedAccount.currency != effectiveCurrency
        ) {
            accountAmountText.replace(',', '.')
                .toDoubleOrNull()?.times(100)?.toLong()
        } else {
            null
        },
        toAccountUuid = if (isTransfer) toAccountUuid.ifBlank { null } else null,
        toAmountCents = if (
            isTransfer && pickedToAccount != null &&
            pickedToAccount.currency != effectiveCurrency
        ) {
            toAmountText.replace(',', '.')
                .toDoubleOrNull()?.times(100)?.toLong()
        } else {
            null
        },
        createdAtMillis = initialEntry?.createdAtMillis ?: nowMillis,
        updatedAtMillis = initialEntry?.updatedAtMillis ?: nowMillis,
        deletedAtMillis = initialEntry?.deletedAtMillis,
        syncStatus = initialEntry?.syncStatus ?: LedgerSyncStatus.PENDING_PUSH,
        serverRevision = initialEntry?.serverRevision ?: 0L,
    )
    val previewBreakdown = if (effectiveMode != CostTrackingMode.NONE && previewAmountCents > 0L) {
        LedgerCalculator.calculateCostBreakdown(previewEntry, nowMillis)
    } else {
        null
    }

    fun saveWithRateFetch() {
        if (rateFetching) return
        rateFetching = true
        rateError = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    fxStore.rateForDate(selectedDate, fetchIfMissing = true)
                        ?: throw FxException(FxFailure.NETWORK)
                }
            }
            outcome.onSuccess { rates ->
                val rate = rates.cnyPerUnit(currency)
                if (rate != null && rate > 0) {
                    lockedRate = rate
                    lockedRateDate = rates.date
                    lockedRateSource = rates.source.id
                    onSave(
                        previewEntry.copy(
                            fxRateToCny = rate,
                            fxRateDate = rates.date,
                            fxRateSource = rates.source.id,
                        ),
                    )
                } else {
                    rateError = FxException(FxFailure.INVALID_RESPONSE)
                }
            }
            outcome.onFailure { error ->
                rateError = error as? FxException
                    ?: FxException(FxFailure.NETWORK, cause = error)
            }
            rateFetching = false
        }
    }

    val amountFocusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (initialEntry == null) {
                                    strings.addLedgerEntry
                                } else {
                                    strings.editLedgerEntry
                                },
                                maxLines = 1,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = strings.cancelAction,
                                )
                            }
                        },
                        actions = {
                            if (initialEntry != null) {
                                IconButton(onClick = { onDelete(initialEntry) }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = strings.deleteAction,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    attemptedSave = true
                                    rateError = null
                                    if (amountValid &&
                                        !decimalsForbidden &&
                                        !rateFetching
                                    ) {
                                        when {
                                            // Adjustments are denominated in the
                                            // account's currency; never fetch FX.
                                            isAdjustment -> onSave(
                                                previewEntry.copy(
                                                    fxRateToCny = 1.0,
                                                    fxRateDate = null,
                                                    fxRateSource = null,
                                                ),
                                            )
                                            isTransfer -> {
                                                if (toAccountUuid.isNotBlank() &&
                                                    toAccountUuid != accountUuid
                                                ) {
                                                    onSave(
                                                        previewEntry.copy(
                                                            fxRateToCny = 1.0,
                                                            fxRateDate = null,
                                                            fxRateSource = null,
                                                        ),
                                                    )
                                                }
                                            }
                                            currency == "CNY" -> onSave(
                                                previewEntry.copy(
                                                    fxRateToCny = 1.0,
                                                    fxRateDate = null,
                                                    fxRateSource = null,
                                                ),
                                            )
                                            useManualRate -> manualRate
                                                ?.takeIf { it > 0 }
                                                ?.let {
                                                    onSave(
                                                        previewEntry.copy(
                                                            fxRateToCny = it,
                                                            fxRateDate = null,
                                                            fxRateSource = FX_SOURCE_MANUAL,
                                                        ),
                                                    )
                                                }
                                            lockedRate != null -> onSave(
                                                previewEntry.copy(
                                                    fxRateToCny = lockedRate ?: 1.0,
                                                    fxRateDate = lockedRateDate,
                                                    fxRateSource = lockedRateSource,
                                                ),
                                            )
                                            else -> saveWithRateFetch()
                                        }
                                    }
                                },
                            ) {
                                if (rateFetching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = LocalContentColor.current,
                                    )
                                } else {
                                    Text(strings.saveAction)
                                }
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Expense / Income / Transfer segmented control.
                    // Adjustments keep their type: they only exist via reconcile.
                    if (isAdjustment) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = strings.adjustmentTypeLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = strings.adjustmentEditorHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf(
                                LedgerEntryType.EXPENSE to strings.expenseTypeLabel,
                                LedgerEntryType.INCOME to strings.incomeTypeLabel,
                                LedgerEntryType.TRANSFER to strings.transferTypeLabel,
                            ).forEachIndexed { index, (type, label) ->
                                SegmentedButton(
                                    selected = entryType == type,
                                    onClick = { entryType = type },
                                    enabled = !isLinked,
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = 3,
                                    ),
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }

                    if (isTransfer) {
                        Text(
                            text = strings.transferFromLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AccountChips(
                            accounts = liveAccounts,
                            selected = accountUuid,
                            onSelect = { accountUuid = it },
                        )
                        Text(
                            text = strings.transferToLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AccountChips(
                            accounts = liveAccounts.filter { it.uuid != accountUuid },
                            selected = toAccountUuid,
                            onSelect = { toAccountUuid = it },
                        )
                        if (toAccountUuid == accountUuid && toAccountUuid.isNotBlank()) {
                            Text(
                                text = strings.sameAccountError,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else if (liveAccounts.isNotEmpty()) {
                        Text(
                            text = strings.accountFieldLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AccountChips(
                            accounts = liveAccounts,
                            selected = accountUuid,
                            onSelect = { accountUuid = it },
                        )
                    }

                    if (!isTransfer && !isAdjustment) {
                        // Currency picker (rate to CNY locked at save)
                        LedgerCurrencyChips(
                            strings = strings,
                            selected = currency,
                            onSelect = { code ->
                                if (code != currency) {
                                    currency = code
                                    lockedRate = null
                                    lockedRateDate = null
                                    lockedRateSource = null
                                    rateError = null
                                }
                            },
                        )
                    }

                    // Amount (auto-focused for new entries)
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(strings.entryAmountLabel) },
                        prefix = { Text(LedgerCalculator.currencySymbol(currency)) },
                        textStyle = MaterialTheme.typography.headlineSmall,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = attemptedSave &&
                            (!amountValid || decimalsForbidden),
                        supportingText = if (
                            attemptedSave &&
                            (!amountValid || decimalsForbidden)
                        ) {
                            {
                                Text(
                                    if (decimalsForbidden && amountValid) {
                                        strings.decimalsNotAllowed(currency)
                                    } else {
                                        strings.amountInvalidError
                                    },
                                )
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(amountFocusRequester),
                    )

                    if (!isTransfer && !isAdjustment &&
                        pickedAccount != null &&
                        pickedAccount.currency != currency
                    ) {
                        OutlinedTextField(
                            value = accountAmountText,
                            onValueChange = {
                                accountAmountText = it
                                accountAmountEdited = true
                            },
                            label = {
                                Text(
                                    strings.accountAmountLabel(
                                        if (entryType == LedgerEntryType.INCOME) {
                                            strings.accountCreditWord
                                        } else {
                                            strings.accountDebitWord
                                        },
                                        pickedAccount.currency,
                                    ),
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (isTransfer &&
                        pickedToAccount != null &&
                        pickedToAccount.currency != effectiveCurrency
                    ) {
                        OutlinedTextField(
                            value = toAmountText,
                            onValueChange = { toAmountText = it },
                            label = {
                                Text(
                                    strings.accountAmountLabel(
                                        strings.accountCreditWord,
                                        pickedToAccount.currency,
                                    ),
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (!isTransfer && !isAdjustment && currency != "CNY") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = when {
                                    lockedRate != null -> {
                                        val base = lockedRateDate?.let {
                                            strings.rateLineLocked(
                                                currency,
                                                formatRate(lockedRate ?: 0.0),
                                                it,
                                            )
                                        } ?: strings.rateLineManual(
                                            currency,
                                            formatRate(lockedRate ?: 0.0),
                                        )
                                        listOf(
                                            base,
                                            strings.fxSourceName(lockedRateSource),
                                        ).filter { it.isNotBlank() }.joinToString(" · ")
                                    }
                                    useManualRate -> manualRate
                                        ?.takeIf { it > 0 }
                                        ?.let {
                                            strings.rateLineManual(
                                                currency,
                                                formatRate(it),
                                            )
                                        }
                                        ?: strings.rateFetchOnSave
                                    cachedRatePreview != null ->
                                        cachedRatePreview?.let { cached ->
                                            cached.cnyPerUnit(currency)?.let {
                                                strings.rateLineCached(
                                                    currency,
                                                    formatRate(it),
                                                    cached.date,
                                                ) + " · " +
                                                    strings.fxSourceName(cached.source.id)
                                            }
                                        }
                                            ?: strings.rateFetchOnSave
                                    else -> strings.rateFetchOnSave
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (previewAmountCents > 0L) {
                                Text(
                                    text = strings.approxCny(
                                        LedgerCalculator.formatCurrency(
                                            previewEntry.baseAmountCents / 100.0,
                                        ),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        rateError?.let { error ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme
                                        .errorContainer,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = strings.fxError(
                                            error.failure,
                                            error.httpCode,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme
                                            .onErrorContainer,
                                    )
                                    latestCachedRate?.let { cached ->
                                        TextButton(
                                            onClick = {
                                                val rate = cached.cnyPerUnit(currency)
                                                if (rate != null && rate > 0) {
                                                    lockedRate = rate
                                                    lockedRateDate = cached.date
                                                    lockedRateSource = cached.source.id
                                                    rateError = null
                                                }
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
                                    Text(
                                        text = strings.manualRateHint,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme
                                            .onErrorContainer,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = strings.manualRateSwitch,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(
                                checked = useManualRate,
                                onCheckedChange = {
                                    useManualRate = it
                                    rateError = null
                                },
                            )
                        }

                        if (useManualRate) {
                            OutlinedTextField(
                                value = manualRateText,
                                onValueChange = { manualRateText = it },
                                label = {
                                    Text(strings.manualRateFieldLabel(currency))
                                },
                                suffix = { Text("CNY") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                ),
                                singleLine = true,
                                isError = attemptedSave &&
                                    (manualRate == null || manualRate <= 0),
                                supportingText = if (
                                    attemptedSave &&
                                    (manualRate == null || manualRate <= 0)
                                ) {
                                    { Text(strings.manualRateInvalid) }
                                } else {
                                    null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Tag picker (multi-select) — hidden for transfers/adjustments
                    if (!isTransfer && !isAdjustment) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = strings.tagLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onManageTags) {
                                Text(strings.manageLabel)
                            }
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                        liveTags.forEach { tag ->
                            FilterChip(
                                selected = tag.uuid in selectedTagUuids,
                                onClick = {
                                    val next = selectedTagUuids.toMutableSet()
                                    if (!next.add(tag.uuid)) next.remove(tag.uuid)
                                    tagUuidsText = next.joinToString(",")
                                },
                                label = {
                                    Text(
                                        (if (tag.emoji.isNotBlank()) "${tag.emoji} " else "") +
                                            tag.name,
                                    )
                                },
                            )
                        }
                            FilterChip(
                                selected = false,
                                onClick = { showTagCreate = true },
                                label = { Text(strings.newTagChip) },
                            )
                        }
                    }

                    // Optional title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(strings.entryTitleLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Date picker row
                    Box {
                        OutlinedTextField(
                            value = "$selectedDate · ${strings.formatLedgerWeekday(selectedDate)}",
                            onValueChange = {},
                            label = { Text(strings.entryDateLabel) },
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
                                            selected = targetDaysText == daysVal,
                                            onClick = { targetDaysText = daysVal },
                                            label = { Text(label) },
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = targetDaysText,
                                    onValueChange = { targetDaysText = it },
                                    label = { Text(strings.targetDaysLabel) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
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
                            }

                            if (previewBreakdown != null) {
                                val daily = LedgerCalculator.formatCurrency(previewBreakdown.dailyCostYuan)
                                val monthly = LedgerCalculator.formatCurrency(previewBreakdown.monthlyCostYuan)
                                val yearly = LedgerCalculator.formatCurrency(previewBreakdown.yearlyCostYuan)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(12.dp),
                                ) {
                                    Text(
                                        text = strings.averageCostPreview(daily, monthly, yearly),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(strings.entryNoteLabel) },
                        minLines = 1,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        ToolboxDatePickerDialog(
            initial = selectedDate,
            minDate = null,
            maxDate = LocalDate.now(zone),
            strings = strings,
            onConfirm = { day ->
                if (day.toEpochDay() != selectedEpochDay) {
                    selectedEpochDay = day.toEpochDay()
                    lockedRate = null
                    lockedRateDate = null
                    lockedRateSource = null
                }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showTagCreate) {
        NewTagDialog(
            strings = strings,
            existing = liveTags,
            onDismiss = { showTagCreate = false },
            onCreate = { tag ->
                onCreateTag(tag)
                tagUuidsText = (selectedTagUuids + tag.uuid).joinToString(",")
                showTagCreate = false
            },
        )
    }

    LaunchedEffect(Unit) {
        if (initialEntry == null) {
            amountFocusRequester.requestFocus()
        }
    }
}

/** Chip row for picking a ledger account. */
@Composable
internal fun AccountChips(
    accounts: List<LedgerAccount>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        accounts.filter { !it.isArchived }.forEach { account ->
            FilterChip(
                selected = selected == account.uuid,
                onClick = { onSelect(account.uuid) },
                label = {
                    Text(
                        (if (account.emoji.isNotBlank()) "${account.emoji} " else "") +
                            account.name,
                    )
                },
            )
        }
    }
}

/** Shared 4-currency segmented selector. */
@Composable
internal fun LedgerCurrencyChips(
    strings: ToolboxStrings,
    selected: String,
    onSelect: (String) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        LEDGER_CURRENCIES.forEachIndexed { index, code ->
            SegmentedButton(
                selected = selected == code,
                onClick = { onSelect(code) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = LEDGER_CURRENCIES.size,
                ),
            ) {
                Text(code)
            }
        }
    }
}

@Composable
private fun LedgerCycleChips(
    strings: ToolboxStrings,
    billingCycle: BillingCycle,
    customCycleDaysText: String,
    customCycleUnit: String,
    onCycle: (BillingCycle) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            BillingCycle.MONTHLY,
            BillingCycle.QUARTERLY,
            BillingCycle.SEMI_ANNUAL,
            BillingCycle.YEARLY,
            BillingCycle.WEEKLY,
            BillingCycle.CUSTOM_DAYS,
        ).forEach { cycle ->
            FilterChip(
                selected = billingCycle == cycle,
                onClick = { onCycle(cycle) },
                label = {
                    Text(
                        if (cycle == BillingCycle.CUSTOM_DAYS) {
                            // Reads "自定义" up front — the configured
                            // interval shows below once selected.
                            buildString {
                                append(strings.cycleCustomChip)
                                if (billingCycle == BillingCycle.CUSTOM_DAYS) {
                                    append(" · ")
                                    append(
                                        strings.cycleName(
                                            cycle,
                                            customCycleDaysText.toIntOrNull() ?: 30,
                                            runCatching {
                                                CycleUnit.valueOf(customCycleUnit)
                                            }.getOrDefault(CycleUnit.DAYS),
                                        ),
                                    )
                                }
                            }
                        } else {
                            strings.cycleName(
                                cycle,
                                customCycleDaysText.toIntOrNull() ?: 30,
                                runCatching { CycleUnit.valueOf(customCycleUnit) }
                                    .getOrDefault(CycleUnit.DAYS),
                            )
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun CustomCycleInputs(
    strings: ToolboxStrings,
    countText: String,
    onCount: (String) -> Unit,
    unit: String,
    onUnit: (String) -> Unit,
) {
    OutlinedTextField(
        value = countText,
        onValueChange = onCount,
        label = { Text(strings.customDaysInputLabel) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        CycleUnit.entries.forEachIndexed { index, cycleUnit ->
            SegmentedButton(
                selected = unit == cycleUnit.name,
                onClick = { onUnit(cycleUnit.name) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = CycleUnit.entries.size,
                ),
            ) {
                Text(strings.cycleUnitName(cycleUnit))
            }
        }
    }
}
