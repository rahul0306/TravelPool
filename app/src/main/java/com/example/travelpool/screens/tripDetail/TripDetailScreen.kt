package com.example.travelpool.screens.tripDetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.travelpool.screens.notification.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    modifier: Modifier = Modifier,
    tripId: String,
    viewModel: TripDetailViewModel = viewModel(),
    onBack: () -> Unit,
    onOpenPool: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenTravelSearch: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(tripId) {
        viewModel.loadTrip(tripId)
    }
    LaunchedEffect(state.trip?.id, state.trip?.startDate) {
        val trip = state.trip ?: return@LaunchedEffect
        ReminderScheduler.scheduleTripStartReminders(
            context = context,
            tripId = trip.id,
            tripName = trip.name,
            startDateMillis = trip.startDate
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!state.isLoading && state.error == null && state.trip != null) {
                BottomActionBar(
                    primaryText = "Open shared pool",
                    secondaryText = "Cancel",
                    onPrimary = { onOpenPool(tripId) },
                    onSecondary = onBack
                )
            }
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            state.error != null -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            state.trip != null -> {
                val trip = state.trip!!

                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    HeaderScenic(
                        title = "Trip details",
                        onBack = onBack,
                        onDone = {  },
                        onChatClick = { onOpenChat(tripId) },
                        onSearchClick = { onOpenTravelSearch(tripId) },
                        onCheckClick = {
                            viewModel.markCompleted(tripId)
                        }
                    )

                    TripBasicsCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 130.dp, bottom = 96.dp)
                            .align(Alignment.TopCenter),
                        tripName = trip.name,
                        destination = trip.destination,
                        startDateMillis = trip.startDate,
                        endDateMillis = trip.endDate,
                        startingCity = trip.startingAirport,
                        members = state.members,
                        joinCode = trip.joinCode.ifBlank { trip.id },
                        onOpenPool = { onOpenPool(tripId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderScenic(
    title: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onChatClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCheckClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Text(
                text = title,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search flights & hotels",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            IconButton(onClick = onCheckClick) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Confirm",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            IconButton(onClick = onChatClick) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Trip Chat",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
        )
    }
}

@Composable
private fun TripBasicsCard(
    modifier: Modifier = Modifier,
    tripName: String,
    destination: String,
    startDateMillis: Long,
    endDateMillis: Long,
    startingCity: String?,
    members: List<com.example.travelpool.data.TripMember>,
    joinCode: String,
    onOpenPool: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    fun fmt(millis: Long): String =
        if (millis <= 0L) "" else dateFmt.format(Date(millis))

    Spacer(Modifier.height(14.dp))

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Trip basics",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "View your trip details and share the invite code.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Label("Trip name")
            ReadonlyField(value = tripName, trailing = null)

            Spacer(Modifier.height(12.dp))

            Label("Destination")
            ReadonlyField(
                value = destination,
                trailing = {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            Spacer(Modifier.height(12.dp))

            Label("Dates")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReadonlyField(
                    modifier = Modifier.weight(1f),
                    value = fmt(startDateMillis).ifBlank { "Start date" },
                    trailing = {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                ReadonlyField(
                    modifier = Modifier.weight(1f),
                    value = fmt(endDateMillis).ifBlank { "End date" },
                    trailing = {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            Label("Starting city / airport (optional)")
            ReadonlyField(
                value = startingCity ?: "Not set",
                trailing = {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            Spacer(Modifier.height(14.dp))

            Label("Members")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(members) { m ->
                        AssistChip(
                            onClick = {  },
                            label = {
                                Text(
                                    text = m.name.ifEmpty { m.email },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Label("Trip code (share this to invite)")
            Surface(
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SelectionContainer(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = joinCode,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    primaryText: String,
    secondaryText: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(primaryText, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onSecondary) {
            Text(secondaryText)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ReadonlyField(
    modifier: Modifier = Modifier,
    value: String,
    trailing: (@Composable (() -> Unit))?
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.width(10.dp))
                trailing()
            }
        }
    }
}