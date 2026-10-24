package com.example.data.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

object ActivityTransitionManager {

    private const val REQUEST_CODE = 4001
    private var isRegistered = false

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private fun buildTransitionRequest(): ActivityTransitionRequest {
        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        )
        return ActivityTransitionRequest(transitions)
    }

    @SuppressLint("MissingPermission")
    fun registerTransitions(context: Context) {
        if (isRegistered) return
        try {
            val client = ActivityRecognition.getClient(context)
            val request = buildTransitionRequest()
            val pendingIntent = getPendingIntent(context)

            client.requestActivityTransitionUpdates(request, pendingIntent)
                .addOnSuccessListener {
                    isRegistered = true
                    TrackingDiagnosticLogger.logSuccess(
                        "ActivityRecognition",
                        "Registered for IN_VEHICLE and STILL activity transitions"
                    )
                }
                .addOnFailureListener { e ->
                    TrackingDiagnosticLogger.logWarn(
                        "ActivityRecognition",
                        "Failed to register transitions: ${e.message}"
                    )
                }
        } catch (e: Exception) {
            TrackingDiagnosticLogger.logWarn(
                "ActivityRecognition",
                "Exception registering activity transitions: ${e.message}"
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun unregisterTransitions(context: Context) {
        if (!isRegistered) return
        try {
            val client = ActivityRecognition.getClient(context)
            val pendingIntent = getPendingIntent(context)
            client.removeActivityTransitionUpdates(pendingIntent)
                .addOnSuccessListener {
                    isRegistered = false
                    TrackingDiagnosticLogger.logInfo(
                        "ActivityRecognition",
                        "Unregistered activity transitions"
                    )
                }
        } catch (e: Exception) {
            TrackingDiagnosticLogger.logWarn(
                "ActivityRecognition",
                "Exception unregistering transitions: ${e.message}"
            )
        }
    }
}
