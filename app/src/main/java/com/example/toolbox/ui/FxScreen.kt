package com.example.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.toolbox.R
import com.example.toolbox.fx.CurrencySearch
import com.example.toolbox.fx.FxPinnedCurrencies
import com.example.toolbox.fx.LEDGER_CURRENCIES
import com.example.toolbox.fx.FxException
import com.example.toolbox.fx.FxFailure
import com.example.toolbox.fx.FxRateReader
import com.example.toolbox.fx.FxSource
import com.example.toolbox.fx.FxRateStore
import com.example.toolbox.fx.FxRates
import com.example.toolbox.ledger.LedgerCalculator
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Currency converter backed by the Frankfurter/ECB reference rates. The cached
 * `latest` snapshot is shown offline; the network is touched only when the user
 * taps refresh or the explicit "get rates" button.
 */
@Composable
internal fun FxScreen(
    strings: ToolboxStrings,
    onBack: () -> Unit,
    cachedRates: FxRates? = null,
    onRatesChange: (FxRates?) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnRatesChange by rememberUpdatedState(onRatesChange)
    val fxStore = remember { FxRateStore(context.applicationContext) }
    val reader = remember { FxRateReader() }
    val displayLocale = if (strings.language == AppLanguage.CHINESE) {
        Locale.SIMPLIFIED_CHINESE
    } else {
        Locale.US
    }

    var rates by remember { mutableStateOf(cachedRates) }
    var ratesLoaded by remember { mutableStateOf(cachedRates != null) }
    var fetching by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    var amountText by rememberSaveable { mutableStateOf("100") }
    var sourceCurrency by rememberSaveable { mutableStateOf("EUR") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var currencyQuery by rememberSaveable { mutableStateOf("") }
    var pickerQuery by rememberSaveable { mutableStateOf("") }
    val preferences = remember(context) { AppPreferences(context) }
    var pinnedCodes by remember { mutableStateOf(preferences.fxPinnedCurrencies()) }
    val featuredCodes = FxPinnedCurrencies.featured(pinnedCodes)

    fun togglePin(code: String) {
        pinnedCodes = FxPinnedCurrencies.toggle(pinnedCodes, code)
        preferences.saveFxPinnedCurrencies(pinnedCodes)
    }
    var lastAttempts by remember { mutableStateOf<List<Pair<FxSource, FxFailure>>>(emptyList()) }

    LaunchedEffect(Unit) {
        rates = withContext(Dispatchers.IO) { fxStore.latest() }
        ratesLoaded = true
        currentOnRatesChange(rates)
    }

    fun refresh() {
        if (fetching) return
        fetching = true
        errorText = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    reader.fetchLatest().also { fxStore.saveLatest(it) }
                }
            }
            outcome.onSuccess {
                rates = it
                currentOnRatesChange(it)
            }
            outcome.onFailure { error ->
                val fx = error as? FxException
                lastAttempts = fx?.attempts.orEmpty()
                errorText = fx?.let { strings.fxError(it.failure, it.httpCode) }
                    ?: strings.fxNetworkError
            }
            fetching = false
        }
    }

    Scaffold(
        topBar = {
            ToolboxTopBar(
                title = strings.moduleTitle(ToolboxModule.FX),
                onBack = onBack,
                backLabel = strings.back,
                actions = {
                    IconButton(onClick = ::refresh, enabled = !fetching) {
                        if (fetching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = strings.fxGetRates,
                            )
                        }
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FxStatusCard(
                    strings = strings,
                    rates = rates,
                    loaded = ratesLoaded,
                    errorText = errorText,
                    attemptLines = lastAttempts.map { (source, failure) ->
                        strings.fxSourceError(source.id, failure, null)
                    },
                    onGetRates = ::refresh,
                    fetching = fetching,
                )
            }

            item {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(strings.entryAmountLabel) },
                    prefix = { Text(LedgerCalculator.currencySymbol(sourceCurrency)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                    ),
                    singleLine = true,
                )
            }

            item {
                Text(
                    text = strings.fxFromLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(featuredCodes) { code ->
                        FilterChip(
                            selected = sourceCurrency == code,
                            onClick = { sourceCurrency = code },
                            label = { Text(code) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = sourceCurrency !in featuredCodes,
                            onClick = {
                                pickerQuery = ""
                                showCurrencyPicker = true
                            },
                            label = {
                                Text(
                                    if (sourceCurrency !in featuredCodes) {
                                        sourceCurrency
                                    } else {
                                        strings.fxMoreChip
                                    },
                                )
                            },
                        )
                    }
                }
            }

            val currentRates = rates
            if (currentRates != null) {
                item {
                    OutlinedTextField(
                        value = currencyQuery,
                        onValueChange = { currencyQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(strings.fxSearchHint) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (currencyQuery.isNotEmpty()) {
                                IconButton(onClick = { currencyQuery = "" }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_close),
                                        contentDescription = strings.cancelAction,
                                    )
                                }
                            }
                        },
                        singleLine = true,
                    )
                }
            }
            val amount = amountText.replace(',', '.').toDoubleOrNull()
            if (currentRates != null && amount != null) {
                item {
                    InfoCard(title = strings.moduleTitle(ToolboxModule.FX)) {
                        val currencyKeys = remember(currentRates) {
                            CurrencySearch.keys(currentRates.currencies)
                        }
                        val ledgerCodes = featuredCodes
                            .filter { it != sourceCurrency }
                            .filter { it in currentRates.eurRates }
                        val otherCodes = currentRates.currencies
                            .filter { it != sourceCurrency }
                            .filter { it !in featuredCodes }
                        val searching = currencyQuery.isNotBlank()
                        val shown = if (searching) {
                            CurrencySearch.filter(
                                ledgerCodes + otherCodes,
                                currencyQuery,
                                currencyKeys,
                            )
                        } else if (showAll) {
                            ledgerCodes + otherCodes
                        } else {
                            ledgerCodes
                        }
                        if (searching && shown.isEmpty()) {
                            Text(
                                text = strings.fxSearchEmpty,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        shown.forEachIndexed { index, code ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme
                                        .outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                            val converted = currentRates
                                .convert(amount, sourceCurrency, code)
                            InfoRow(
                                label = "$code · " + currencyDisplayName(
                                    code,
                                    displayLocale,
                                ),
                                value = converted?.let {
                                    formatFxAmount(it, code)
                                } ?: strings.unknown,
                                onCopy = {
                                    copyToClipboard(
                                        context = context,
                                        label = code,
                                        value = it,
                                    )
                                },
                            )
                        }
                        if (!searching && !showAll && otherCodes.isNotEmpty()) {
                            TextButton(
                                onClick = { showAll = true },
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Text(strings.fxShowAll(otherCodes.size))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCurrencyPicker && rates != null) {
        val pickerKeys = remember(rates) {
            CurrencySearch.keys(rates?.currencies.orEmpty())
        }
        val pickerOrder = remember(rates) {
            featuredCodes.filter { it in rates!!.eurRates } +
                rates!!.currencies.filter { it !in featuredCodes }
        }
        val pickerShown = CurrencySearch.filter(pickerOrder, pickerQuery, pickerKeys)
        AlertDialog(
            onDismissRequest = { showCurrencyPicker = false },
            title = { Text(strings.fxPickCurrency) },
            text = {
                Column {
                    OutlinedTextField(
                        value = pickerQuery,
                        onValueChange = { pickerQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        placeholder = { Text(strings.fxSearchHint) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = null,
                            )
                        },
                        singleLine = true,
                    )
                    Text(
                        text = strings.fxPinHint,
                        modifier = Modifier.padding(bottom = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn {
                        if (pickerShown.isEmpty()) {
                            item {
                                Text(
                                    text = strings.fxSearchEmpty,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                                )
                            }
                        }
                        items(pickerShown) { code ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        sourceCurrency = code
                                        showCurrencyPicker = false
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = "$code · " + currencyDisplayName(
                                            code,
                                            displayLocale,
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (code !in LEDGER_CURRENCIES) {
                                    val pinned = code in pinnedCodes
                                    IconButton(onClick = { togglePin(code) }) {
                                        Icon(
                                            painter = painterResource(
                                                if (pinned) {
                                                    R.drawable.ic_star
                                                } else {
                                                    R.drawable.ic_star_outline
                                                },
                                            ),
                                            contentDescription = if (pinned) {
                                                strings.fxUnpinAction(code)
                                            } else {
                                                strings.fxPinAction(code)
                                            },
                                            tint = if (pinned) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyPicker = false }) {
                    Text(strings.cancelAction)
                }
            },
        )
    }
}

@Composable
private fun FxStatusCard(
    strings: ToolboxStrings,
    rates: FxRates?,
    loaded: Boolean,
    errorText: String?,
    attemptLines: List<String> = emptyList(),
    onGetRates: () -> Unit,
    fetching: Boolean,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            if (rates != null) {
                InfoRow(
                    label = strings.fxRateDateLabel,
                    value = rates.date,
                    onCopy = {
                        copyToClipboard(
                            context = context,
                            label = strings.fxRateDateLabel,
                            value = it,
                        )
                    },
                )
                InfoRow(
                    label = strings.fxFetchedLabel,
                    value = strings.formatLedgerDateTime(rates.fetchedAtMillis),
                    onCopy = {
                        copyToClipboard(
                            context = context,
                            label = strings.fxFetchedLabel,
                            value = it,
                        )
                    },
                )
                InfoRow(
                    label = strings.fxSourceLabel,
                    value = strings.fxSourceName(rates.source.id),
                    onCopy = {},
                )
                if (rates.source != FxSource.FRANKFURTER) {
                    Text(
                        text = listOf(
                            strings.fxFallbackNote,
                            if (rates.source == FxSource.CURRENCY_API) {
                                strings.fxCommunityNote
                            } else {
                                ""
                            },
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (loaded) {
                Text(
                    text = strings.fxNoRates,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onGetRates, enabled = !fetching) {
                    if (fetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current,
                        )
                    } else {
                        Text(strings.fxGetRates)
                    }
                }
            }
            errorText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                attemptLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun currencyDisplayName(code: String, locale: Locale): String =
    runCatching {
        Currency.getInstance(code).getDisplayName(locale)
    }.getOrDefault(code)

private fun formatFxAmount(value: Double, code: String): String {
    val digits = runCatching {
        Currency.getInstance(code).defaultFractionDigits
    }.getOrDefault(2).coerceAtLeast(0)
    return LedgerCalculator.currencySymbol(code) +
        String.format(Locale.US, "%.${digits}f", value)
}
