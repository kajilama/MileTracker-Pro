package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "saved_places")
data class SavedPlace(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val category: String = "Store", // "Home", "Office", "Store", "Client", "Warehouse"
    val lat: Double? = null,
    val lng: Double? = null
)
