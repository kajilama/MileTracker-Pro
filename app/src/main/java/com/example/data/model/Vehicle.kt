package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val makeModel: String,
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val licensePlate: String = "",
    val vin: String = "",
    val fuelType: String = "Hybrid", // "Gas", "Diesel", "Hybrid", "Plug-in Hybrid", "Electric EV"
    val startingOdometer: Double = 0.0,
    val currentOdometer: Double = startingOdometer,
    val odometerJan1: Double? = null,
    val odometerDec31: Double? = null,
    val photoUri: String? = null,
    val manualUrl: String? = null,
    val oilChangeIntervalMiles: Double = 5000.0,
    val lastOilChangeOdo: Double = startingOdometer,
    val tireRotationIntervalMiles: Double = 7500.0,
    val lastTireRotationOdo: Double = startingOdometer,
    val cabinAirFilterIntervalMiles: Double = 15000.0,
    val lastCabinFilterOdo: Double = startingOdometer,
    val brakeFluidIntervalMiles: Double = 25000.0,
    val lastBrakeFluidOdo: Double = startingOdometer,
    val evBatteryCoolantDueMonths: Int = 48,
    val oemBrand: String = "Toyota", // Toyota, Tesla, Honda, Ford, Chevrolet, Hyundai, Nissan
    val nextInspectionDate: String = "Dec 2026",
    val maintenanceNotes: String = "",
    val isDefault: Boolean = false
)
