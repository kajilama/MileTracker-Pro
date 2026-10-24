package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.util.AppConstants
import java.util.UUID

enum class TripPurpose(val displayName: String, val defaultRate: Double) {
    BUSINESS("Business", 0.67),
    GIG_DELIVERY("Gig Delivery / Customer Dropoff", 0.67),
    PERSONAL("Personal", 0.00),
    MEDICAL("Medical", 0.21),
    CHARITY("Charity", 0.14),
    COMMUTE("Commute (Non-deductible)", 0.00),
    OTHER("Other", 0.00)
}

data class TripStop(
    val id: String = UUID.randomUUID().toString(),
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val dropoffMinutes: Int = AppConstants.DEFAULT_MINUTES_PER_STOP
)

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val purpose: TripPurpose = TripPurpose.BUSINESS,
    val startAddress: String,
    val endAddress: String,
    val startLat: Double? = null,
    val startLng: Double? = null,
    val endLat: Double? = null,
    val endLng: Double? = null,
    val distanceMiles: Double,
    val deductionRate: Double = 0.67, // IRS standard business rate ($0.67/mi)
    val totalDeduction: Double = distanceMiles * deductionRate,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val startTimeMillis: Long = System.currentTimeMillis() - (1800000L), // 30 min default
    val endTimeMillis: Long = System.currentTimeMillis(),
    val isRoundTrip: Boolean = false,
    val stopsCount: Int = 0,
    val stopsSummary: String = "", // comma-separated or json summary
    val drivingMinutes: Int = 0,
    val dropoffMinutes: Int = 0,
    val totalDurationMinutes: Int = 0,
    val odometerStart: Double? = null,
    val odometerEnd: Double? = null,
    val vehicleId: String? = null,
    val vehicleName: String? = null,
    val notes: String = "",
    val tags: String = "", // comma-separated
    val routePointsJson: String = "", // serialized "lat,lng;lat,lng" for actual navigation road points
    val receiptUri: String? = null,
    val expenseAmount: Double = 0.0,
    val expenseCategory: String = "",
    val tripTypeId: String? = null,
    val isSyncedWithDrive: Boolean = false
) {
    fun getRoadPoints(): List<Pair<Double, Double>> {
        if (routePointsJson.isBlank()) return emptyList()
        return routePointsJson.split(";").mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size == 2) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()
                if (lat != null && lng != null) Pair(lat, lng) else null
            } else null
        }
    }
}
