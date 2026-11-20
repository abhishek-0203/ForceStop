package com.abhishek.forcestop.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abhishek.forcestop.block.ActiveBlock
import com.abhishek.forcestop.block.BlockManager
import com.abhishek.forcestop.block.BlockRepository
import com.abhishek.forcestop.block.Schedule
import com.abhishek.forcestop.engine.ShizukuAppController
import com.abhishek.forcestop.log.EventLog
import com.abhishek.forcestop.setup.SetupState
import com.abhishek.forcestop.shizuku.ShizukuShell
import com.abhishek.forcestop.shizuku.ShizukuStarter
import com.abhishek.forcestop.shizuku.ShizukuStatus
import com.abhishek.forcestop.target.TargetApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

enum class Screen { LOADING, SETUP, HOME, DIAGNOSTICS }

data class UiState(
    val screen: Screen = Screen.LOADING,
    val setup: SetupState? = null,
    val shizukuVersion: Int? = null,
    val target: TargetApp? = null,
    val targetStopped: Boolean? = null,
    val targetSuspended: Boolean? = null,
    val targetDisabled: Boolean? = null,
    val blocks: List<ActiveBlock> = emptyList(),
    /** Packages of [blocks] that are actually stopped or disabled right now. */
    val enforced: Set<String> = emptySet(),
    val schedules: List<Schedule> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val shellOutput: String? = null,
) {
    val shizukuReady: Boolean get() = setup?.shizuku == ShizukuStatus.READY
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()
    private val diagnosticsController = ShizukuAppController()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    val log: StateFlow<List<String>> = EventLog.lines

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)

        viewModelScope.launch {
            ShizukuStatus.awaitBinder(timeoutMs = 1_500)
            refresh()
            _state.update { it.copy(screen = if (it.setup?.requiredComplete == true) Screen.HOME else Screen.SETUP) }
        }
        viewModelScope.launch { BlockRepository.state.collect { refresh() } }
    }

    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
    }

    fun onResume() {
        refresh()
        viewModelScope.launch {
            BlockManager.reconcile(app, trigger = "app open")
            refresh()
        }
    }

    fun refresh() {
        val setup = SetupState.read(app)
        val installed = setup.installedTargets
        val blocks = BlockManager.activeBlocks()
        _state.update { current ->
            val target = current.target?.takeIf { it in installed }
                ?: TargetApp.fromPackage(BlockRepository.selectedTarget)?.takeIf { it in installed }
                ?: installed.firstOrNull()
            current.copy(
                setup = setup,
                shizukuVersion = if (setup.shizuku == ShizukuStatus.NOT_RUNNING) {
                    null
                } else {
                    runCatching { Shizuku.getVersion() }.getOrNull()
                },
                target = target,
                targetStopped = target?.isStopped(app),
                targetSuspended = target?.isSuspended(app),
                targetDisabled = target?.isDisabled(app),
                blocks = blocks,
                enforced = blocks.filter { it.target.isBlocked(app) != false }.map { it.target.packageName }.toSet(),
                schedules = BlockRepository.state.value.schedules.sortedBy { it.startMinute },
            )
        }
    }

    fun navigate(screen: Screen) {
        _state.update { it.copy(screen = screen) }
        refresh()
    }

    fun selectTarget(target: TargetApp) {
        BlockRepository.selectedTarget = target.packageName
        _state.update { it.copy(target = target) }
        refresh()
    }

    /** [context] must be a visible activity so Shizuku can be brought to the front. */
    fun startShizuku(context: Context) {
        when (ShizukuStarter.start(context)) {
            ShizukuStarter.Result.NEEDS_WIFI -> showMessage("Connect to Wi-Fi first. Shizuku starts through Wireless debugging.")
            ShizukuStarter.Result.NOT_INSTALLED -> showMessage("Install Shizuku, then come back.")
            ShizukuStarter.Result.OPENED_SHIZUKU -> Unit
        }
    }

    fun requestShizukuPermission() {
        runCatching { Shizuku.requestPermission(ShizukuStatus.PERMISSION_REQUEST_CODE) }
            .onFailure { showMessage("Couldn't ask Shizuku for permission: ${it.message}") }
    }

    /** [minutes] is remembered as the tile and widget duration when the block was a fixed length. */
    fun startBlock(endAt: Long, minutes: Int?) = runBusy {
        val target = _state.value.target ?: return@runBusy
        minutes?.let { BlockRepository.defaultMinutes = it }
        BlockManager.block(app, target, endAt)
            .onFailure {
                EventLog.log("Block failed: ${it.message}")
                showMessage("Couldn't block ${target.label}: ${it.message}")
            }
    }

    fun extend(packageName: String, minutes: Int) = runBusy { BlockManager.extend(app, packageName, minutes) }

    fun unblock(packageName: String) = runBusy { BlockManager.unblock(app, packageName, reason = "app") }

    fun reapply() = runBusy {
        BlockManager.reconcile(app, trigger = "manual")
        val notEnforced = BlockManager.activeBlocks().filter { it.target.isBlocked(app) == false }
        if (notEnforced.isNotEmpty()) {
            showMessage("${notEnforced.joinToString { it.target.label }} is still running. Check that Shizuku is connected.")
        }
    }

    fun saveSchedule(schedule: Schedule) = runBusy { BlockManager.saveSchedule(app, schedule) }

    fun deleteSchedule(id: String) = runBusy { BlockManager.deleteSchedule(app, id) }

    fun setScheduleEnabled(schedule: Schedule, enabled: Boolean) =
        runBusy { BlockManager.saveSchedule(app, schedule.copy(enabled = enabled)) }

    fun forceStopNow() = runBusy {
        val target = _state.value.target ?: return@runBusy
        diagnosticsController.forceStop(target.packageName)
            .onSuccess { EventLog.log("Force-stopped ${target.label}; stopped=${target.isStopped(app)}") }
            .onFailure { EventLog.log("Force stop failed: ${it.message}") }
    }

    /** Escape hatch: re-enable the target regardless of block state. A running block re-applies on next sync. */
    fun releaseNow() = runBusy {
        val target = _state.value.target ?: return@runBusy
        diagnosticsController.release(target.packageName)
            .onSuccess {
                BlockRepository.update { it.copy(restricted = it.restricted - target.packageName) }
                EventLog.log("Manually re-enabled ${target.label}; disabled=${target.isDisabled(app)}")
            }
            .onFailure { EventLog.log("Manual re-enable failed: ${it.message}") }
    }

    fun runShell(command: String) = runBusy {
        val output = runCatching { ShizukuShell.exec(command) }.fold(
            onSuccess = { "exit ${it.exitCode}\n${it.output}" },
            onFailure = { "error: ${it.message}" },
        )
        _state.update { it.copy(shellOutput = output) }
    }

    fun clearLog() = EventLog.clear()

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun showMessage(message: String) = _state.update { it.copy(message = message) }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
                refresh()
            }
        }
    }
}
