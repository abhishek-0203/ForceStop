package com.abhishek.forcestop

import android.app.Application
import com.abhishek.forcestop.block.BlockManager
import com.abhishek.forcestop.block.BlockRepository
import com.abhishek.forcestop.block.Notifications
import com.abhishek.forcestop.log.EventLog
import com.abhishek.forcestop.shizuku.ShizukuStarter
import com.abhishek.forcestop.util.AppScope
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class ForceStopApp : Application() {

    override fun onCreate() {
        super.onCreate()
        EventLog.init(this)
        BlockRepository.init(this)
        Notifications.createChannels(this)

        // Re-apply or finish blocks whenever Shizuku (re)connects, e.g. after it is started post-reboot.
        Shizuku.addBinderReceivedListenerSticky {
            Notifications.cancelStartShizuku(this)
            AppScope.launch {
                ShizukuStarter.grantSettingsPermissionIfNeeded(this@ForceStopApp)
                BlockManager.reconcile(this@ForceStopApp, trigger = "Shizuku connected")
            }
        }
    }
}
