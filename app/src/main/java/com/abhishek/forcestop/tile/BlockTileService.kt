package com.abhishek.forcestop.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.abhishek.forcestop.R
import com.abhishek.forcestop.block.BlockManager
import com.abhishek.forcestop.block.BlockRepository
import com.abhishek.forcestop.log.EventLog
import com.abhishek.forcestop.shizuku.ShizukuStatus
import com.abhishek.forcestop.target.defaultTarget
import com.abhishek.forcestop.util.AppScope
import com.abhishek.forcestop.util.formatDuration
import com.abhishek.forcestop.util.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Toggles a block on the default target for [BlockRepository.defaultMinutes]. */
class BlockTileService : TileService() {

    override fun onStartListening() {
        render()
    }

    override fun onClick() {
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        val target = defaultTarget(this) ?: return
        val blocked = BlockManager.activeBlocks().any { it.target == target }
        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE
            updateTile()
        }
        AppScope.launch {
            if (blocked) {
                BlockManager.unblock(applicationContext, target.packageName, reason = "quick settings tile")
            } else {
                val endAt = System.currentTimeMillis() + BlockRepository.defaultMinutes * 60_000L
                BlockManager.block(applicationContext, target, endAt)
                    .onFailure { EventLog.log("Block from tile failed: ${it.message}") }
            }
            withContext(Dispatchers.Main) { render() }
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val target = defaultTarget(this)
        val block = target?.let { t -> BlockManager.activeBlocks().firstOrNull { it.target == t } }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_block)
        tile.label = target?.label ?: getString(R.string.app_name)
        tile.state = when {
            target == null -> Tile.STATE_UNAVAILABLE
            block != null -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                target == null -> "No app to block"
                block != null -> "Until ${formatTime(this, block.endAt)}"
                ShizukuStatus.current() != ShizukuStatus.READY -> "Shizuku not running"
                else -> "Block ${formatDuration(BlockRepository.defaultMinutes)}"
            }
        }
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, BlockTileService::class.java))
            }
        }
    }
}
