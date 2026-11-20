package com.abhishek.forcestop.ui.setup

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.abhishek.forcestop.shizuku.ShizukuStarter
import com.abhishek.forcestop.shizuku.ShizukuStatus
import com.abhishek.forcestop.ui.AppViewModel
import com.abhishek.forcestop.ui.Screen
import com.abhishek.forcestop.ui.UiState
import com.abhishek.forcestop.ui.openShizuku

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(state: UiState, snackbar: SnackbarHostState, viewModel: AppViewModel) {
    val setup = state.setup ?: return
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refresh()
        if (!granted) openNotificationSettings(context)
    }

    var showPairingHelp by remember { mutableStateOf(false) }
    if (showPairingHelp) PairingHelpDialog(onDismiss = { showPairingHelp = false })

    BackHandler(enabled = setup.requiredComplete) { viewModel.navigate(Screen.HOME) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup") },
                navigationIcon = {
                    if (setup.requiredComplete) {
                        IconButton(onClick = { viewModel.navigate(Screen.HOME) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
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
            Text(
                "ForceStop needs a few things before it can block apps.",
                style = MaterialTheme.typography.bodyLarge,
            )

            val version = state.shizukuVersion?.let { " v$it" }.orEmpty()
            when {
                !setup.shizukuInstalled -> SetupItem(
                    title = "Shizuku",
                    done = false,
                    required = true,
                    description = "Shizuku lets ForceStop force-stop apps without root. Install it, then start it " +
                        "with Wireless debugging.",
                    actionLabel = "Get Shizuku",
                    onAction = { openShizuku(context) },
                )
                setup.shizuku == ShizukuStatus.NOT_RUNNING -> SetupItem(
                    title = "Shizuku",
                    done = false,
                    required = true,
                    description = if (ShizukuStarter.canToggleDebugging(context)) {
                        "Shizuku isn't running. Tap Start Shizuku, then Start in Shizuku. ForceStop switches on " +
                            "Wireless debugging for you. Needed again after every reboot, on Wi-Fi."
                    } else {
                        "Shizuku isn't running. Turn on Wireless debugging in Developer options, open Shizuku " +
                            "and tap Start. After the first start, ForceStop switches Wireless debugging on for you."
                    },
                    actionLabel = "Start Shizuku",
                    onAction = { viewModel.startShizuku(context) },
                )
                setup.shizuku == ShizukuStatus.UNSUPPORTED -> SetupItem(
                    title = "Shizuku",
                    done = false,
                    required = true,
                    description = "Shizuku$version is too old. Update it to v11 or newer.",
                    actionLabel = "Get Shizuku",
                    onAction = { openShizuku(context) },
                )
                setup.shizuku == ShizukuStatus.PERMISSION_REQUIRED -> SetupItem(
                    title = "Shizuku",
                    done = false,
                    required = true,
                    description = "Shizuku$version is running. Allow ForceStop to use it.",
                    actionLabel = "Allow",
                    onAction = viewModel::requestShizukuPermission,
                )
                else -> SetupItem(
                    title = "Shizuku",
                    done = true,
                    required = true,
                    description = "Connected (Shizuku$version).",
                )
            }
            if (setup.shizukuInstalled && setup.shizuku == ShizukuStatus.NOT_RUNNING) {
                TextButton(onClick = { showPairingHelp = true }) { Text("Start fails in Shizuku? Pair it first") }
            }

            SetupItem(
                title = "Notifications",
                done = setup.notificationsAllowed,
                required = true,
                description = "Shows the countdown while an app is blocked, and the \"tap to resume\" reminder " +
                    "that brings it back when the block ends.",
                actionLabel = "Allow",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                },
            )

            SetupItem(
                title = "WhatsApp",
                done = setup.installedTargets.isNotEmpty(),
                required = true,
                description = if (setup.installedTargets.isEmpty()) {
                    "Install WhatsApp or WhatsApp Business first."
                } else {
                    "Found: ${setup.installedTargets.joinToString { it.label }}."
                },
            )

            SetupItem(
                title = "Exact alarms",
                done = setup.exactAlarmsAllowed,
                required = false,
                description = "Lets blocks end exactly on time instead of a few minutes late.",
                actionLabel = "Allow",
                onAction = { openExactAlarmSettings(context) },
            )

            SetupItem(
                title = "Unrestricted battery",
                done = setup.batteryUnrestricted,
                required = false,
                description = "Stops battery saving from delaying or dropping block timers. Matters most on " +
                    "Motorola, Samsung and Xiaomi phones.",
                actionLabel = "Allow",
                onAction = { requestBatteryExemption(context) },
            )

            Button(
                onClick = { viewModel.navigate(Screen.HOME) },
                enabled = setup.requiredComplete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun SetupItem(
    title: String,
    done: Boolean,
    required: Boolean,
    description: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val (icon: ImageVector, tint) = when {
        done -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        required -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
        else -> Icons.Filled.Info to MaterialTheme.colorScheme.tertiary
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = tint)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (required) "  Required" else "  Recommended",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(description, style = MaterialTheme.typography.bodyMedium)
                if (!done && actionLabel != null) {
                    FilledTonalButton(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
    }
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    )
}

@SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    )
}
