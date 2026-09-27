package com.example.toolbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Shared full-width date picker. The default DatePickerDialog width clips the
 * month navigation arrows on ~375dp-wide phones, so this opts out of the
 * platform default width. Dates are normalized to UTC because the M3 picker
 * works in UTC millis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxDatePickerDialog(
    initial: LocalDate,
    minDate: LocalDate?,
    maxDate: LocalDate?,
    strings: ToolboxStrings,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneOffset.UTC
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(zone)
                    .toLocalDate()
                if (minDate != null && date < minDate) return false
                if (maxDate != null && date > maxDate) return false
                return true
            }

            override fun isSelectableYear(year: Int): Boolean {
                if (minDate != null && year < minDate.year) return false
                if (maxDate != null && year > maxDate.year) return false
                return true
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(
                            Instant.ofEpochMilli(millis)
                                .atZone(zone)
                                .toLocalDate(),
                        )
                    }
                },
            ) {
                Text(strings.confirmAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelAction)
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            DatePicker(state = state, showModeToggle = true)
        }
    }
}
