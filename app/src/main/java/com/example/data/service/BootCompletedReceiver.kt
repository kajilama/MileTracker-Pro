package com.example.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.PreferencesManager

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferencesManager(context)
            val profile = prefs.userProfile.value
            TrackingDiagnosticLogger.logInfo(
                "BootReceiver",
                "Boot completed received. Auto-detection enabled=${profile.autoDriveDetectionEnabled}"
            )
            if (profile.autoDriveDetectionEnabled) {
                DriveTrackingService.startService(context, manual = false)
            }
        }
    }
}
