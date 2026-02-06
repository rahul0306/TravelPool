package com.example.travelpool.screens.tripDetail

import com.example.travelpool.data.Trip
import com.example.travelpool.data.TripMember

data class TripDetailUiState(
    val trip: Trip? = null,
    val members:List<TripMember> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
