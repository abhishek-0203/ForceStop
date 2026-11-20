package com.abhishek.forcestop.ui.setup

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abhishek.forcestop.ui.openShizuku

private val PAIRING_STEPS = listOf(
    "Open Shizuku and, under \"Start via Wireless debugging\", tap Pairing.",
    "Tap Open Wireless debugging below, then \"Pair device with pairing code\". Leave the 6-digit code on screen.",
    "Pull down the notification shade. In Shizuku's notification, tap \"Enter pairing code\" and type the code.",
    "Go back to Shizuku and tap Start. From now on Start works after every restart, on Wi-Fi.",
)

/** Shizuku's Start only works after it has been paired with Wireless debugging once. */
@Composable
fun PairingHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair Shizuku (one time)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "If Start in Shizuku fails or Shizuku shows its pairing screen, it hasn't been paired with " +
                        "Wireless debugging yet:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PAIRING_STEPS.forEachIndexed { index, step ->
                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = { openShizuku(context) }) { Text("Open Shizuku") }
                OutlinedButton(onClick = { openWirelessDebugging(context) }) { Text("Open Wireless debugging") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** There is no public screen for Wireless debugging; Developer options with the item highlighted is closest. */
fun openWirelessDebugging(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
