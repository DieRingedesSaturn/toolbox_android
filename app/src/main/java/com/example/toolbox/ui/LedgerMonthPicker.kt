package com.example.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.toolbox.R
import java.time.YearMonth

/** Select a month directly without paging through every intervening month. */
@Composable
internal fun LedgerMonthPickerDialog(
    initial: YearMonth,
    latest: YearMonth,
    strings: ToolboxStrings,
    onConfirm: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    var yearText by rememberSaveable { mutableStateOf(initial.year.toString()) }
    var pickedMonth by rememberSaveable { mutableIntStateOf(initial.monthValue) }
    val year = yearText.toIntOrNull()?.takeIf { it in 1..latest.year }
    val picked = year?.let { YearMonth.of(it, pickedMonth) }
    val valid = picked != null && picked <= latest

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .acrylicSurface(
                shape = MaterialTheme.shapes.extraLarge,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        title = { Text(strings.selectLedgerMonth) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        enabled = year != null && year > 1,
                        onClick = { yearText = (requireNotNull(year) - 1).toString() },
                    ) {
                        Icon(painterResource(R.drawable.ic_chevron_left), strings.previousLedgerYear)
                    }
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { input ->
                            if (input.length <= 4 && input.all { it in '0'..'9' }) yearText = input
                        },
                        label = { Text(strings.ledgerYearLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = year == null,
                        supportingText = if (year == null) {
                            { Text(strings.ledgerYearRangeHint(latest.year)) }
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = year != null && year < latest.year,
                        onClick = { yearText = (requireNotNull(year) + 1).toString() },
                    ) {
                        Icon(painterResource(R.drawable.ic_chevron_right), strings.nextLedgerYear)
                    }
                }
                (1..12).chunked(3).forEach { months ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        months.forEach { month ->
                            ToolboxFilterChip(
                                selected = pickedMonth == month,
                                enabled = year != null && YearMonth.of(year, month) <= latest,
                                onClick = { pickedMonth = month },
                                label = { Text(strings.ledgerMonthLabel(month)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        yearText = latest.year.toString()
                        pickedMonth = latest.monthValue
                    },
                ) {
                    Text(strings.thisMonthLabel)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { picked?.takeIf { it <= latest }?.let(onConfirm) },
            ) {
                Text(strings.confirmAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
        },
    )
}
