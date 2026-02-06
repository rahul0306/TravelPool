package com.example.travelpool.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repo: ProfileRepository = ProfileRepository()
): ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState(isLoading = true))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val res = repo.fetchProfile()
            if (res.isSuccess) {
                val p = res.getOrNull()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    profile = p,
                    name = p?.name.orEmpty(),
                    homeAirport = p?.homeAirport.orEmpty()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = res.exceptionOrNull()?.message ?: "Failed to load profile"
                )
            }
        }
    }

    fun setEditing(editing: Boolean) {
        val p = _uiState.value.profile
        _uiState.value = _uiState.value.copy(
            isEditing = editing,
            name = if (!editing) (p?.name.orEmpty()) else _uiState.value.name,
            homeAirport = if (!editing) (p?.homeAirport.orEmpty()) else _uiState.value.homeAirport
        )
    }

    fun onNameChange(v: String) {
        _uiState.value = _uiState.value.copy(name = v)
    }

    fun onHomeAirportChange(v: String) {
        _uiState.value = _uiState.value.copy(homeAirport = v)
    }

    fun save() {
        viewModelScope.launch {
            val name = _uiState.value.name.trim()
            if (name.isBlank()) {
                _uiState.value = _uiState.value.copy(error = "Name cannot be empty")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val res = repo.updateProfile(
                name = name,
                homeAirport = _uiState.value.homeAirport
            )
            if (res.isSuccess) {
                val updated = repo.fetchProfile()
                val p = updated.getOrNull()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    isEditing = false,
                    profile = p,
                    name = p?.name.orEmpty(),
                    homeAirport = p?.homeAirport.orEmpty()
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = res.exceptionOrNull()?.message ?: "Failed to save"
                )
            }
        }
    }

    fun signOut() {
        repo.signOut()
    }
}