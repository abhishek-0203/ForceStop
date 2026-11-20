package com.abhishek.forcestop.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.abhishek.forcestop.BuildConfig
import com.abhishek.forcestop.block.ActiveBlock
import com.abhishek.forcestop.block.BlockActionReceiver
import com.abhishek.forcestop.block.Schedule
import com.abhishek.forcestop.shizuku.ShizukuStatus
import com.abhishek.forcestop.ui.setup.PairingHelpDialog
import com.abhishek.forcestop.target.TargetApp
import com.abhishek.forcestop.ui.AppViewModel
import com.abhishek.forcestop.ui.Screen
import com.abhishek.forcestop.ui.SectionCard
import com.abhishek.forcestop.ui.UiState
import com.abhishek.forcestop.util.describeSchedule
import com.abhishek.forcestop.util.formatDuration
import com.abhishek.forcestop.util.formatRemaining
import com.abhishek.forcestop.util.formatTime
import kotlinx.coroutines.delay
import java.time.LocalTime

private val PRESET_MINUTES = (if (BuildConfig.DEBUG) listOf(1) else emptyList()) + listOf(15, 30, 60, 120, 240, 480)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: UiState, snackbar: SnackbarHostState, viewModel: AppViewModel) {
    val targets = state.setup?.installedTargets.orEmpty()
    var editingSchedule by remember { mutableStateOf<Schedule?>(null) }
    var creatingSchedule by remember { mutableStateOf(false) }
    var showPairingHelp by remember { mutableStateOf(false) }
    if (showPairingHelp) PairingHelpDialog(onDismiss = { showPairingHelp = false })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ForceStop") },
                actions = {
                    IconButton(onClick = { viewModel.navigate(Screen.SETUP) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Setup")
                    }
                    IconButton(onClick = { viewModel.navigate(Screen.DIAGNOSTICS) }) {
                        Icon(Icons.Filled.Build, contentDescription = "Diagnostics")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.shizukuReady) {
                SectionCard(
                    title = "Shizuku isn't connected",
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text("Blocks can't start or end, and scheduled blocks won't apply, until Shizuku is running.")
                    if (state.setup?.shizuku == ShizukuStatus.NOT_RUNNING && state.setup.shizukuInstalled) {
                        val context = LocalContext.current
                        FilledTonalButton(onClick = { viewModel.startShizuku(context) }) { Text("Start Shizuku") }
                        TextButton(onClick = { showPairingHelp = true }) { Text("Start fails in Shizuku? Pair it first") }
                    } else {
                        FilledTonalButton(onClick = { viewModel.navigate(Screen.SETUP) }) { Text("Fix in setup") }
                    }
                }
            }

            state.blocks.forEach { block ->
                ActiveBlockCard(
                    block = block,
                    enforced = block.target.packageName in state.enforced,
                    canReapply = state.shizukuReady && !state.busy,
                    busy = state.busy,
                    onUnblock = { viewModel.unblock(block.target.packageName) },
                    onExtend = { viewModel.extend(block.target.packageName, BlockActionReceiver.DEFAULT_EXTEND_MINUTES) },
                    onReapply = viewModel::reapply,
                )
            }

            NewBlockCard(
                targets = targets,
                selected = state.target,
                alreadyBlocked = state.blocks.any { it.target == state.target },
                enabled = state.shizukuReady && !state.busy,
                onSelect = viewModel::selectTarget,
                onBlock = viewModel::startBlock,
            )

            SchedulesCard(
                schedules = state.schedules,
                activeScheduleIds = state.blocks.flatMap { it.scheduleIds }.toSet(),
                enabled = targets.isNotEmpty() && !state.busy,
                onAdd = { creatingSchedule = true },
                onEdit = { editingSchedule = it },
                onToggle = viewModel::setScheduleEnabled,
            )

            HowItWorksCard(state.target?.label ?: "WhatsApp")
        }
    }

    val defaultTarget = state.target ?: targets.firstOrNull()
    if ((creatingSchedule || editingSchedule != null) && defaultTarget != null) {
        ScheduleEditorDialog(
            initial = editingSchedule,
            targets = targets,
            defaultTarget = defaultTarget,
            onDismiss = {
                creatingSchedule = false
                editingSchedule = null
            },
            onSave = {
                viewModel.saveSchedule(it)
                creatingSchedule = false
                editingSchedule = null
            },
            onDelete = {
                viewModel.deleteSchedule(it)
                editingSchedule = null
            },
        )
    }
}

@Composable
private fun ActiveBlockCard(
    block: ActiveBlock,
    enforced: Boolean,
    canReapply: Boolean,
    busy: Boolean,
    onUnblock: () -> Unit,
    onExtend: () -> Unit,
    onReapply: () -> Unit,
) {
    val context = LocalContext.current
    val label = block.target.label
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(block.endAt, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }
    SectionCard(
        title = if (enforced) "$label is blocked" else "$label is running again",
        colors = CardDefaults.cardColors(
            containerColor = if (enforced) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ),
    ) {
        Text(
            "Until ${formatTime(context, block.endAt)} · ${formatRemaining(block.endAt - now)}",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            when {
                block.manual && block.scheduleIds.isNotEmpty() -> "Scheduled, extended manually"
                block.manual -> "Started manually"
                else -> "Started by a schedule"
            },
            style = MaterialTheme.typography.labelMedium,
        )
        if (enforced) {
            Text("Calls and messages are waiting on $label's servers. Nothing is lost.")
        } else {
            Text("$label was re-enabled while Shizuku wasn't running, so it can receive messages again.")
            Button(onClick = onReapply, enabled = canReapply) { Text("Block it again") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onUnblock, enabled = !busy) { Text("Unblock now") }
            OutlinedButton(onClick = onExtend, enabled = !busy) {
                Text("+${BlockActionReceiver.DEFAULT_EXTEND_MINUTES} min")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewBlockCard(
    targets: List<TargetApp>,
    selected: TargetApp?,
    alreadyBlocked: Boolean,
    enabled: Boolean,
    onSelect: (TargetApp) -> Unit,
    onBlock: (endAt: Long, minutes: Int?) -> Unit,
) {
    val context = LocalContext.current
    var length by remember { mutableStateOf<BlockLength>(BlockLength.For(60)) }
    var showTimePicker by remember { mutableStateOf(false) }

    SectionCard("Block now") {
        if (selected == null) {
            Text("No supported app is installed.")
            return@SectionCard
        }
        if (targets.size > 1) {
            Text("App", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.forEach { target ->
                    FilterChip(
                        selected = target == selected,
                        onClick = { onSelect(target) },
                        label = { Text(target.label) },
                    )
                }
            }
        }

        Text("How long", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESET_MINUTES.forEach { minutes ->
                FilterChip(
                    selected = length == BlockLength.For(minutes),
                    onClick = { length = BlockLength.For(minutes) },
                    label = { Text(formatDuration(minutes)) },
                )
            }
            val until = length as? BlockLength.Until
            FilterChip(
                selected = until != null,
                onClick = { showTimePicker = true },
                label = {
                    Text(until?.describe(context, System.currentTimeMillis())?.replaceFirstChar { it.uppercase() } ?: "Until…")
                },
            )
        }

        Button(
            onClick = { onBlock(length.endAt(System.currentTimeMillis()), (length as? BlockLength.For)?.minutes) },
            enabled = enabled && !alreadyBlocked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (alreadyBlocked) {
                    "${selected.label} is already blocked"
                } else {
                    "Block ${selected.label} ${length.describe(context, System.currentTimeMillis())}"
                }
            )
        }
    }

    if (showTimePicker) {
        val initial = length as? BlockLength.Until
        TimeDialog(
            title = "Block until",
            initialMinuteOfDay = initial?.let { it.hour * 60 + it.minute }
                ?: ((LocalTime.now().hour + 1) % 24 * 60),
            onDismiss = { showTimePicker = false },
            onConfirm = {
                length = BlockLength.Until(it / 60, it % 60)
                showTimePicker = false
            },
        )
    }
}

@Composable
private fun SchedulesCard(
    schedules: List<Schedule>,
    activeScheduleIds: Set<String>,
    enabled: Boolean,
    onAdd: () -> Unit,
    onEdit: (Schedule) -> Unit,
    onToggle: (Schedule, Boolean) -> Unit,
) {
    val context = LocalContext.current
    SectionCard("Schedules") {
        if (schedules.isEmpty()) {
            Text(
                "Block automatically at set times, for example weeknights 10 PM – 7 AM.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        schedules.forEachIndexed { index, schedule ->
            if (index > 0) HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onEdit(schedule) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val label = TargetApp.fromPackage(schedule.packageName)?.label ?: schedule.packageName
                    Text(
                        if (schedule.id in activeScheduleIds) "$label · active now" else label,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(describeSchedule(context, schedule), style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = { onToggle(schedule, it) },
                    enabled = enabled,
                )
            }
        }
        OutlinedButton(onClick = onAdd, enabled = enabled) { Text("Add schedule") }
    }
}

@Composable
private fun HowItWorksCard(label: String) {
    SectionCard("How blocking works") {
        Text(
            "While blocked, $label is force-stopped and disabled, so nothing can start it and its icon is " +
                "hidden. Calls don't ring and messages wait on $label's servers (senders see a single tick).",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "When the block ends, $label is enabled again. Tap the reminder notification or open $label and " +
                "everything arrives. Your chats are never touched. Ordinary SIM calls and SMS are not affected.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Add the ForceStop widget to your home screen, or the \"Block app\" tile to Quick Settings, " +
                "to block or unblock with one tap.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
