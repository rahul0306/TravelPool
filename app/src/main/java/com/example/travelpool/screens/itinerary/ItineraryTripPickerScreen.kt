package com.example.travelpool.screens.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.travelpool.navigation.BottomNavigation
import com.example.travelpool.screens.home.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryTripPickerScreen(
    onTripSelected: (String) -> Unit,
    onBack: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
    navController: NavController
) {
    val state by homeViewModel.uiState.collectAsState()

    var query by remember { mutableStateOf("") }

    val filteredTrips = remember(state.trips, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) state.trips
        else state.trips.filter {
            it.name.lowercase().contains(q) || it.destination.lowercase().contains(q)
        }
    }
    var bottomSelected by remember { mutableIntStateOf(2) }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose a Trip") },
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
                Box(
                    modifier = Modifier
                        .padding(pad)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                Box(
                    modifier = Modifier
                        .padding(pad)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .padding(pad)
                        .fillMaxSize()
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        placeholder = { Text("Search trips…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredTrips, key = { it.id }) { trip ->
                            TripPickerCard(
                                name = trip.name,
                                destination = trip.destination,
                                dateText = runCatching { formatDateRange(trip.startDate, trip.endDate) }
                                    .getOrElse { "" },
                                membersText = runCatching { "${trip.members.size} members" }
                                    .getOrElse { "Members" },
                                badgeText = "Planning",
                                onClick = { onTripSelected(trip.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripPickerCard(
    name: String,
    destination: String,
    dateText: String,
    membersText: String,
    badgeText: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(badgeText) },
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = null
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(4.dp))

                    val line2 = if (dateText.isNotBlank()) "$destination • $dateText" else destination
                    Text(
                        text = line2,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = membersText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "›",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

private fun formatDateRange(startMillis: Long, endMillis: Long): String {
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    val s = fmt.format(Date(startMillis))
    val e = fmt.format(Date(endMillis))
    return if (s == e) s else "$s–$e"
}
