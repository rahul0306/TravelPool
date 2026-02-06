package com.example.travelpool.screens.itinerary

import com.example.travelpool.data.ItineraryItem

data class ItineraryUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val tripId: String = "",
    val items: List<ItineraryItem> = emptyList(),
    val typeFilter: String? = null
)
