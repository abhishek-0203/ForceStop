package com.abhishek.forcestop.block

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.abhishek.forcestop.R
import com.abhishek.forcestop.target.TargetApp
import com.abhishek.forcestop.ui.MainActivity
import com.abhishek.forcestop.util.formatTime

object Notifications {

    const val ID_ACTIVE = 1

    private const val CHANNEL_STATUS = "block_status"
    private const val CHANNEL_ENDED = "block_ended"
    private const val ID_RELEASE_PENDING = 3
    private const val ID_START_SHIZUKU = 4
    private const val REQUEST_START_SHIZUKU = 13
    private const val ID_ENDED_BASE = 10

    private const val REQUEST_RESUME_BASE = 20
    private const val REQUEST_OPEN_APP = 12

    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_STATUS,
                    context.getString(R.string.channel_status),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    CHANNEL_ENDED,
                    context.getString(R.string.channel_ended),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        )
    }

    fun buildActive(context: Context, blocks: List<ActiveBlock>): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_block)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
        if (blocks.isEmpty()) return builder.setContentTitle("ForceStop").build()

        val notEnforced = blocks.filter { it.target.isBlocked(context) == false }
        val endAt = blocks.minOf { it.endAt }
        val (title, text) = when {
            notEnforced.isNotEmpty() ->
                "${notEnforced.joinToString { it.target.label }} is running again" to
                    "Start Shizuku and ForceStop will block it again."
            blocks.size == 1 ->
                "${blocks.single().target.label} is blocked" to
                    "Calls and messages are held until ${formatTime(context, endAt)}"
            else ->
                "${blocks.size} apps are blocked" to blocks.joinToString { "${it.target.label} until ${formatTime(context, it.endAt)}" }
        }
        val unblock = broadcast(
            context,
            BlockActionReceiver.REQUEST_UNBLOCK,
            BlockActionReceiver.unblockIntent(context, packageName = null, source = "notification"),
        )
        val extend = broadcast(
            context,
            BlockActionReceiver.REQUEST_EXTEND,
            BlockActionReceiver.extendIntent(context, packageName = null, minutes = BlockActionReceiver.DEFAULT_EXTEND_MINUTES),
        )
        return builder
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(endAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .addAction(0, "Unblock now", unblock)
            .addAction(0, "+${BlockActionReceiver.DEFAULT_EXTEND_MINUTES} min", extend)
            .build()
    }

    fun showActive(context: Context, blocks: List<ActiveBlock>) {
        notify(context, ID_ACTIVE, buildActive(context, blocks))
    }

    fun cancelActive(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_ACTIVE)
    }

    fun showEnded(context: Context, target: TargetApp) {
        val index = TargetApp.all.indexOf(target).coerceAtLeast(0)
        val resume = target.launchIntent(context)?.let {
            PendingIntent.getActivity(
                context,
                REQUEST_RESUME_BASE + index,
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ENDED)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle("${target.label} block ended")
            .setContentText("Tap to resume ${target.label}. Messages and calls arrive once it's open.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(resume ?: openAppIntent(context))
            .build()
        notify(context, ID_ENDED_BASE + index, notification)
    }

    fun showReleasePending(context: Context, targets: List<TargetApp>) {
        val labels = targets.joinToString { it.label }
        val notification = NotificationCompat.Builder(context, CHANNEL_ENDED)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle("$labels is still disabled")
            .setContentText("Start Shizuku and ForceStop will re-enable it automatically.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "The block has ended, but $labels can only be re-enabled while Shizuku is running. " +
                        "Start Shizuku (Wireless debugging) and ForceStop will re-enable it automatically, " +
                        "or enable it yourself in Settings → Apps."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .build()
        notify(context, ID_RELEASE_PENDING, notification)
    }

    fun cancelReleasePending(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_RELEASE_PENDING)
    }

    fun showStartShizuku(context: Context) {
        val start = PendingIntent.getActivity(
            context,
            REQUEST_START_SHIZUKU,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_START_SHIZUKU),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ENDED)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle("Start Shizuku")
            .setContentText("Needed after a restart so ForceStop can block and unblock apps.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Tap here, then tap Start in Shizuku. ForceStop switches on Wireless debugging for you " +
                        "(connect to Wi-Fi first). Blocks already running stay in place until then."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(start)
            .addAction(0, "Start Shizuku", start)
            .build()
        notify(context, ID_START_SHIZUKU, notification)
    }

    fun cancelStartShizuku(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_START_SHIZUKU)
    }

    private fun broadcast(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notify(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
