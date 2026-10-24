package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "service_reminders")
data class ServiceReminder(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val vehicleId: String,
    val title: String,
    val intervalMiles: Double = 5000.0,
    val intervalMonths: Int = 6,
    val lastPerformedOdo: Double = 0.0,
    val lastPerformedDate: Long = System.currentTimeMillis(),
    val fuelTypeApplicability: String = "ALL", // "ALL", "GAS_HYBRID", "EV"
    val isCompleted: Boolean = false,
    val notes: String = "",
    val disclaimer: String = "Generic interval, verify with your owner's manual"
)
