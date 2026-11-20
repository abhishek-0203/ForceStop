package com.abhishek.forcestop.block

import com.abhishek.forcestop.target.TargetApp
import java.time.ZoneId

data class StoredState(
    /** Manual blocks: package name to end time. */
    val manual: Map<String, Long> = emptyMap(),
    val schedules: List<Schedule> = emptyList(),
    /** Schedule windows the user ended early: schedule id to the end of the skipped window. */
    val skips: Map<String, Long> = emptyMap(),
    /** Packages ForceStop has disabled and not yet re-enabled. */
    val restricted: Set<String> = emptySet(),
)

data class ActiveBlock(
    val target: TargetApp,
    val endAt: Long,
    val manual: Boolean,
    val scheduleIds: List<String>,
)

/** Pure functions deciding what should be blocked; all side effects live in [BlockManager]. */
object BlockPlanner {

    fun activeBlocks(state: StoredState, now: Long, zone: ZoneId): List<ActiveBlock> {
        val manual = state.manual.filterValues { it > now }
        val scheduled = state.schedules
            .filter { it.enabled }
            .mapNotNull { schedule ->
                val window = schedule.activeWindow(now, zone) ?: return@mapNotNull null
                if ((state.skips[schedule.id] ?: 0L) >= window.end) return@mapNotNull null
                Triple(schedule.packageName, schedule.id, window.end)
            }

        return (manual.keys + scheduled.map { it.first }).distinct().mapNotNull { packageName ->
            val target = TargetApp.fromPackage(packageName) ?: return@mapNotNull null
            val windows = scheduled.filter { it.first == packageName }
            ActiveBlock(
                target = target,
                endAt = (windows.map { it.third } + listOfNotNull(manual[packageName])).max(),
                manual = packageName in manual,
                scheduleIds = windows.map { it.second },
            )
        }
    }

    /** The next moment the set of active blocks can change, used for the single reconcile alarm. */
    fun nextTransition(state: StoredState, now: Long, zone: ZoneId): Long? {
        val manualEnds = state.manual.values
        val scheduleTimes = state.schedules.filter { it.enabled }.flatMap { schedule ->
            listOfNotNull(schedule.activeWindow(now, zone)?.end, schedule.nextStart(now, zone))
        }
        return (manualEnds + scheduleTimes).filter { it > now }.minOrNull()
    }

    /** Drops expired manual blocks and skips so the stored state does not grow forever. */
    fun prune(state: StoredState, now: Long): StoredState = state.copy(
        manual = state.manual.filterValues { it > now },
        skips = state.skips.filterValues { it > now },
    )
}
