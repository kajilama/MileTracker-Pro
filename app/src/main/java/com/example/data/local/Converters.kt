package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.TripPurpose

class Converters {
    @TypeConverter
    fun fromTripPurpose(value: TripPurpose): String = value.name

    @TypeConverter
    fun toTripPurpose(value: String): TripPurpose = try {
        TripPurpose.valueOf(value)
    } catch (e: Exception) {
        TripPurpose.BUSINESS
    }
}
