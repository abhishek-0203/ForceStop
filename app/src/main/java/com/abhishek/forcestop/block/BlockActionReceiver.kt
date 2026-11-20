package com.abhishek.forcestop.block

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abhishek.forcestop.log.EventLog
import com.abhishek.forcestop.target.TargetApp
import com.abhishek.forcestop.util.launchAsync

/** Handles the transition alarm plus notification and widget buttons. */
class BlockActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: "unknown"
        when (intent.action) {
            ACTION_RECONCILE -> launchAsync { BlockManager.reconcile(appContext, trigger = "timer") }
            ACTION_UNBLOCK -> launchAsync { BlockManager.unblock(appContext, packageName, reason = source) }
            ACTION_EXTEND -> launchAsync {
                BlockManager.extend(appContext, packageName, intent.getIntExtra(EXTRA_MINUTES, DEFAULT_EXTEND_MINUTES))
            }
            ACTION_BLOCK -> launchAsync {
                val target = TargetApp.fromPackage(packageName) ?: return@launchAsync
                val minutes = intent.getIntExtra(EXTRA_MINUTES, BlockRepository.defaultMinutes)
                BlockManager.block(appContext, target, System.currentTimeMillis() + minutes * 60_000L)
                    .onFailure { EventLog.log("Block from $source failed: ${it.message}") }
            }
        }
    }

    companion object {
        const val REQUEST_ALARM = 0
        const val REQUEST_UNBLOCK = 1
        const val REQUEST_EXTEND = 2
        const val REQUEST_WIDGET = 100
        const val DEFAULT_EXTEND_MINUTES = 15

        private const val ACTION_RECONCILE = "com.abhishek.forcestop.action.RECONCILE"
        private const val ACTION_UNBLOCK = "com.abhishek.forcestop.action.UNBLOCK"
        private const val ACTION_EXTEND = "com.abhishek.forcestop.action.EXTEND"
        private const val ACTION_BLOCK = "com.abhishek.forcestop.action.BLOCK"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_MINUTES = "minutes"
        private const val EXTRA_SOURCE = "source"

        fun reconcileIntent(context: Context): Intent =
            Intent(context, BlockActionReceiver::class.java).setAction(ACTION_RECONCILE)

        fun unblockIntent(context: Context, packageName: String?, source: String): Intent =
            Intent(context, BlockActionReceiver::class.java)
                .setAction(ACTION_UNBLOCK)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_SOURCE, source)

        fun extendIntent(context: Context, packageName: String?, minutes: Int): Intent =
            Intent(context, BlockActionReceiver::class.java)
                .setAction(ACTION_EXTEND)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_MINUTES, minutes)

        fun blockIntent(context: Context, packageName: String, minutes: Int, source: String): Intent =
            Intent(context, BlockActionReceiver::class.java)
                .setAction(ACTION_BLOCK)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_MINUTES, minutes)
                .putExtra(EXTRA_SOURCE, source)
    }
}
