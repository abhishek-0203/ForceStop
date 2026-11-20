package com.abhishek.forcestop.block

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/** One alarm, set for the next moment a block starts or ends. */
object BlockAlarms {

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(context: Context, at: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = intent(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(intent(context))
    }

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        BlockActionReceiver.REQUEST_ALARM,
        BlockActionReceiver.reconcileIntent(context),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
