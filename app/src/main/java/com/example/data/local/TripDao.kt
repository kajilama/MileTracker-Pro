package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Trip
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY dateTimestamp DESC")
    fun getAllTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getTripById(id: String): Trip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<Trip>)

    @Update
    suspend fun updateTrip(trip: Trip)

    @Delete
    suspend fun deleteTrip(trip: Trip)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTripById(id: String)

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    @Query("UPDATE trips SET isSyncedWithDrive = 1")
    suspend fun markAllAsSynced()

    @Query("SELECT * FROM trips WHERE dateTimestamp >= :start AND dateTimestamp <= :end ORDER BY dateTimestamp DESC")
    fun getTripsInRange(start: Long, end: Long): Flow<List<Trip>>
}
