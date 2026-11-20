package com.abhishek.forcestop.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.abhishek.forcestop.BuildConfig
import com.abhishek.forcestop.util.AppScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicInteger

data class ShellResult(val exitCode: Int, val output: String) {
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * Runs shell commands as the shell user through a Shizuku user service. The service is a separate
 * process, so it is torn down after [IDLE_UNBIND_MS] without commands instead of idling for a whole block.
 */
object ShizukuShell {

    private const val BIND_TIMEOUT_MS = 10_000L
    private const val IDLE_UNBIND_MS = 30_000L

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val mutex = Mutex()
    private val activeCalls = AtomicInteger(0)

    @Volatile
    private var service: IShellService? = null

    @Volatile
    private var pendingConnection: CompletableDeferred<IShellService>? = null

    @Volatile
    private var idleJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) return
            val connected = IShellService.Stub.asInterface(binder)
            service = connected
            pendingConnection?.complete(connected)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    suspend fun exec(command: String): ShellResult {
        activeCalls.incrementAndGet()
        idleJob?.cancel()
        try {
            val shell = connect()
            return withContext(Dispatchers.IO) {
                val bundle = shell.exec(command)
                ShellResult(
                    exitCode = bundle.getInt(ShellService.KEY_EXIT_CODE, -1),
                    output = bundle.getString(ShellService.KEY_OUTPUT).orEmpty(),
                )
            }
        } finally {
            if (activeCalls.decrementAndGet() == 0) scheduleIdleUnbind()
        }
    }

    private suspend fun connect(): IShellService = mutex.withLock {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        check(ShizukuStatus.current() == ShizukuStatus.READY) { "Shizuku is not running or not authorized" }

        val deferred = CompletableDeferred<IShellService>()
        pendingConnection = deferred
        try {
            withContext(Dispatchers.Main) { Shizuku.bindUserService(userServiceArgs, connection) }
            withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
        } finally {
            pendingConnection = null
        }
    }

    private fun scheduleIdleUnbind() {
        idleJob?.cancel()
        idleJob = AppScope.launch {
            delay(IDLE_UNBIND_MS)
            mutex.withLock {
                if (activeCalls.get() != 0 || service == null) return@withLock
                withContext(Dispatchers.Main) {
                    runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
                }
                service = null
            }
        }
    }
}
