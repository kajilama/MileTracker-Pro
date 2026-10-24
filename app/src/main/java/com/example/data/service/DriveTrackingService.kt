package com.example.data.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.model.Trip
import com.example.data.model.TripPurpose
import com.example.data.model.TripStop
import com.example.data.repository.LocationService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class LiveDriveState(
    val isActive: Boolean = false,
    val isAutoDetected: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val distanceMiles: Double = 0.0,
    val currentSpeedMph: Int = 0,
    val currentAddress: String = "Monitoring Location",
    val startAddress: String = "",
    val startLat: Double? = null,
    val startLng: Double? = null,
    val activeTripTypeId: String? = "type_1",
    val activeTripTypeName: String = "Walmart Spark",
    val stops: List<TripStop> = emptyList()
)

class DriveTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var database: AppDatabase
    private lateinit var locationService: LocationService
    private lateinit var sharedPrefs: SharedPreferences

    private var locationCallback: LocationCallback? = null
    private var timerJob: Job? = null
    private var lastLocation: Location? = null
    private var lastSavedTripTimestamp: Long = 0L

    private val stateMachine = MotionDetectionStateMachine(
        onTripStartRequested = {
            onStateMachineTripStart()
        },
        onTripEndRequested = {
            onStateMachineTripEnd()
        },
        onStateChanged = { _, newState ->
            _motionState.value = newState
            updateNotification()
        }
    )

    companion object {
        const val CHANNEL_ID = "drive_tracking_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_TRACKING = "ACTION_START_TRACKING"
        const val ACTION_START_MANUAL = "ACTION_START_MANUAL"
        const val ACTION_END_TRIP = "ACTION_END_TRIP"
        const val ACTION_ADD_STOP = "ACTION_ADD_STOP"
        const val ACTION_SIMULATE_SPEED = "ACTION_SIMULATE_SPEED"
        const val ACTION_ACTIVITY_TRANSITION = "ACTION_ACTIVITY_TRANSITION"

        private val _driveState = MutableStateFlow(LiveDriveState())
        val driveState: StateFlow<LiveDriveState> = _driveState.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _motionState = MutableStateFlow(MotionState.IDLE)
        val motionState: StateFlow<MotionState> = _motionState.asStateFlow()

        fun startService(
            context: Context,
            manual: Boolean = false,
            tripTypeName: String = "Walmart Spark",
            tripTypeId: String? = null
        ) {
            val intent = Intent(context, DriveTrackingService::class.java).apply {
                action = if (manual) ACTION_START_MANUAL else ACTION_START_TRACKING
                putExtra("TRIP_TYPE_NAME", tripTypeName)
                if (tripTypeId != null) putExtra("TRIP_TYPE_ID", tripTypeId)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                TrackingDiagnosticLogger.logError("Service", "Failed to start service: ${e.message}")
            }
        }

        fun stopDrive(context: Context) {
            val intent = Intent(context, DriveTrackingService::class.java).apply {
                action = ACTION_END_TRIP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                TrackingDiagnosticLogger.logError("Service", "Failed to stop drive: ${e.message}")
            }
        }

        fun addDropoffStop(context: Context) {
            val intent = Intent(context, DriveTrackingService::class.java).apply {
                action = ACTION_ADD_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                TrackingDiagnosticLogger.logError("Service", "Failed to add stop: ${e.message}")
            }
        }

        fun simulateMotion(context: Context, speedMph: Int) {
            val intent = Intent(context, DriveTrackingService::class.java).apply {
                action = ACTION_SIMULATE_SPEED
                putExtra("SPEED", speedMph)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                TrackingDiagnosticLogger.logError("Service", "Failed to simulate motion: ${e.message}")
            }
        }

        fun onActivityTransitionEvent(context: Context, activityType: Int, isEntering: Boolean) {
            val intent = Intent(context, DriveTrackingService::class.java).apply {
                action = ACTION_ACTIVITY_TRANSITION
                putExtra("ACTIVITY_TYPE", activityType)
                putExtra("IS_ENTERING", isEntering)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                TrackingDiagnosticLogger.logError("Service", "Failed to dispatch transition: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        preferencesManager = PreferencesManager(this)
        database = AppDatabase.getDatabase(this)
        locationService = LocationService(this)
        sharedPrefs = getSharedPreferences("drive_tracking_active_state", Context.MODE_PRIVATE)

        createNotificationChannel()
        _isServiceRunning.value = true
        TrackingDiagnosticLogger.logInfo("Service", "DriveTrackingService created")

        ActivityTransitionManager.registerTransitions(this)
        syncStateMachineConfig()
    }

    private fun syncStateMachineConfig() {
        val profile = preferencesManager.userProfile.value
        stateMachine.config = MotionConfig(
            speedTriggerThresholdMph = profile.speedTriggerThresholdMph,
            inactivityTimeoutSeconds = profile.inactivityTimeoutMinutes * 60,
            stationarySpeedThresholdMph = 3,
            requiredConsecutiveFastFixes = 2,
            maxValidGpsAccuracyMeters = 60f
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_TRACKING
        syncStateMachineConfig()

        when (action) {
            ACTION_START_TRACKING -> {
                startMonitoring(manual = false)
            }
            ACTION_START_MANUAL -> {
                startMonitoring(manual = true)
                stateMachine.processEvent(MotionEvent.ManualStart)
            }
            ACTION_END_TRIP -> {
                stateMachine.processEvent(MotionEvent.ManualStop)
            }
            ACTION_ADD_STOP -> {
                recordCustomerDropoffStop()
            }
            ACTION_SIMULATE_SPEED -> {
                val speed = intent?.getIntExtra("SPEED", 25) ?: 25
                TrackingDiagnosticLogger.logInfo("Service", "Simulating GPS speed: $speed mph")
                stateMachine.processEvent(MotionEvent.GpsFix(speedMph = speed, accuracyMeters = 10f))
            }
            ACTION_ACTIVITY_TRANSITION -> {
                val type = intent?.getIntExtra("ACTIVITY_TYPE", 0) ?: 0
                val entering = intent?.getBooleanExtra("IS_ENTERING", true) ?: true
                stateMachine.processEvent(MotionEvent.ActivityTransition(type, entering))
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ongoing Drive Tracking & Telemetry",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time drive progress, speed, distance, and end trip action."
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startMonitoring(manual: Boolean) {
        val notification = buildOngoingNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            TrackingDiagnosticLogger.logError("Service", "startForeground error: ${e.message}")
        }

        setupLocationUpdates(isHighAccuracy = _driveState.value.isActive || manual)

        if (timerJob == null || timerJob?.isActive == false) {
            timerJob = serviceScope.launch {
                while (true) {
                    delay(1000)
                    stateMachine.processEvent(MotionEvent.TimerTick(1))
                    if (_driveState.value.isActive) {
                        _driveState.value = _driveState.value.copy(
                            elapsedSeconds = _driveState.value.elapsedSeconds + 1
                        )
                        if (_driveState.value.elapsedSeconds % 5 == 0L) {
                            updateNotification()
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationUpdates(isHighAccuracy: Boolean) {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }

        val interval = if (isHighAccuracy) 2500L else 8000L
        val minInterval = if (isHighAccuracy) 1500L else 4000L
        val minDistance = if (isHighAccuracy) 4f else 15f
        val priority = if (isHighAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val request = LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(minInterval)
            .setMinUpdateDistanceMeters(minDistance)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                onNewGpsLocation(loc)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
            TrackingDiagnosticLogger.logInfo(
                "Service",
                "Location updates requested (highAccuracy=$isHighAccuracy, interval=${interval}ms)"
            )
        } catch (e: Exception) {
            TrackingDiagnosticLogger.logError("Service", "Failed to request location updates: ${e.message}")
        }
    }

    private fun onNewGpsLocation(location: Location) {
        val speedMph = if (location.hasSpeed()) {
            (location.speed * 2.23694f).toInt()
        } else {
            if (lastLocation != null) {
                val deltaM = lastLocation!!.distanceTo(location)
                val deltaSec = ((location.time - lastLocation!!.time) / 1000f).coerceAtLeast(1f)
                ((deltaM / deltaSec) * 2.23694f).toInt()
            } else 0
        }

        TrackingDiagnosticLogger.logInfo(
            "GPS",
            "Fix: lat=%.4f, lng=%.4f, speed=%d mph, acc=%.1fm".format(
                Locale.US,
                location.latitude,
                location.longitude,
                speedMph,
                location.accuracy
            )
        )

        if (_driveState.value.isActive && lastLocation != null) {
            val deltaMeters = lastLocation!!.distanceTo(location)
            if (deltaMeters in 3f..2000f) {
                val deltaMiles = deltaMeters * 0.000621371
                _driveState.value = _driveState.value.copy(
                    distanceMiles = _driveState.value.distanceMiles + deltaMiles,
                    currentSpeedMph = speedMph
                )
            }
        }
        lastLocation = location

        stateMachine.processEvent(
            MotionEvent.GpsFix(
                speedMph = speedMph,
                accuracyMeters = location.accuracy,
                timestamp = location.time
            )
        )
    }

    private fun onStateMachineTripStart() {
        serviceScope.launch {
            val loc = locationService.getCurrentLocation()
            val addr = if (loc != null) locationService.reverseGeocode(loc.latitude, loc.longitude) else "Current Location"
            val (matchedTypeId, matchedTypeName) = evaluateAutoClassification(addr)

            lastLocation = loc
            _driveState.value = LiveDriveState(
                isActive = true,
                isAutoDetected = true,
                elapsedSeconds = 0L,
                distanceMiles = 0.0,
                currentSpeedMph = 25,
                currentAddress = addr,
                startAddress = addr,
                startLat = loc?.latitude,
                startLng = loc?.longitude,
                activeTripTypeId = matchedTypeId,
                activeTripTypeName = matchedTypeName,
                stops = emptyList()
            )

            setupLocationUpdates(isHighAccuracy = true)
            updateNotification()
            TrackingDiagnosticLogger.logSuccess(
                "Service",
                "Trip started automatically! Trip Type: $matchedTypeName, Origin: $addr"
            )
        }
    }

    private fun onStateMachineTripEnd() {
        concludeAndSaveDrive()
    }

    private fun recordCustomerDropoffStop() {
        serviceScope.launch {
            val loc = locationService.getCurrentLocation()
            val addr = if (loc != null) {
                locationService.reverseGeocode(loc.latitude, loc.longitude)
            } else {
                "Customer Dropoff #${_driveState.value.stops.size + 1}"
            }
            val configuredMins = preferencesManager.userProfile.value.minutesPerDropoffStop
            val stop = TripStop(
                id = UUID.randomUUID().toString(),
                address = addr,
                lat = loc?.latitude,
                lng = loc?.longitude,
                dropoffMinutes = configuredMins
            )
            _driveState.value = _driveState.value.copy(
                stops = _driveState.value.stops + stop
            )
            TrackingDiagnosticLogger.logSuccess("Service", "Recorded dropoff stop: $addr (+${configuredMins} min)")
            updateNotification()
        }
    }

    private fun concludeAndSaveDrive() {
        val state = _driveState.value
        if (!state.isActive) return

        val now = System.currentTimeMillis()
        if (now - lastSavedTripTimestamp < 15000L) {
            TrackingDiagnosticLogger.logWarn("Service", "Ignored duplicate save request within 15s")
            _driveState.value = LiveDriveState(isActive = false)
            return
        }

        serviceScope.launch {
            val endLoc = locationService.getCurrentLocation()
            val endAddress = if (endLoc != null) {
                locationService.reverseGeocode(endLoc.latitude, endLoc.longitude)
            } else {
                state.currentAddress
            }

            val rawMiles = state.distanceMiles
            val finalMiles = if (rawMiles >= 0.1) {
                String.format(Locale.US, "%.1f", rawMiles).toDoubleOrNull() ?: rawMiles
            } else 1.2

            val configuredMinsPerStop = preferencesManager.userProfile.value.minutesPerDropoffStop
            val dropoffMins = if (state.stops.isNotEmpty()) {
                state.stops.sumOf { it.dropoffMinutes }
            } else {
                state.stops.size * configuredMinsPerStop
            }
            val driveMins = ((finalMiles / 24.0) * 60).toInt().coerceAtLeast(2)
            val totalMins = driveMins + dropoffMins

            val allTripTypes = database.customTripTypeDao().getAllTripTypes().first()
            val matchedType = allTripTypes.find { it.id == state.activeTripTypeId }
                ?: allTripTypes.find { it.name.equals(state.activeTripTypeName, ignoreCase = true) }

            val resolvedTypeId = matchedType?.id
            val resolvedTypeName = matchedType?.name ?: "Unclassified"

            val purpose = when {
                matchedType == null -> TripPurpose.OTHER
                !matchedType.isTaxDeductible && matchedType.name.contains("Commute", ignoreCase = true) -> TripPurpose.COMMUTE
                !matchedType.isTaxDeductible -> TripPurpose.PERSONAL
                matchedType.name.contains("Medical", ignoreCase = true) -> TripPurpose.MEDICAL
                matchedType.name.contains("Charity", ignoreCase = true) -> TripPurpose.CHARITY
                else -> TripPurpose.GIG_DELIVERY
            }

            val tripYear = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
            val officialRates = IrsRateService.getOfficialRateForYear(tripYear)
            val rateToUse = when {
                matchedType != null && !matchedType.isTaxDeductible -> 0.0
                matchedType != null && matchedType.name.contains("Medical", ignoreCase = true) -> officialRates.medicalRate
                matchedType != null && matchedType.name.contains("Charity", ignoreCase = true) -> officialRates.charityRate
                matchedType != null && matchedType.isTaxDeductible -> officialRates.businessRate
                else -> 0.0
            }

            val vehicles = database.vehicleDao().getAllVehicles().first()
            val defaultVehicle = vehicles.find { it.isDefault } ?: vehicles.firstOrNull()

            val savedTrip = Trip(
                id = UUID.randomUUID().toString(),
                title = "$resolvedTypeName Trip (${state.stops.size} dropoffs)",
                purpose = purpose,
                startAddress = state.startAddress.ifBlank { "8400 Rosedale Hwy, Bakersfield, CA" },
                endAddress = endAddress,
                startLat = state.startLat,
                startLng = state.startLng,
                endLat = endLoc?.latitude,
                endLng = endLoc?.longitude,
                distanceMiles = finalMiles,
                deductionRate = rateToUse,
                totalDeduction = finalMiles * rateToUse,
                dateTimestamp = now,
                startTimeMillis = now - (totalMins * 60 * 1000L),
                endTimeMillis = now,
                isRoundTrip = state.stops.isNotEmpty(),
                stopsCount = state.stops.size,
                stopsSummary = state.stops.joinToString(" | ") { it.address },
                drivingMinutes = driveMins,
                dropoffMinutes = dropoffMins,
                totalDurationMinutes = totalMins,
                vehicleId = defaultVehicle?.id,
                vehicleName = defaultVehicle?.makeModel,
                notes = "Auto-tracked via GPS telemetry & Activity Recognition • Trip Type: $resolvedTypeName",
                tags = resolvedTypeName,
                tripTypeId = resolvedTypeId,
                isSyncedWithDrive = false
            )

            try {
                database.tripDao().insertTrip(savedTrip)
                lastSavedTripTimestamp = now
                TrackingDiagnosticLogger.logSuccess(
                    "Service",
                    "Trip saved to database: ${savedTrip.title}, %.1f mi, $%.2f tax deduction".format(
                        Locale.US,
                        savedTrip.distanceMiles,
                        savedTrip.totalDeduction
                    )
                )
            } catch (e: Exception) {
                TrackingDiagnosticLogger.logError("Service", "Failed to save trip to Room: ${e.message}")
            }

            _driveState.value = LiveDriveState(isActive = false)
            setupLocationUpdates(isHighAccuracy = false)
            updateNotification()
        }
    }

    private suspend fun evaluateAutoClassification(originAddress: String): Pair<String?, String> {
        try {
            val rules = database.autoClassificationRuleDao().getAllRules().first()
            val tripTypes = database.customTripTypeDao().getAllTripTypes().first()
            val lowerOrigin = originAddress.lowercase()

            for (rule in rules.filter { it.isEnabled }) {
                val origPatterns = rule.originPattern.split("|").map { it.trim().lowercase() }.filter { it.isNotBlank() }
                for (origPat in origPatterns) {
                    val matchesWalmart = origPat == "all walmart stores" && (lowerOrigin.contains("walmart") || lowerOrigin.contains("rosedale"))
                    if (matchesWalmart || lowerOrigin.contains(origPat)) {
                        val matchedType = tripTypes.find { it.id == rule.tripTypeId }
                            ?: tripTypes.find { it.name.equals(rule.tripTypeName, ignoreCase = true) }
                        if (matchedType != null) {
                            return matchedType.id to matchedType.name
                        }
                    }
                }
            }

            val defaultType = tripTypes.find { it.isDefault }
                ?: tripTypes.find { it.id == preferencesManager.userProfile.value.defaultTripTypeId }
                ?: tripTypes.find { it.name.equals(preferencesManager.userProfile.value.defaultTripTypeName, ignoreCase = true) }

            if (defaultType != null) {
                return defaultType.id to defaultType.name
            }
        } catch (e: Exception) {
            TrackingDiagnosticLogger.logWarn("Service", "Error evaluating auto-classification: ${e.message}")
        }
        return null to "Unclassified"
    }

    private fun buildOngoingNotification(): Notification {
        val state = _driveState.value
        val mins = state.elapsedSeconds / 60
        val secs = state.elapsedSeconds % 60
        val timeFormatted = "%02d:%02d".format(mins, secs)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endTripIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DriveTrackingService::class.java).apply { action = ACTION_END_TRIP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (state.isActive) {
            "Active Drive • Trip Type: ${state.activeTripTypeName}"
        } else {
            "Drive Detection Active (State: ${_motionState.value.name})"
        }

        val content = if (state.isActive) {
            "%.1f mi • %d mph • %s • %s".format(
                state.distanceMiles,
                state.currentSpeedMph,
                timeFormatted,
                state.startAddress.take(24)
            )
        } else {
            "Monitoring vehicular motion > ${preferencesManager.userProfile.value.speedTriggerThresholdMph} mph to auto-record trips."
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(state.isActive)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (state.isActive) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "■ End Trip",
                    endTripIntent
                ).build()
            )
        }

        return builder.build()
    }

    private fun updateNotification() {
        try {
            notificationManager.notify(NOTIFICATION_ID, buildOngoingNotification())
        } catch (_: Exception) {
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        ActivityTransitionManager.unregisterTransitions(this)
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        timerJob?.cancel()
        TrackingDiagnosticLogger.logInfo("Service", "DriveTrackingService destroyed")
    }
}
