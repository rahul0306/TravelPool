package com.example.travelpool.screens.notification

import com.example.travelpool.data.AppNotification

data class NotificationsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val notifications: List<AppNotification> = emptyList()
){
    val unreadCount: Int get() = notifications.count { !it.isRead }
}
