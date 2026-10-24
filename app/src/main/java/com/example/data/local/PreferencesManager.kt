package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.UserProfile
import com.example.data.util.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "miletracker_datastore")

class PreferencesManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("miletracker_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val KEY_MINUTES_PER_STOP = intPreferencesKey("minutes_per_dropoff_stop")
        val KEY_DEFAULT_TRIP_TYPE_ID = stringPreferencesKey("default_trip_type_id")
    }

    private val _userProfile = MutableStateFlow(loadProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    val minutesPerDropoffStopFlow: Flow<Int> = appContext.settingsDataStore.data.map { preferences ->
        preferences[KEY_MINUTES_PER_STOP]
            ?: prefs.getInt("minutes_per_dropoff_stop", AppConstants.DEFAULT_MINUTES_PER_STOP)
    }

    init {
        scope.launch {
            try {
                appContext.settingsDataStore.data.collect { dsPrefs ->
                    val dsMinutes = dsPrefs[KEY_MINUTES_PER_STOP]
                    val dsTypeId = dsPrefs[KEY_DEFAULT_TRIP_TYPE_ID]
                    var current = _userProfile.value
                    var changed = false
                    if (dsMinutes != null && dsMinutes != current.minutesPerDropoffStop) {
                        val safe = dsMinutes.coerceIn(0, 15)
                        prefs.edit().putInt("minutes_per_dropoff_stop", safe).apply()
                        current = current.copy(minutesPerDropoffStop = safe)
                        changed = true
                    } else if (dsMinutes == null) {
                        // Seed DataStore from current profile value
                        appContext.settingsDataStore.edit {
                            it[KEY_MINUTES_PER_STOP] = current.minutesPerDropoffStop
                        }
                    }
                    if (dsTypeId != null && dsTypeId != current.defaultTripTypeId) {
                        prefs.edit().putString("default_trip_type_id", dsTypeId).apply()
                        current = current.copy(defaultTripTypeId = dsTypeId)
                        changed = true
                    }
                    if (changed) {
                        _userProfile.value = current
                    }
                }
            } catch (_: Exception) {
                // Ignore DataStore initialization errors in unit test contexts
            }
        }
    }

    private fun loadProfile(): UserProfile {
        return UserProfile(
            id = prefs.getString("user_id", AppConstants.SUPPORT_EMAIL) ?: AppConstants.SUPPORT_EMAIL,
            displayName = prefs.getString("display_name", "Kaji Lama") ?: "Kaji Lama",
            email = prefs.getString("email", AppConstants.SUPPORT_EMAIL) ?: AppConstants.SUPPORT_EMAIL,
            phone = prefs.getString("phone", "+1 (555) 234-5678") ?: "+1 (555) 234-5678",
            businessName = prefs.getString("business_name", "Gig Logistics & Delivery Services") ?: "Gig Logistics & Delivery Services",
            homeBaseAddress = prefs.getString("home_base_address", "8400 Rosedale Hwy, Bakersfield, CA 93312") ?: "8400 Rosedale Hwy, Bakersfield, CA 93312",
            taxStatus = prefs.getString("tax_status", "1099 Independent Contractor") ?: "1099 Independent Contractor",
            taxIdEin = prefs.getString("tax_id_ein", "XX-XXX4321") ?: "XX-XXX4321",
            annualMileageTarget = prefs.getFloat("annual_mileage_target", 20000.0f).toDouble(),
            photoUrl = prefs.getString("photo_url", null),
            isSignedInWithGoogle = prefs.getBoolean("is_signed_in", false),
            lastDriveSyncTimestamp = prefs.getLong("last_drive_sync", System.currentTimeMillis() - 3600000L),
            autoSyncToDrive = prefs.getBoolean("auto_sync_drive", true),
            autoStartDriveGps = prefs.getBoolean("auto_start_drive_gps", true),
            autoStopDriveGps = prefs.getBoolean("auto_stop_drive_gps", true),
            speedTriggerThresholdMph = prefs.getInt("speed_trigger_threshold", 10),
            inactivityTimeoutMinutes = prefs.getInt("inactivity_timeout", 3),
            minutesPerDropoffStop = prefs.getInt("minutes_per_dropoff_stop", AppConstants.DEFAULT_MINUTES_PER_STOP).coerceIn(0, 15),
            autoDriveDetectionEnabled = prefs.getBoolean("auto_drive_detection", true),
            autoClassifyWorkHours = prefs.getBoolean("auto_classify_work_hours", true),
            autoClassifyWeekends = prefs.getBoolean("auto_classify_weekends", true),
            bluetoothAutoStartEnabled = prefs.getBoolean("bt_auto_start", false),
            bluetoothAutoStopEnabled = prefs.getBoolean("bt_auto_stop", false),
            selectedBluetoothDeviceName = prefs.getString("bt_device_name", "Any Car Bluetooth") ?: "Any Car Bluetooth",
            defaultTripTypeId = prefs.getString("default_trip_type_id", "type_1") ?: "type_1",
            defaultTripTypeName = prefs.getString("default_trip_type", "Walmart Spark") ?: "Walmart Spark",
            irsBusinessRate = prefs.getFloat("irs_business_rate", 0.70f).toDouble(),
            irsMedicalRate = prefs.getFloat("irs_medical_rate", 0.22f).toDouble(),
            irsCharityRate = prefs.getFloat("irs_charity_rate", 0.14f).toDouble(),
            distanceUnit = prefs.getString("distance_unit", "Miles") ?: "Miles",
            currencySymbol = prefs.getString("currency_symbol", "$") ?: "$",
            fontPoints = prefs.getInt("font_points", 14),
            fontScale = prefs.getFloat("font_scale", 1.0f),
            screenZoomPercent = prefs.getInt("screen_zoom_percent", 100),
            themeMode = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM",
            themePreset = prefs.getString("theme_preset", "OCEAN") ?: "OCEAN"
        )
    }

    fun saveProfile(profile: UserProfile) {
        val safeMinutes = profile.minutesPerDropoffStop.coerceIn(0, 15)
        val normalizedProfile = profile.copy(minutesPerDropoffStop = safeMinutes)
        prefs.edit().apply {
            putString("user_id", normalizedProfile.id)
            putString("display_name", normalizedProfile.displayName)
            putString("email", normalizedProfile.email)
            putString("phone", normalizedProfile.phone)
            putString("business_name", normalizedProfile.businessName)
            putString("home_base_address", normalizedProfile.homeBaseAddress)
            putString("tax_status", normalizedProfile.taxStatus)
            putString("tax_id_ein", normalizedProfile.taxIdEin)
            putFloat("annual_mileage_target", normalizedProfile.annualMileageTarget.toFloat())
            putString("photo_url", normalizedProfile.photoUrl)
            putBoolean("is_signed_in", normalizedProfile.isSignedInWithGoogle)
            putLong("last_drive_sync", normalizedProfile.lastDriveSyncTimestamp)
            putBoolean("auto_sync_drive", normalizedProfile.autoSyncToDrive)
            putBoolean("auto_start_drive_gps", normalizedProfile.autoStartDriveGps)
            putBoolean("auto_stop_drive_gps", normalizedProfile.autoStopDriveGps)
            putInt("speed_trigger_threshold", normalizedProfile.speedTriggerThresholdMph)
            putInt("inactivity_timeout", normalizedProfile.inactivityTimeoutMinutes)
            putInt("minutes_per_dropoff_stop", safeMinutes)
            putBoolean("auto_drive_detection", normalizedProfile.autoDriveDetectionEnabled)
            putBoolean("auto_classify_work_hours", normalizedProfile.autoClassifyWorkHours)
            putBoolean("auto_classify_weekends", normalizedProfile.autoClassifyWeekends)
            putBoolean("bt_auto_start", normalizedProfile.bluetoothAutoStartEnabled)
            putBoolean("bt_auto_stop", normalizedProfile.bluetoothAutoStopEnabled)
            putString("bt_device_name", normalizedProfile.selectedBluetoothDeviceName)
            putString("default_trip_type_id", normalizedProfile.defaultTripTypeId)
            putString("default_trip_type", normalizedProfile.defaultTripTypeName)
            putFloat("irs_business_rate", normalizedProfile.irsBusinessRate.toFloat())
            putFloat("irs_medical_rate", normalizedProfile.irsMedicalRate.toFloat())
            putFloat("irs_charity_rate", normalizedProfile.irsCharityRate.toFloat())
            putString("distance_unit", normalizedProfile.distanceUnit)
            putString("currency_symbol", normalizedProfile.currencySymbol)
            putInt("font_points", normalizedProfile.fontPoints)
            putFloat("font_scale", normalizedProfile.fontScale)
            putInt("screen_zoom_percent", normalizedProfile.screenZoomPercent)
            putString("theme_mode", normalizedProfile.themeMode)
            putString("theme_preset", normalizedProfile.themePreset)
            apply()
        }
        _userProfile.value = normalizedProfile
        scope.launch {
            try {
                appContext.settingsDataStore.edit { ds ->
                    ds[KEY_MINUTES_PER_STOP] = safeMinutes
                    ds[KEY_DEFAULT_TRIP_TYPE_ID] = normalizedProfile.defaultTripTypeId
                }
            } catch (_: Exception) {}
        }
    }

    fun setDefaultTripType(typeName: String, typeId: String? = null) {
        val updated = _userProfile.value.copy(
            defaultTripTypeName = typeName,
            defaultTripTypeId = typeId ?: _userProfile.value.defaultTripTypeId
        )
        saveProfile(updated)
    }

    fun setMinutesPerDropoffStop(minutes: Int) {
        val safeMinutes = minutes.coerceIn(0, 15)
        val updated = _userProfile.value.copy(minutesPerDropoffStop = safeMinutes)
        saveProfile(updated)
    }

    fun setSignInState(
        signedIn: Boolean,
        email: String = AppConstants.SUPPORT_EMAIL,
        displayName: String = "Kaji Lama",
        photoUrl: String? = null
    ) {
        val current = _userProfile.value
        val updated = current.copy(
            id = if (signedIn) email else "guest",
            email = email,
            displayName = displayName,
            photoUrl = photoUrl,
            isSignedInWithGoogle = signedIn
        )
        saveProfile(updated)
    }

    fun updateLastSyncTime(timestamp: Long = System.currentTimeMillis()) {
        val updated = _userProfile.value.copy(lastDriveSyncTimestamp = timestamp)
        saveProfile(updated)
    }

    fun updateIrsRates(business: Double, medical: Double, charity: Double) {
        val updated = _userProfile.value.copy(
            irsBusinessRate = business,
            irsMedicalRate = medical,
            irsCharityRate = charity
        )
        saveProfile(updated)
    }

    fun updateUnits(distanceUnit: String, currencySymbol: String) {
        val updated = _userProfile.value.copy(
            distanceUnit = distanceUnit,
            currencySymbol = currencySymbol
        )
        saveProfile(updated)
    }

    fun setAutoSync(enabled: Boolean) {
        val updated = _userProfile.value.copy(autoSyncToDrive = enabled)
        saveProfile(updated)
    }

    fun setAutoDriveDetection(enabled: Boolean) {
        val updated = _userProfile.value.copy(autoDriveDetectionEnabled = enabled)
        saveProfile(updated)
    }

    fun setTrackingParameters(
        autoStart: Boolean,
        autoStop: Boolean,
        speedThresholdMph: Int,
        timeoutMinutes: Int,
        minutesPerDropoffStop: Int = _userProfile.value.minutesPerDropoffStop
    ) {
        val updated = _userProfile.value.copy(
            autoStartDriveGps = autoStart,
            autoStopDriveGps = autoStop,
            speedTriggerThresholdMph = speedThresholdMph,
            inactivityTimeoutMinutes = timeoutMinutes,
            minutesPerDropoffStop = minutesPerDropoffStop.coerceIn(0, 15)
        )
        saveProfile(updated)
    }

    fun setBluetoothSettings(
        autoStart: Boolean,
        autoStop: Boolean,
        deviceName: String
    ) {
        val updated = _userProfile.value.copy(
            bluetoothAutoStartEnabled = autoStart,
            bluetoothAutoStopEnabled = autoStop,
            selectedBluetoothDeviceName = deviceName
        )
        saveProfile(updated)
    }

    fun setDisplayZoom(fontPoints: Int, zoomPercent: Int) {
        val rawScale = (fontPoints.toFloat() / 14f) * (zoomPercent.toFloat() / 100f)
        val computedScale = rawScale.coerceIn(0.85f, 1.55f)
        val updated = _userProfile.value.copy(
            fontPoints = fontPoints,
            screenZoomPercent = zoomPercent,
            fontScale = computedScale
        )
        saveProfile(updated)
    }

    fun setFontScale(scale: Float) {
        val safeScale = scale.coerceIn(0.85f, 1.55f)
        val computedPoints = (safeScale * 14f).toInt().coerceIn(10, 24)
        val updated = _userProfile.value.copy(
            fontScale = safeScale,
            fontPoints = computedPoints
        )
        saveProfile(updated)
    }

    fun setThemeMode(mode: String) {
        val updated = _userProfile.value.copy(themeMode = mode)
        saveProfile(updated)
    }

    fun setThemePreset(preset: String) {
        val updated = _userProfile.value.copy(themePreset = preset)
        saveProfile(updated)
    }

    fun signOut() {
        setSignInState(
            signedIn = false,
            email = "guest@local",
            displayName = "Guest Driver",
            photoUrl = null
        )
    }

    fun hasSeenWelcome(): Boolean = prefs.getBoolean("has_seen_welcome", false)

    fun markWelcomeSeen() {
        prefs.edit().putBoolean("has_seen_welcome", true).apply()
    }
}
