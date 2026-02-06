package com.example.travelpool.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelpool.screens.home.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class ChatInboxViewModel(
    private val repo: HomeRepository = HomeRepository()
): ViewModel() {
    private val _uiState = MutableStateFlow(ChatInboxUiState())
    val uiState: StateFlow<ChatInboxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.tripsForCurrentUserFlow()
                .onStart { _uiState.value = _uiState.value.copy(isLoading = true, error = null) }
                .catch { e -> _uiState.value = ChatInboxUiState(isLoading = false, error = e.message) }
                .collect { trips -> _uiState.value = ChatInboxUiState(trips = trips, isLoading = false) }
        }
    }
}