package com.abhishek.forcestop.block

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.abhishek.forcestop.log.EventLog
import com.abhishek.forcestop.util.AppScope
import kotlinx.coroutines.launch

/**
 * Runs only while something is blocked. It listens for the target being re-enabled (from Settings or
 * the Play Store) so the block is restored within a second instead of the next time ForceStop opens.
 */
class BlockGuardService : Service() {

    private val packageChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            if (BlockManager.activeBlocks().none { it.target.packageName == packageName }) return
            AppScope.launch { BlockManager.reconcile(applicationContext, trigger = "$packageName changed") }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_PACKAGE_CHANGED).apply { addDataScheme("package") }
        ContextCompat.registerReceiver(this, packageChanged, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val blocks = BlockManager.activeBlocks()
        try {
            ServiceCompat.startForeground(
                this,
                Notifications.ID_ACTIVE,
                Notifications.buildActive(this, blocks),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            EventLog.log("Block guard couldn't start: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        if (blocks.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        unregisterReceiver(packageChanged)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        private var running = false

        /** Background restrictions may refuse a start, so only ask when the guard isn't already up. */
        fun start(context: Context) {
            if (running) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, BlockGuardService::class.java))
            } catch (e: Exception) {
                EventLog.log("Block guard not started: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BlockGuardService::class.java))
        }
    }
}
