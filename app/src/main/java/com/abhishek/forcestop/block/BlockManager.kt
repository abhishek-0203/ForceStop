package com.abhishek.forcestop.block

import android.content.Context
import com.abhishek.forcestop.engine.AppController
import com.abhishek.forcestop.engine.ShizukuAppController
import com.abhishek.forcestop.log.EventLog
import com.abhishek.forcestop.shizuku.ShizukuStatus
import com.abhishek.forcestop.target.TargetApp
import com.abhishek.forcestop.tile.BlockTileService
import com.abhishek.forcestop.util.formatTime
import com.abhishek.forcestop.widget.BlockWidgetProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * Owns blocking. Every entry point (UI, tile, widget, alarm, notification, boot, Shizuku connecting,
 * the target being re-enabled) ends in [sync], which compares what should be blocked right now with
 * what ForceStop has actually disabled and fixes the difference.
 */
object BlockManager {

    private const val SHIZUKU_WAIT_MS = 3_000L

    private val mutex = Mutex()
    private val controller: AppController = ShizukuAppController()

    fun activeBlocks(now: Long = System.currentTimeMillis()): List<ActiveBlock> =
        BlockPlanner.activeBlocks(BlockRepository.state.value, now, ZoneId.systemDefault())

    suspend fun block(context: Context, target: TargetApp, endAt: Long): Result<Unit> = mutex.withLock {
        if (endAt <= System.currentTimeMillis()) return Result.failure(IllegalArgumentException("End time is in the past"))
        val status = ShizukuStatus.awaitBinder(SHIZUKU_WAIT_MS)
        if (status != ShizukuStatus.READY) return Result.failure(IllegalStateException("Shizuku is $status"))

        BlockRepository.update { it.copy(restricted = it.restricted + target.packageName) }
        controller.restrict(target.packageName).onFailure { error ->
            controller.release(target.packageName)
                .onSuccess { BlockRepository.update { it.copy(restricted = it.restricted - target.packageName) } }
            return Result.failure(error)
        }
        BlockRepository.update { state ->
            state.copy(manual = state.manual + (target.packageName to maxOf(endAt, state.manual[target.packageName] ?: 0L)))
        }
        EventLog.log("Blocked ${target.label} until ${formatTime(context, endAt)}; ${describe(context, target)}")
        sync(context, trigger = "manual block")
        Result.success(Unit)
    }

    /** Extends the block on [packageName], or on every active block when null. */
    suspend fun extend(context: Context, packageName: String?, minutes: Int) = mutex.withLock {
        val now = System.currentTimeMillis()
        val blocks = activeBlocks(now).filter { packageName == null || it.target.packageName == packageName }
        if (blocks.isEmpty()) return
        BlockRepository.update { state ->
            state.copy(manual = state.manual + blocks.associate { it.target.packageName to maxOf(it.endAt, now) + minutes * 60_000L })
        }
        blocks.forEach { EventLog.log("Extended ${it.target.label} by $minutes min") }
        sync(context, trigger = "extend")
    }

    /** Ends the block on [packageName] (or all blocks), skipping the current window of any schedule. */
    suspend fun unblock(context: Context, packageName: String?, reason: String) = mutex.withLock {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val state = BlockRepository.state.value
        val packages = activeBlocks(now).map { it.target.packageName }.filter { packageName == null || it == packageName }
        if (packages.isEmpty()) return
        val skips = state.schedules
            .filter { it.packageName in packages }
            .mapNotNull { schedule -> schedule.activeWindow(now, zone)?.let { schedule.id to it.end } }
        BlockRepository.update { it.copy(manual = it.manual - packages.toSet(), skips = it.skips + skips) }
        EventLog.log("Unblocked ${packages.joinToString { TargetApp.fromPackage(it)?.label ?: it }} ($reason)")
        sync(context, trigger = reason)
    }

    suspend fun saveSchedule(context: Context, schedule: Schedule) = mutex.withLock {
        BlockRepository.update { state ->
            state.copy(schedules = state.schedules.filterNot { it.id == schedule.id } + schedule)
        }
        sync(context, trigger = "schedule saved")
    }

    suspend fun deleteSchedule(context: Context, id: String) = mutex.withLock {
        BlockRepository.update { state ->
            state.copy(schedules = state.schedules.filterNot { it.id == id }, skips = state.skips - id)
        }
        sync(context, trigger = "schedule deleted")
    }

    suspend fun reconcile(context: Context, trigger: String) = mutex.withLock { sync(context, trigger) }

    private suspend fun sync(context: Context, trigger: String) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        BlockRepository.update { BlockPlanner.prune(it, now) }

        val blocks = BlockPlanner.activeBlocks(BlockRepository.state.value, now, zone)
        val desired = blocks.map { it.target }
        val restricted = BlockRepository.state.value.restricted.mapNotNull { TargetApp.fromPackage(it) }
        val toRelease = restricted - desired.toSet()
        val toApply = desired.filter { it !in restricted || it.isDisabled(context) != true }

        if (toRelease.isNotEmpty() || toApply.isNotEmpty()) {
            val status = ShizukuStatus.awaitBinder(SHIZUKU_WAIT_MS)
            if (status == ShizukuStatus.READY) {
                toApply.forEach { apply(context, it, trigger, wasRestricted = it in restricted) }
                toRelease.forEach { release(context, it, trigger) }
            } else {
                if (toApply.isNotEmpty()) {
                    EventLog.log("Can't block ${toApply.joinToString { it.label }} ($trigger): Shizuku is $status")
                }
                if (toRelease.isNotEmpty()) {
                    EventLog.log("Can't re-enable ${toRelease.joinToString { it.label }} ($trigger): Shizuku is $status")
                }
            }
        }

        val stillRestricted = BlockRepository.state.value.restricted.mapNotNull { TargetApp.fromPackage(it) } - desired.toSet()
        if (stillRestricted.isEmpty()) {
            Notifications.cancelReleasePending(context)
        } else {
            Notifications.showReleasePending(context, stillRestricted)
        }

        BlockPlanner.nextTransition(BlockRepository.state.value, now, zone)
            ?.let { BlockAlarms.schedule(context, it) }
            ?: BlockAlarms.cancel(context)

        if (blocks.isEmpty()) {
            BlockGuardService.stop(context)
            Notifications.cancelActive(context)
        } else {
            Notifications.showActive(context, blocks)
            BlockGuardService.start(context)
        }
        BlockWidgetProvider.updateAll(context)
        BlockTileService.requestUpdate(context)
    }

    private suspend fun apply(context: Context, target: TargetApp, trigger: String, wasRestricted: Boolean) {
        BlockRepository.update { it.copy(restricted = it.restricted + target.packageName) }
        controller.restrict(target.packageName)
            .onSuccess {
                val what = if (wasRestricted) "Re-blocked ${target.label}, it had been re-enabled" else "Blocked ${target.label}"
                EventLog.log("$what ($trigger); ${describe(context, target)}")
            }
            .onFailure { EventLog.log("Blocking ${target.label} failed ($trigger): ${it.message}") }
    }

    private suspend fun release(context: Context, target: TargetApp, trigger: String) {
        controller.release(target.packageName)
            .onSuccess {
                BlockRepository.update { it.copy(restricted = it.restricted - target.packageName) }
                EventLog.log("Re-enabled ${target.label} ($trigger); ${describe(context, target)}")
                Notifications.showEnded(context, target)
            }
            .onFailure { EventLog.log("Re-enabling ${target.label} failed ($trigger): ${it.message}") }
    }

    private fun describe(context: Context, target: TargetApp): String =
        "stopped=${target.isStopped(context)} disabled=${target.isDisabled(context)}"
}
