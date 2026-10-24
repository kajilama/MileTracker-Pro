package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SavedPlace
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY name ASC")
    fun getAllSavedPlaces(): Flow<List<SavedPlace>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: SavedPlace)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaces(places: List<SavedPlace>)

    @Update
    suspend fun updatePlace(place: SavedPlace)

    @Delete
    suspend fun deletePlace(place: SavedPlace)

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun deletePlaceById(id: String)
}
