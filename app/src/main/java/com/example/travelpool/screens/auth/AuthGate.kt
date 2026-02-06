package com.example.travelpool.screens.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AuthGate(
    navToAuth: () -> Unit,
    navToMain: () -> Unit,
    viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkIfLoggedIn()
    }

    LaunchedEffect(state.isLoading, state.isLoggedIn, state.needsEmailVerification) {
        if (state.isLoading) return@LaunchedEffect

        when {
            state.isLoggedIn -> navToMain()
            state.needsEmailVerification -> navToAuth()
            else -> navToAuth()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
