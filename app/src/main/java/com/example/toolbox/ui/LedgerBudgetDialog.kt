package com.example.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.toolbox.ledger.LedgerBudgets
import com.example.toolbox.ledger.LedgerTag
import java.util.Locale
import kotlin.math.roundToLong

@Composable
internal fun LedgerBudgetDialog(
    strings: ToolboxStrings,
    budgets: LedgerBudgets,
    tags: List<LedgerTag>,
    onSave: (LedgerBudgets) -> Unit,
    onDismiss: () -> Unit,
) {
    var monthlyText by remember { mutableStateOf(budgetText(budgets.monthlyCents)) }
    val tagTexts = remember {
        mutableStateMapOf<String, String>().apply {
            tags.forEach { put(it.uuid, budgetText(budgets.tagCents[it.uuid])) }
        }
    }
    val allValid = isValidBudget(monthlyText) && tagTexts.values.all(::isValidBudget)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.budgetTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BudgetField(
                    strings = strings,
                    label = strings.budgetMonthlyLabel,
                    value = monthlyText,
                    onValueChange = { monthlyText = it },
                )
                if (tags.isNotEmpty()) {
                    Text(
                        text = strings.budgetByTagLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    tags.forEach { tag ->
                        BudgetField(
                            strings = strings,
                            label = "${tag.emoji} ${tag.name}".trim(),
                            value = tagTexts[tag.uuid].orEmpty(),
                            onValueChange = { tagTexts[tag.uuid] = it },
                        )
                    }
                }
                Text(
                    text = strings.budgetLocalOnlyNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = allValid,
                onClick = {
                    onSave(
                        LedgerBudgets(
                            monthlyCents = parseBudgetCents(monthlyText),
                            tagCents = tagTexts.mapNotNull { (uuid, text) ->
                                parseBudgetCents(text)?.let { uuid to it }
                            }.toMap(),
                        ),
                    )
                },
            ) {
                Text(strings.saveAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction)
            }
        },
    )
}

@Composable
private fun BudgetField(
    strings: ToolboxStrings,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val invalid = !isValidBudget(value)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(strings.budgetNonePlaceholder) },
        prefix = { Text("¥") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = invalid,
        supportingText = if (invalid) {
            { Text(strings.amountInvalidError) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun budgetText(cents: Long?): String =
    cents?.let { "%.2f".format(Locale.US, it / 100.0).removeSuffix(".00") }.orEmpty()

private fun parseBudgetCents(text: String): Long? = text.trim()
    .replace(',', '.')
    .takeIf { it.isNotEmpty() }
    ?.toDoubleOrNull()
    ?.takeIf { it.isFinite() && it > 0.0 }
    ?.let { (it * 100.0).roundToLong() }
    ?.takeIf { it > 0L }

private fun isValidBudget(text: String): Boolean {
    val trimmed = text.trim().replace(',', '.')
    if (trimmed.isEmpty()) return true
    val value = trimmed.toDoubleOrNull() ?: return false
    return value.isFinite() && value >= 0.0
}
