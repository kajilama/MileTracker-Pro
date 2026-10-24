package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AutoClassificationRule
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoClassificationRuleDao {
    @Query("SELECT * FROM auto_classification_rules ORDER BY ruleName ASC")
    fun getAllRules(): Flow<List<AutoClassificationRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutoClassificationRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<AutoClassificationRule>)

    @Update
    suspend fun updateRule(rule: AutoClassificationRule)

    @Delete
    suspend fun deleteRule(rule: AutoClassificationRule)

    @Query("DELETE FROM auto_classification_rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)
}
