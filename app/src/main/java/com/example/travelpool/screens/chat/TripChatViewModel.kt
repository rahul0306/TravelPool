package com.example.travelpool.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class TripChatViewModel(
    private val repo: ChatRepository = ChatRepository()
): ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun observe(tripId: String) {
        viewModelScope.launch {
            repo.messagesFlow(tripId)
                .onStart { _uiState.value = _uiState.value.copy(isLoading = true, error = null) }
                .catch { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
                .collect { msgs ->
                    _uiState.value = ChatUiState(messages = msgs, isLoading = false, error = null)
                }
        }
    }
    fun send(tripId: String, text: String) {
        val t = text.trim()
        if (t.isBlank()) return

        viewModelScope.launch {
            val res = repo.sendMessage(tripId, t)
            if (res.isFailure) {
                _uiState.value = _uiState.value.copy(error = res.exceptionOrNull()?.message)
            }
        }
    }
}