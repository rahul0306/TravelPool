package com.example.travelpool.screens.tripDetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelpool.screens.home.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TripDetailViewModel(
    private val repo: HomeRepository = HomeRepository(),
): ViewModel() {
    private val _uiState = MutableStateFlow(TripDetailUiState())
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    fun loadTrip(tripId: String) {
        if (_uiState.value.trip != null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val tripResult = repo.getTrip(tripId)
            val membersResult = repo.getTripMembers(tripId)

            val trip = tripResult.getOrNull()
            val members = membersResult.getOrNull()

            if (trip == null || members == null) {
                val errorMessage = tripResult.exceptionOrNull()?.message
                    ?: membersResult.exceptionOrNull()?.message
                    ?: "Failed to load trip"
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorMessage
                )
            } else {
                _uiState.value = TripDetailUiState(
                    trip = trip,
                    members = members,
                    isLoading = false,
                    error = null
                )
            }
        }
    }

    fun markCompleted(tripId: String) {
        viewModelScope.launch {
            val res = repo.markTripCompleted(tripId)
            if (res.isFailure) {
            } else {
                _uiState.value = _uiState.value.copy(
                    trip = _uiState.value.trip?.copy(status = "completed")
                )
            }
        }
    }

}