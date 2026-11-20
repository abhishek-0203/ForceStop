package com.abhishek.forcestop.util

import android.content.BroadcastReceiver
import com.abhishek.forcestop.log.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Receivers get roughly 10 seconds after goAsync() before the system considers them hung. */
fun BroadcastReceiver.launchAsync(timeoutMs: Long = 9_000, block: suspend () -> Unit) {
    val pending = goAsync()
    AppScope.launch {
        try {
            withTimeout(timeoutMs) { block() }
        } catch (e: Exception) {
            EventLog.log("${javaClass.simpleName} failed: ${e.message}")
        } finally {
            pending.finish()
        }
    }
}
