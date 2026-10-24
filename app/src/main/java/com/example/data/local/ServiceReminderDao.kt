package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ServiceReminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceReminderDao {

    @Query("SELECT * FROM service_reminders WHERE vehicleId = :vehicleId ORDER BY title ASC")
    fun getRemindersForVehicle(vehicleId: String): Flow<List<ServiceReminder>>

    @Query("SELECT * FROM service_reminders ORDER BY title ASC")
    fun getAllReminders(): Flow<List<ServiceReminder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ServiceReminder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<ServiceReminder>)

    @Update
    suspend fun updateReminder(reminder: ServiceReminder)

    @Delete
    suspend fun deleteReminder(reminder: ServiceReminder)

    @Query("DELETE FROM service_reminders WHERE vehicleId = :vehicleId")
    suspend fun deleteRemindersForVehicle(vehicleId: String)
}
