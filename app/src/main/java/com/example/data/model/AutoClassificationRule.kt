package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "auto_classification_rules")
data class AutoClassificationRule(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val ruleName: String,
    val originPattern: String,
    val destinationPattern: String,
    val tripTypeName: String,
    val tripTypeId: String? = null,
    val isEnabled: Boolean = true,
    val isBidirectional: Boolean = true
)
