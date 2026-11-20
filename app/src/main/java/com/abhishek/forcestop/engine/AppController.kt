package com.abhishek.forcestop.engine

import com.abhishek.forcestop.log.EventLog
import com.abhishek.forcestop.shizuku.ShellResult
import com.abhishek.forcestop.shizuku.ShizukuShell

/** Privileged operations on other apps. Shizuku first; root can be added later. */
interface AppController {

    suspend fun forceStop(packageName: String): Result<Unit>

    /**
     * Force-stops and disables the app. Force stop and suspension are not enough: Meta's push relay
     * (com.facebook.services, preinstalled on many phones) wakes stopped WhatsApp and its calls ring
     * through Telecom even while suspended. Nothing can start a disabled package.
     */
    suspend fun restrict(packageName: String): Result<Unit>

    /** Undoes [restrict]. The app stays force-stopped until something launches it. */
    suspend fun release(packageName: String): Result<Unit>
}

class ShizukuAppController : AppController {

    override suspend fun forceStop(packageName: String): Result<Unit> = runCatching {
        exec("am force-stop $packageName")
    }

    override suspend fun restrict(packageName: String): Result<Unit> = runCatching {
        exec("am force-stop $packageName")
        val disable = ShizukuShell.exec("pm disable-user --user 0 $packageName")
        if (!disable.isSuccess) {
            EventLog.log("Disabling $packageName failed, falling back to suspend: ${disable.output.trim()}")
            exec("pm suspend $packageName")
        }
    }

    override suspend fun release(packageName: String): Result<Unit> = runCatching {
        exec("pm enable --user 0 $packageName")
        exec("pm unsuspend $packageName")
    }

    private suspend fun exec(command: String): ShellResult {
        val result = ShizukuShell.exec(command)
        check(result.isSuccess) { "`$command` exited with ${result.exitCode}: ${result.output.trim()}" }
        return result
    }
}
