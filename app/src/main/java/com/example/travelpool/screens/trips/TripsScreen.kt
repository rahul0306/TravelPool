package com.example.travelpool.screens.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.travelpool.data.Trip
import com.example.travelpool.navigation.BottomNavigation
import com.example.travelpool.screens.home.HomeViewModel
import com.example.travelpool.screens.home.TripCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    onBack: (() -> Unit)? = null,
    onTripSelected: (Trip) -> Unit,
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var bottomSelected by remember { mutableIntStateOf(1) }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trips") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigation(
                selectedIndex = bottomSelected,
                onSelected = { idx ->
                    when (idx) {
                        0 -> navController.navigate("home")
                        1 -> navController.navigate("trips")
                        2 -> navController.navigate("itinerary")
                        3 -> navController.navigate("chat")
                    }
                }
            )
        },
    ) { pad ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(pad)) {
                    CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.Center))
                }
            }

            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(pad)) {
                    Text(
                        text = state.error.orEmpty(),
                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                    )
                }
            }

            state.trips.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(pad)) {
                    Text(
                        text = "No trips yet.",
                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(pad),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.trips, key = { it.id }) { trip ->
                        TripCard(
                            trip = trip,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onTripSelected(trip) }
                        )
                    }
                }
            }
        }
    }
}
