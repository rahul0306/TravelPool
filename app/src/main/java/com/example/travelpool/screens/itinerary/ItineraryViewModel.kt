package com.example.travelpool.screens.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelpool.data.ItineraryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class ItineraryViewModel(
    private val repo: ItineraryRepository = ItineraryRepository()
): ViewModel() {
    private val _uiState = MutableStateFlow(ItineraryUiState())
    val uiState: StateFlow<ItineraryUiState> = _uiState.asStateFlow()

    fun bind(tripId: String) {
        if (_uiState.value.tripId == tripId && _uiState.value.items.isNotEmpty()) return
        viewModelScope.launch {
            repo.itineraryFlow(tripId)
                .onStart { _uiState.value = _uiState.value.copy(isLoading = true, error = null, tripId = tripId) }
                .catch { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
                .collect { items ->
                    _uiState.value = _uiState.value.copy(isLoading = false, items = items)
                }
        }
    }

    fun setTypeFilter(type: String?) {
        _uiState.value = _uiState.value.copy(typeFilter = type)
    }

    suspend fun save(item: ItineraryItem): Result<Unit> =
        repo.upsert(_uiState.value.tripId, item)

    suspend fun delete(itemId: String): Result<Unit> =
        repo.delete(_uiState.value.tripId, itemId)
}