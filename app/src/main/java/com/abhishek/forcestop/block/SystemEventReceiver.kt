package com.abhishek.forcestop.block

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abhishek.forcestop.log.EventLog
import com.abhishek.forcestop.shizuku.ShizukuStatus
import com.abhishek.forcestop.util.launchAsync

/** Alarms are dropped on reboot and app update, and schedules follow the local clock. */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "boot"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "app update"
            Intent.ACTION_TIME_CHANGED -> "clock change"
            Intent.ACTION_TIMEZONE_CHANGED -> "time zone change"
            else -> return
        }
        val appContext = context.applicationContext
        BlockManager.activeBlocks().forEach {
            EventLog.log("On $trigger: ${it.target.label} disabled=${it.target.isDisabled(appContext)}")
        }
        launchAsync {
            BlockManager.reconcile(appContext, trigger)
            if (trigger == "boot" && ShizukuStatus.awaitBinder(timeoutMs = 3_000) != ShizukuStatus.READY) {
                Notifications.showStartShizuku(appContext)
            }
        }
    }
}
