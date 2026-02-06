package com.example.travelpool.screens.chat

import com.example.travelpool.data.ChatMessage

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
