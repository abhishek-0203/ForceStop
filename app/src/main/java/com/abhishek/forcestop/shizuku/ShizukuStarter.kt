package com.abhishek.forcestop.shizuku

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.Settings
import com.abhishek.forcestop.log.EventLog

/**
 * Shizuku's own "start" screen is not exported, so ForceStop can't start it directly. What it can do is
 * switch on Wireless debugging (with WRITE_SECURE_SETTINGS, granted once through Shizuku) and open
 * Shizuku, leaving the user a single "Start" tap.
 */
object ShizukuStarter {

    private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

    enum class Result { OPENED_SHIZUKU, NEEDS_WIFI, NOT_INSTALLED }

    fun canToggleDebugging(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun isWirelessDebuggingOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, ADB_WIFI_ENABLED, 0) == 1

    /** Call from a visible activity: opening another app from the background is blocked. */
    fun start(context: Context): Result {
        if (!ShizukuStatus.isInstalled(context)) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(ShizukuStatus.DOWNLOAD_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return Result.NOT_INSTALLED
        }
        if (!isOnWifi(context)) return Result.NEEDS_WIFI

        if (canToggleDebugging(context) && !isWirelessDebuggingOn(context)) {
            runCatching {
                val resolver = context.contentResolver
                Settings.Global.putInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
                Settings.Global.putInt(resolver, ADB_WIFI_ENABLED, 1)
            }.onSuccess { EventLog.log("Turned on Wireless debugging for Shizuku") }
                .onFailure { EventLog.log("Couldn't turn on Wireless debugging: ${it.message}") }
        }
        context.packageManager.getLaunchIntentForPackage(ShizukuStatus.SHIZUKU_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let(context::startActivity)
        return Result.OPENED_SHIZUKU
    }

    /** WRITE_SECURE_SETTINGS survives reboots, so this only has to succeed once while Shizuku is up. */
    suspend fun grantSettingsPermissionIfNeeded(context: Context) {
        if (canToggleDebugging(context) || ShizukuStatus.current() != ShizukuStatus.READY) return
        val result = runCatching {
            ShizukuShell.exec("pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}")
        }.getOrNull()
        EventLog.log(
            if (canToggleDebugging(context)) {
                "ForceStop can now switch on Wireless debugging by itself"
            } else {
                "Couldn't grant settings permission: ${result?.output?.trim()}"
            }
        )
    }

    private fun isOnWifi(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
