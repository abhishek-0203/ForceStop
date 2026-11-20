package com.abhishek.forcestop.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.abhishek.forcestop.R
import com.abhishek.forcestop.block.BlockActionReceiver
import com.abhishek.forcestop.block.BlockManager
import com.abhishek.forcestop.block.BlockRepository
import com.abhishek.forcestop.block.Notifications
import com.abhishek.forcestop.target.defaultTarget
import com.abhishek.forcestop.util.formatDuration
import com.abhishek.forcestop.util.formatTime

/** Stands in for the target's hidden launcher icon: shows the block status with a one-tap action. */
class BlockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, BlockWidgetProvider::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val views = RemoteViews(context.packageName, R.layout.widget_block)
            val target = defaultTarget(context)
            val block = target?.let { t -> BlockManager.activeBlocks().firstOrNull { it.target == t } }

            views.setOnClickPendingIntent(R.id.widget_root, Notifications.openAppIntent(context))
            when {
                target == null -> {
                    views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                    views.setTextViewText(R.id.widget_status, "No app to block")
                    views.setTextViewText(R.id.widget_action, "Open")
                    views.setOnClickPendingIntent(R.id.widget_action, Notifications.openAppIntent(context))
                }
                block != null -> {
                    views.setTextViewText(R.id.widget_title, "${target.label} blocked")
                    views.setTextViewText(R.id.widget_status, "Until ${formatTime(context, block.endAt)}")
                    views.setTextViewText(R.id.widget_action, "Unblock now")
                    views.setOnClickPendingIntent(
                        R.id.widget_action,
                        broadcast(context, BlockActionReceiver.unblockIntent(context, target.packageName, source = "widget")),
                    )
                }
                else -> {
                    val minutes = BlockRepository.defaultMinutes
                    views.setTextViewText(R.id.widget_title, target.label)
                    views.setTextViewText(R.id.widget_status, "Not blocked")
                    views.setTextViewText(R.id.widget_action, "Block ${formatDuration(minutes)}")
                    views.setOnClickPendingIntent(
                        R.id.widget_action,
                        broadcast(context, BlockActionReceiver.blockIntent(context, target.packageName, minutes, source = "widget")),
                    )
                }
            }
            manager.updateAppWidget(ids, views)
        }

        private fun broadcast(context: Context, intent: android.content.Intent): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                BlockActionReceiver.REQUEST_WIDGET,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
