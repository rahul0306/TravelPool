package com.example.travelpool.data

data class AppNotification(
    val id: String = "",
    val uid: String = "",
    val tripId: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val deepLink: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
