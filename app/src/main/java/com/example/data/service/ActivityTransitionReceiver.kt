package com.example.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult

class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent) ?: return
            for (event in result.transitionEvents) {
                val isEntering = event.transitionType == 0 // ACTIVITY_TRANSITION_ENTER
                TrackingDiagnosticLogger.logInfo(
                    "ActivityReceiver",
                    "Received transition: activity=${event.activityType}, entering=$isEntering"
                )
                DriveTrackingService.onActivityTransitionEvent(
                    context = context,
                    activityType = event.activityType,
                    isEntering = isEntering
                )
            }
        }
    }
}
