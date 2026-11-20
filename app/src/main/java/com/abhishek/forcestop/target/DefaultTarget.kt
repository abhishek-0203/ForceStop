package com.abhishek.forcestop.target

import android.content.Context
import com.abhishek.forcestop.block.BlockRepository

/** The app the tile and widget act on: the one last picked in ForceStop, else the first installed. */
fun defaultTarget(context: Context): TargetApp? {
    val installed = TargetApp.installed(context)
    return TargetApp.fromPackage(BlockRepository.selectedTarget)?.takeIf { it in installed } ?: installed.firstOrNull()
}
