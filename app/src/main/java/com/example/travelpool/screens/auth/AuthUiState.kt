package com.example.travelpool.screens.auth

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val userName: String? = null,
    val needsEmailVerification: Boolean = false,
    val verificationEmail: String? = null,

    val info: String? = null
)
