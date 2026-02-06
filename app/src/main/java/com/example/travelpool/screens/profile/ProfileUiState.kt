package com.example.travelpool.screens.profile

import com.example.travelpool.data.UserProfile

data class ProfileUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val profile: UserProfile? = null,

    val name: String = "",
    val homeAirport: String = "",
    val isEditing: Boolean = false,
    val isSaving: Boolean = false
)
