package com.example.data.model

data class AddressSuggestion(
    val title: String,
    val subtitle: String,
    val fullAddress: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isStoreOrPlace: Boolean = false,
    val isSavedPlace: Boolean = false,
    val distanceDisplay: String? = null
)
