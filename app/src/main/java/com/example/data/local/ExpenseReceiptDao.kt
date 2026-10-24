package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ExpenseReceipt
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseReceiptDao {
    @Query("SELECT * FROM expense_receipts ORDER BY dateTimestamp DESC")
    fun getAllReceipts(): Flow<List<ExpenseReceipt>>

    @Query("SELECT * FROM expense_receipts WHERE vehicleId = :vehicleId ORDER BY dateTimestamp DESC")
    fun getReceiptsByVehicle(vehicleId: String): Flow<List<ExpenseReceipt>>

    @Query("SELECT * FROM expense_receipts WHERE tripId = :tripId LIMIT 1")
    suspend fun getReceiptByTrip(tripId: String): ExpenseReceipt?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: ExpenseReceipt)

    @Update
    suspend fun updateReceipt(receipt: ExpenseReceipt)

    @Delete
    suspend fun deleteReceipt(receipt: ExpenseReceipt)

    @Query("DELETE FROM expense_receipts WHERE id = :id")
    suspend fun deleteReceiptById(id: String)
}
