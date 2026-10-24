package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "expense_receipts")
data class ExpenseReceipt(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val vehicleId: String? = null,
    val tripId: String? = null,
    val category: String = "Fuel / Charging", // Fuel / Charging, Repairs, Maintenance, Tires, Insurance, Registration, Parking / Tolls, Car Wash, Other
    val amount: Double = 0.0,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val odometer: Double? = null,
    val serviceReminderId: String? = null,
    val imageUri: String = "",
    val merchant: String = "",
    val notes: String = ""
) {
    companion object {
        val ALL_CATEGORIES = listOf(
            "Fuel / Charging",
            "Repairs",
            "Maintenance",
            "Tires",
            "Insurance",
            "Registration",
            "Parking / Tolls",
            "Car Wash",
            "Other"
        )
    }
}
