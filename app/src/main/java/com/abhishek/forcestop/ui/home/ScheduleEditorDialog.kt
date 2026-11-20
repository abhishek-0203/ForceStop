package com.abhishek.forcestop.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abhishek.forcestop.block.Schedule
import com.abhishek.forcestop.target.TargetApp
import com.abhishek.forcestop.util.formatMinuteOfDay
import com.abhishek.forcestop.util.shortName
import java.time.DayOfWeek
import java.util.UUID

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

private enum class EditingTime { START, END }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditorDialog(
    initial: Schedule?,
    targets: List<TargetApp>,
    defaultTarget: TargetApp,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
    onDelete: (String) -> Unit,
) {
    val context = LocalContext.current
    var target by remember { mutableStateOf(initial?.let { TargetApp.fromPackage(it.packageName) } ?: defaultTarget) }
    var startMinute by remember { mutableIntStateOf(initial?.startMinute ?: (22 * 60)) }
    var endMinute by remember { mutableIntStateOf(initial?.endMinute ?: (7 * 60)) }
    var days by remember { mutableStateOf(initial?.days ?: WEEKDAYS) }
    var editing by remember { mutableStateOf<EditingTime?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New schedule" else "Edit schedule") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (targets.size > 1) {
                    Text("App", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        targets.forEach {
                            FilterChip(selected = it == target, onClick = { target = it }, label = { Text(it.label) })
                        }
                    }
                }

                Text("Time", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = EditingTime.START }) {
                        Text("From ${formatMinuteOfDay(context, startMinute)}")
                    }
                    OutlinedButton(onClick = { editing = EditingTime.END }) {
                        Text("To ${formatMinuteOfDay(context, endMinute)}")
                    }
                }
                if (endMinute <= startMinute) {
                    Text(
                        if (endMinute == startMinute) "Blocks for a full 24 hours." else "Ends the next morning.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text("Starts on", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(day.shortName()) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = days.isNotEmpty(),
                onClick = {
                    onSave(
                        Schedule(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            packageName = target.packageName,
                            days = days,
                            startMinute = startMinute,
                            endMinute = endMinute,
                            enabled = initial?.enabled ?: true,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial.id) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    editing?.let { which ->
        TimeDialog(
            title = if (which == EditingTime.START) "Block from" else "Block until",
            initialMinuteOfDay = if (which == EditingTime.START) startMinute else endMinute,
            onDismiss = { editing = null },
            onConfirm = {
                if (which == EditingTime.START) startMinute = it else endMinute = it
                editing = null
            },
        )
    }
}
