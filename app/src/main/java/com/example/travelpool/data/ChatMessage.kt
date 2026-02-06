package com.example.travelpool.data

data class ChatMessage(
    val id: String = "",
    val tripId: String = "",
    val text: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
