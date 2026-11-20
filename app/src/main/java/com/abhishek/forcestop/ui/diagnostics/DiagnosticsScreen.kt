package com.abhishek.forcestop.ui.diagnostics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abhishek.forcestop.ui.AppViewModel
import com.abhishek.forcestop.ui.Screen
import com.abhishek.forcestop.ui.SectionCard
import com.abhishek.forcestop.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(state: UiState, viewModel: AppViewModel) {
    val log by viewModel.log.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val target = state.target

    BackHandler { viewModel.navigate(Screen.HOME) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigate(Screen.HOME) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard("Status") {
                    val version = state.shizukuVersion?.let { " v$it" }.orEmpty()
                    Text("Shizuku: ${state.setup?.shizuku}$version")
                    Text("Target: ${target?.let { "${it.label} (${it.packageName})" } ?: "none"}")
                    Text(
                        "Stopped state: " + when (state.targetStopped) {
                            true -> "yes (Google pushes blocked)"
                            false -> "no (can receive pushes)"
                            null -> "unknown"
                        }
                    )
                    Text("Suspended: ${state.targetSuspended ?: "unknown"}")
                    Text("Disabled: ${state.targetDisabled ?: "unknown"}")
                    TextButton(onClick = viewModel::refresh) { Text("Refresh") }
                }
            }
            item {
                SectionCard("Manual actions") {
                    Button(onClick = viewModel::forceStopNow, enabled = state.shizukuReady && target != null && !state.busy) {
                        Text("Force stop ${target?.label.orEmpty()} now")
                    }
                    OutlinedButton(onClick = viewModel::releaseNow, enabled = state.shizukuReady && target != null && !state.busy) {
                        Text("Re-enable ${target?.label.orEmpty()}")
                    }
                    OutlinedButton(
                        onClick = { target?.launchIntent(context)?.let(context::startActivity) },
                        enabled = target != null,
                    ) {
                        Text("Open ${target?.label.orEmpty()}")
                    }
                }
            }
            item {
                var command by remember(target) {
                    mutableStateOf("dumpsys package ${target?.packageName.orEmpty()} | grep -m1 'User 0: ceDataInode'")
                }
                SectionCard("Shell (runs as the shell user via Shizuku)") {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    Button(onClick = { viewModel.runShell(command) }, enabled = state.shizukuReady && !state.busy) {
                        Text("Run")
                    }
                    state.shellOutput?.let {
                        SelectionContainer { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    }
                }
            }
            item {
                SectionCard("Event log") {
                    TextButton(onClick = viewModel::clearLog) { Text("Clear") }
                    if (log.isEmpty()) {
                        Text("No events yet.")
                    } else {
                        SelectionContainer {
                            Text(log.asReversed().joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
