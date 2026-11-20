package com.abhishek.forcestop.setup

import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.abhishek.forcestop.block.BlockAlarms
import com.abhishek.forcestop.shizuku.ShizukuStatus
import com.abhishek.forcestop.target.TargetApp

data class SetupState(
    val shizuku: ShizukuStatus,
    val shizukuInstalled: Boolean,
    val notificationsAllowed: Boolean,
    val exactAlarmsAllowed: Boolean,
    val batteryUnrestricted: Boolean,
    val installedTargets: List<TargetApp>,
) {
    /** Notifications are required because the "tap to resume" reminder is how a blocked app comes back. */
    val requiredComplete: Boolean
        get() = shizuku == ShizukuStatus.READY && notificationsAllowed && installedTargets.isNotEmpty()

    companion object {
        fun read(context: Context): SetupState = SetupState(
            shizuku = ShizukuStatus.current(),
            shizukuInstalled = ShizukuStatus.isInstalled(context),
            notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            exactAlarmsAllowed = BlockAlarms.canScheduleExact(context),
            batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
            installedTargets = TargetApp.installed(context),
        )
    }
}
