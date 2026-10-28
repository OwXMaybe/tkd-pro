package com.tkdpro.core.designsystem.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tkdpro.R
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.ui.LocalFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun TkdTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.small,
    )
}

/** Integer input that keeps "empty" distinct from zero, so missing values are never stored as 0. */
@Composable
fun IntField(value: Int?, onValueChange: (Int?) -> Unit, label: String, modifier: Modifier = Modifier, range: IntRange = 0..100_000) {
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(7)
            text = digits
            onValueChange(digits.toIntOrNull()?.coerceIn(range))
        },
        label = { Text(label, maxLines = 1) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = MaterialTheme.shapes.small,
    )
}

/** Decimal input accepting both "." and "," as the decimal separator. */
@Composable
fun DecimalField(value: Double?, onValueChange: (Double?) -> Unit, label: String, modifier: Modifier = Modifier, suffix: String? = null) {
    var text by remember { mutableStateOf(value?.let { plainDecimal(it) } ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val cleaned = raw.filter { it.isDigit() || it == '.' || it == ',' }.take(9)
            text = cleaned
            onValueChange(cleaned.replace(',', '.').toDoubleOrNull())
        },
        label = { Text(label, maxLines = 1) },
        suffix = suffix?.let { { Text(it) } },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.small,
    )
}

private fun plainDecimal(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/** Selects one value out of a small range (e.g. RPE 1–10, ratings 1–5). Tapping the selected value clears it. */
@Composable
fun RatingSelector(label: String, value: Int?, range: IntRange, onChange: (Int?) -> Unit, modifier: Modifier = Modifier, hint: String? = null) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            range.forEach { v ->
                FilterChip(selected = v == value, onClick = { onChange(if (v == value) null else v) }, label = { Text(v.toString()) })
            }
        }
    }
}

@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, epochMillis: Long?, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val f = LocalFormatter.current
    OutlinedPickerButton(
        label = label,
        value = epochMillis?.let { f.date(it) } ?: stringResource(R.string.not_set),
        onClick = { open = true },
        icon = { Icon(Icons.Filled.CalendarMonth, null) },
        modifier = modifier,
    )
    if (open) {
        val zone = ZoneId.systemDefault()
        val initialLocal = epochMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (initialLocal?.toLocalDate() ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = initialLocal?.toLocalTime() ?: LocalTime.of(12, 0)
                        onChange(LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli())
                    }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, epochMillis: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val f = LocalFormatter.current
    val zone = ZoneId.systemDefault()
    val local = Instant.ofEpochMilli(epochMillis).atZone(zone)
    OutlinedPickerButton(label, f.time(epochMillis), { open = true }, { Icon(Icons.Filled.Schedule, null) }, modifier)
    if (open) {
        val state = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute, is24Hour = f.is24Hour)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(local.withHour(state.hour).withMinute(state.minute).toInstant().toEpochMilli())
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun OutlinedPickerButton(label: String, value: String, onClick: () -> Unit, icon: @Composable () -> Unit, modifier: Modifier) {
    androidx.compose.material3.OutlinedCard(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            icon()
            Column(Modifier.padding(start = Spacing.sm)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
