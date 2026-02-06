package com.example.travelpool.screens.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repo: NotificationRepository = NotificationRepository()
): ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init { observe() }

    private fun observe() {
        viewModelScope.launch {
            repo.notificationsFlow()
                .onStart { _uiState.value = _uiState.value.copy(isLoading = true, error = null) }
                .catch { e -> _uiState.value = NotificationsUiState(isLoading = false, error = e.message) }
                .collect { list ->
                    _uiState.value = NotificationsUiState(isLoading = false, notifications = list)
                }
        }
    }

    fun markRead(id: String) = viewModelScope.launch { repo.markRead(id) }
    fun markAllRead() = viewModelScope.launch { repo.markAllRead() }
    fun delete(id: String) = viewModelScope.launch { repo.deleteNotification(id) }
}