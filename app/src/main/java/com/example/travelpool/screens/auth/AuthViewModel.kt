package com.example.travelpool.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repo: AuthRepository = AuthRepository()
): ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(isLoading = false))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, info = null)

            val result = repo.login(email, password)
            if (result.isSuccess) {
                if (!repo.isEmailVerified()) {
                    _uiState.value = AuthUiState(
                        needsEmailVerification = true,
                        verificationEmail = repo.currentUserEmail() ?: email,
                        info = "Please verify your email to continue.",
                        isLoggedIn = false
                    )
                    return@launch
                }

                val name = repo.fetchCurrentUserProfile()?.name
                _uiState.value = AuthUiState(isLoggedIn = true, userName = name)
            } else {
                _uiState.value = AuthUiState(error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun signUp(name: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, info = null)

            val result = repo.signUp(name, email, password)
            _uiState.value = if (result.isSuccess) {
                AuthUiState(
                    needsEmailVerification = true,
                    verificationEmail = email,
                    info = "Verification email sent to $email",
                    isLoggedIn = false
                )
            } else {
                AuthUiState(error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, info = null)
            val result = repo.resendVerificationEmail()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                info = if (result.isSuccess) "Verification email resent." else null,
                error = if (result.isFailure) result.exceptionOrNull()?.message else null
            )
        }
    }

    fun logout() {
        repo.logout()
        _uiState.value = AuthUiState()
    }

    fun checkIfLoggedIn() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, info = null)

            repo.reloadCurrentUser()

            val uid = repo.currentUserUid()
            if (uid == null) {
                _uiState.value = AuthUiState(isLoggedIn = false, isLoading = false)
                return@launch
            }

            if (!repo.isEmailVerified()) {
                _uiState.value = AuthUiState(
                    needsEmailVerification = true,
                    verificationEmail = repo.currentUserEmail(),
                    info = "Please verify your email to continue.",
                    isLoggedIn = false,
                    isLoading = false
                )
                return@launch
            }

            val name = repo.fetchCurrentUserProfile()?.name
            _uiState.value = AuthUiState(isLoggedIn = true, userName = name, isLoading = false)
        }
    }
}