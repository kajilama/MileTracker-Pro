package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CustomTripType
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomTripTypeDao {
    @Query("SELECT * FROM custom_trip_types ORDER BY name ASC")
    fun getAllTripTypes(): Flow<List<CustomTripType>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripType(tripType: CustomTripType)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripTypes(tripTypes: List<CustomTripType>)

    @Update
    suspend fun updateTripType(tripType: CustomTripType)

    @Delete
    suspend fun deleteTripType(tripType: CustomTripType)

    @Query("DELETE FROM custom_trip_types WHERE id = :id")
    suspend fun deleteTripTypeById(id: String)

    @Query("UPDATE custom_trip_types SET isDefault = 0")
    suspend fun clearDefaultTripTypes()

    @Query("UPDATE custom_trip_types SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultTripType(id: String)
}
