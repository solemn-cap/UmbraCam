package com.umbracam.app.screen

import android.content.Context
import android.app.ActivityManager
import android.content.pm.PackageManager
import com.umbracam.core.recording.*

/** Reports device capabilities only; it never opens a camera or requests permission. */
class ReactionCapabilityDetector(private val context: Context) {
    fun read(): ReactionCapability {
        val front = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
        val glEsVersion = context.getSystemService(ActivityManager::class.java).deviceConfigurationInfo.reqGlEsVersion
        val layouts = if (front && glEsVersion >= 0x00020000) ReactionLayout.entries.toSet() else emptySet()
        return ReactionCapability(
        screen = true,
        frontCamera = front,
        microphone = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
        internalAudio = false,
        composedLayouts = layouts,
        microphoneCapture = false,
        )
    }
}
