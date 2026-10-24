package com.example.data.service

enum class MotionState {
    IDLE,
    POSSIBLE_MOVEMENT,
    MOVING,
    TRIP_ACTIVE,
    POSSIBLE_STOP,
    STOPPED,
    TRACKING_PAUSED,
    ERROR
}

data class MotionConfig(
    val speedTriggerThresholdMph: Int = 10,
    val inactivityTimeoutSeconds: Int = 180, // Default 3 minutes
    val stationarySpeedThresholdMph: Int = 3,
    val requiredConsecutiveFastFixes: Int = 2,
    val maxValidGpsAccuracyMeters: Float = 60f
)

sealed class MotionEvent {
    data class GpsFix(
        val speedMph: Int,
        val accuracyMeters: Float,
        val timestamp: Long = System.currentTimeMillis()
    ) : MotionEvent()

    data class ActivityTransition(
        val activityType: Int, // e.g. DetectedActivity.IN_VEHICLE
        val isEntering: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    ) : MotionEvent()

    data class TimerTick(val deltaSeconds: Int = 1) : MotionEvent()
    object ManualStart : MotionEvent()
    object ManualStop : MotionEvent()
    object ForcePause : MotionEvent()
    object ForceResume : MotionEvent()
    data class ErrorOccurred(val reason: String) : MotionEvent()
}

class MotionDetectionStateMachine(
    var config: MotionConfig = MotionConfig(),
    private val onTripStartRequested: () -> Unit = {},
    private val onTripEndRequested: () -> Unit = {},
    private val onStateChanged: (oldState: MotionState, newState: MotionState) -> Unit = { _, _ -> }
) {
    var currentState: MotionState = MotionState.IDLE
        private set

    var consecutiveFastFixes: Int = 0
        private set

    var stationarySeconds: Int = 0
        private set

    var isVehicleActivityActive: Boolean = false
        private set

    fun processEvent(event: MotionEvent) {
        val oldState = currentState

        when (event) {
            is MotionEvent.ManualStart -> {
                stationarySeconds = 0
                consecutiveFastFixes = 0
                transitionTo(MotionState.TRIP_ACTIVE)
                onTripStartRequested()
            }

            is MotionEvent.ManualStop -> {
                stationarySeconds = 0
                consecutiveFastFixes = 0
                transitionTo(MotionState.STOPPED)
                onTripEndRequested()
                transitionTo(MotionState.IDLE)
            }

            is MotionEvent.ForcePause -> {
                if (currentState == MotionState.TRIP_ACTIVE || currentState == MotionState.POSSIBLE_STOP) {
                    transitionTo(MotionState.TRACKING_PAUSED)
                }
            }

            is MotionEvent.ForceResume -> {
                if (currentState == MotionState.TRACKING_PAUSED) {
                    transitionTo(MotionState.TRIP_ACTIVE)
                }
            }

            is MotionEvent.ErrorOccurred -> {
                TrackingDiagnosticLogger.logError("StateMachine", "Error event received: ${event.reason}")
                transitionTo(MotionState.ERROR)
            }

            is MotionEvent.ActivityTransition -> {
                handleActivityTransition(event)
            }

            is MotionEvent.GpsFix -> {
                handleGpsFix(event)
            }

            is MotionEvent.TimerTick -> {
                handleTimerTick(event.deltaSeconds)
            }
        }
    }

    private fun handleActivityTransition(event: MotionEvent.ActivityTransition) {
        // Activity type 0 corresponds to IN_VEHICLE in Google Play Services DetectedActivity
        val isVehicle = event.activityType == 0 || event.activityType == 9 // 0=IN_VEHICLE, 9=CAR
        if (isVehicle) {
            isVehicleActivityActive = event.isEntering
            TrackingDiagnosticLogger.logInfo(
                "StateMachine",
                "Activity Transition: ${if (event.isEntering) "ENTER_VEHICLE" else "EXIT_VEHICLE"}"
            )

            if (event.isEntering && currentState == MotionState.IDLE) {
                transitionTo(MotionState.POSSIBLE_MOVEMENT)
            } else if (!event.isEntering && currentState == MotionState.TRIP_ACTIVE) {
                // Potential stop detected by vehicle exit
                transitionTo(MotionState.POSSIBLE_STOP)
            }
        }
    }

    private fun handleGpsFix(fix: MotionEvent.GpsFix) {
        // Discard poor accuracy fixes to prevent GPS drift spikes
        if (fix.accuracyMeters > config.maxValidGpsAccuracyMeters) {
            TrackingDiagnosticLogger.logWarn(
                "StateMachine",
                "Discarded inaccurate GPS fix (acc=${fix.accuracyMeters}m > max ${config.maxValidGpsAccuracyMeters}m)"
            )
            return
        }

        when (currentState) {
            MotionState.IDLE -> {
                if (fix.speedMph >= config.speedTriggerThresholdMph || isVehicleActivityActive) {
                    consecutiveFastFixes = 1
                    transitionTo(MotionState.POSSIBLE_MOVEMENT)
                    TrackingDiagnosticLogger.logInfo(
                        "StateMachine",
                        "Movement candidate: speed=${fix.speedMph} mph >= threshold ${config.speedTriggerThresholdMph} mph"
                    )
                }
            }

            MotionState.POSSIBLE_MOVEMENT -> {
                if (fix.speedMph >= config.speedTriggerThresholdMph) {
                    consecutiveFastFixes++
                    if (consecutiveFastFixes >= config.requiredConsecutiveFastFixes || isVehicleActivityActive) {
                        transitionTo(MotionState.MOVING)
                        transitionTo(MotionState.TRIP_ACTIVE)
                        stationarySeconds = 0
                        onTripStartRequested()
                    }
                } else if (fix.speedMph < config.stationarySpeedThresholdMph && !isVehicleActivityActive) {
                    // False trigger (e.g. momentary GPS leap while stationary)
                    consecutiveFastFixes = 0
                    transitionTo(MotionState.IDLE)
                    TrackingDiagnosticLogger.logInfo("StateMachine", "False movement candidate discarded, returning to IDLE")
                }
            }

            MotionState.MOVING, MotionState.TRIP_ACTIVE -> {
                if (fix.speedMph < config.stationarySpeedThresholdMph) {
                    // Vehicle stopped (e.g. traffic light, intersection, delivery stop)
                    transitionTo(MotionState.POSSIBLE_STOP)
                    TrackingDiagnosticLogger.logInfo(
                        "StateMachine",
                        "Speed dropped to ${fix.speedMph} mph (< ${config.stationarySpeedThresholdMph} mph). Possible stop window started."
                    )
                } else {
                    // Still moving actively
                    stationarySeconds = 0
                }
            }

            MotionState.POSSIBLE_STOP -> {
                if (fix.speedMph >= config.stationarySpeedThresholdMph) {
                    // Resumed driving! Stop avoided (e.g. red light ended, intersection passed)
                    stationarySeconds = 0
                    transitionTo(MotionState.TRIP_ACTIVE)
                    TrackingDiagnosticLogger.logSuccess(
                        "StateMachine",
                        "Driving resumed (${fix.speedMph} mph). Stop window cancelled, trip continues seamlessly!"
                    )
                }
            }

            MotionState.STOPPED, MotionState.TRACKING_PAUSED, MotionState.ERROR -> {
                // No-op for GPS fixes while stopped or in error
            }
        }
    }

    private fun handleTimerTick(deltaSeconds: Int) {
        if (currentState == MotionState.POSSIBLE_STOP) {
            stationarySeconds += deltaSeconds
            if (stationarySeconds >= config.inactivityTimeoutSeconds) {
                TrackingDiagnosticLogger.logSuccess(
                    "StateMachine",
                    "Stationary timeout reached ($stationarySeconds s >= ${config.inactivityTimeoutSeconds} s). Concluding trip."
                )
                transitionTo(MotionState.STOPPED)
                onTripEndRequested()
                stationarySeconds = 0
                consecutiveFastFixes = 0
                transitionTo(MotionState.IDLE)
            }
        }
    }

    private fun transitionTo(newState: MotionState) {
        if (currentState != newState) {
            val oldState = currentState
            currentState = newState
            TrackingDiagnosticLogger.logInfo("StateMachine", "State transition: $oldState -> $newState")
            onStateChanged(oldState, newState)
        }
    }

    fun reset() {
        currentState = MotionState.IDLE
        consecutiveFastFixes = 0
        stationarySeconds = 0
        isVehicleActivityActive = false
    }
}
