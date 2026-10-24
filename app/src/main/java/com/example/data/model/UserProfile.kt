package com.example.data.model

import com.example.data.util.AppConstants

data class UserProfile(
    val id: String = AppConstants.SUPPORT_EMAIL,
    val displayName: String = "Kaji Lama",
    val email: String = AppConstants.SUPPORT_EMAIL,
    val phone: String = "+1 (555) 234-5678",
    val businessName: String = "Gig Logistics & Delivery Services",
    val homeBaseAddress: String = "742 Evergreen Terrace, Springfield, OR",
    val taxStatus: String = "1099 Independent Contractor",
    val taxIdEin: String = "XX-XXX4321",
    val annualMileageTarget: Double = 20000.0,
    val photoUrl: String? = null,
    val isSignedInWithGoogle: Boolean = true,
    val lastDriveSyncTimestamp: Long = 0L,
    val autoSyncToDrive: Boolean = true,
    val autoStartDriveGps: Boolean = true,
    val autoStopDriveGps: Boolean = true,
    val speedTriggerThresholdMph: Int = 10, // 5-45 mph, default 10
    val inactivityTimeoutMinutes: Int = 3, // 1-15 min, default 3
    val minutesPerDropoffStop: Int = AppConstants.DEFAULT_MINUTES_PER_STOP, // 0-15 min, default 4
    val autoDriveDetectionEnabled: Boolean = true,
    val autoClassifyWorkHours: Boolean = true,
    val autoClassifyWeekends: Boolean = true,
    val bluetoothAutoStartEnabled: Boolean = false,
    val bluetoothAutoStopEnabled: Boolean = false,
    val selectedBluetoothDeviceName: String = "Any Car Bluetooth",
    val defaultTripTypeId: String = "type_1",
    val defaultTripTypeName: String = "Walmart Spark",
    val irsBusinessRate: Double = 0.70, // 2025/2026 IRS standard rate
    val irsMedicalRate: Double = 0.22,
    val irsCharityRate: Double = 0.14,
    val distanceUnit: String = "Miles", // "Miles" or "Kilometers"
    val currencySymbol: String = "$",
    val fontPoints: Int = 14, // 10 to 24 pt
    val fontScale: Float = 1.0f,
    val screenZoomPercent: Int = 100, // 100% to 160%
    val themeMode: String = "SYSTEM", // "LIGHT", "DARK", "SYSTEM"
    val themePreset: String = "OCEAN" // 15 theme presets available
)
