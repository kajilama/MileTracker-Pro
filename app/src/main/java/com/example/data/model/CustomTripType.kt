package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "custom_trip_types")
data class CustomTripType(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#0D9488",
    val isTaxDeductible: Boolean = true,
    val ratePerMile: Double = 0.67,
    val iconName: String = "Briefcase",
    val isDefault: Boolean = false
)
