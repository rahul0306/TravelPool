package com.example.travelpool.screens.chat

import com.example.travelpool.data.Trip

data class ChatInboxUiState(
    val trips: List<Trip> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
