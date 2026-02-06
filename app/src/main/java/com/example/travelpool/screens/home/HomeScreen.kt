package com.example.travelpool.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.travelpool.R
import com.example.travelpool.data.Trip
import com.example.travelpool.navigation.BottomNavigation
import com.example.travelpool.screens.notification.NotificationRepository
import com.example.travelpool.screens.profile.ProfileRepository
import com.example.travelpool.utils.CreateTripDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    onTripSelected: (Trip) -> Unit,
    navController: NavController,
    onOpenChat: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val userName = Firebase.auth.currentUser?.displayName ?: "Traveler"

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }

    var bottomSelected by remember { mutableIntStateOf(0) }

    val notificationRepo = remember { NotificationRepository() }
    var unreadCount by remember { mutableIntStateOf(0) }
    var defaultAirport by remember { mutableStateOf("") }


    LaunchedEffect(Unit) {
        notificationRepo.notificationsFlow().collect { list ->
            unreadCount = list.count { !it.isRead }
        }
    }
    LaunchedEffect(Unit) {
        val res = ProfileRepository().fetchProfile()
        if (res.isSuccess) {
            defaultAirport = res.getOrNull()?.homeAirport.orEmpty()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomNavigation(
                selectedIndex = bottomSelected,
                onSelected = { idx ->
                    when (idx) {
                        0 -> navController.navigate("home")
                        1 -> navController.navigate("trips")
                        2 -> navController.navigate("itinerary")
                        3 -> onOpenChat()
                    }
                }
            )
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }

            state.error != null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Text(
                        text = state.error.orEmpty(),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    HeroHeader(
                        appName = "Travel Pool",
                        notificationCount = unreadCount,
                        onBellClick = { navController.navigate("notifications") },
                        onProfileClick = { navController.navigate("profile") }
                    )

                    Spacer(Modifier.height(10.dp))

                    GreetingCard(
                        userName = userName,
                        onCreateNewTrip = { showCreateDialog = true },
                        onJoinTrip = { showJoinDialog = true }
                    )

                    Spacer(Modifier.height(14.dp))

                    SectionHeader(
                        title = "Upcoming Trips"
                    )

                    val upcomingTrips = state.trips.take(3)
                    if (upcomingTrips.isEmpty()) {
                        EmptyUpcomingTrips()
                    } else {
                        UpcomingTripsRow(
                            trips = upcomingTrips,
                            onTripClick = onTripSelected
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    SectionHeader(
                        title = "Recent Activity"
                    )
                    RecentActivityList(items = state.recentActivity.take(3))


                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTripDialog(
            defaultAirport = defaultAirport,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, destination, start, end, airport ->
                viewModel.createTrip(name, destination, start, end, airport)
            }
        )
    }


    if (showJoinDialog) {
        JoinTripDialog(
            code = joinCode,
            onCodeChange = { joinCode = it },
            onDismiss = { showJoinDialog = false },
            onJoin = {
                viewModel.joinTrip(joinCode.trim())
                showJoinDialog = false
                joinCode = ""
            }
        )
    }
}

@Composable
private fun HeroHeader(
    appName: String,
    notificationCount: Int,
    onBellClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Image(
            painter = painterResource(id = R.drawable.travelpool_header),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            contentScale = ContentScale.FillBounds
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBellClick,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(color = Color.White),
                ) {
                    BadgedBox(
                        badge = {
                            if (notificationCount > 0) {
                                Badge {
                                    Text(
                                        text = if (notificationCount > 99) "99+" else notificationCount.toString()
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(color = Color.White),
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

    }
}

@Composable
private fun GreetingCard(
    userName: String,
    onCreateNewTrip: () -> Unit,
    onJoinTrip: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Hi, $userName 👋",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Let’s plan your next trip!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        ActionRow(
            primaryText = "Create New Trip",
            leadingIcon = Icons.Default.Add,
            isPrimary = true,
            onClick = onCreateNewTrip
        )

        Spacer(Modifier.height(10.dp))

        ActionRow(
            primaryText = "Join Trip",
            leadingIcon = Icons.Default.QrCode2,
            trailingIcon = Icons.Default.QrCode2,
            isPrimary = false,
            onClick = onJoinTrip
        )
    }
}

@Composable
private fun ActionRow(
    primaryText: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = if (isPrimary) Color(0xFFFF7A2F) else MaterialTheme.colorScheme.surface
    val fg = if (isPrimary) Color.White else MaterialTheme.colorScheme.onSurface
    val border =
        if (isPrimary) null else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)

    Surface(
        onClick = onClick,
        shape = shape,
        color = bg,
        tonalElevation = if (isPrimary) 2.dp else 0.dp,
        shadowElevation = if (isPrimary) 1.dp else 0.dp,
        modifier = Modifier.fillMaxWidth(),
        border = if (border == null) null else BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(leadingIcon, contentDescription = null, tint = fg)
            Spacer(Modifier.width(10.dp))
            Text(
                text = primaryText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = fg
            )
            Spacer(Modifier.weight(1f))
            if (trailingIcon != null) {
                Icon(trailingIcon, contentDescription = null, tint = fg)
            } else {
                Text("›", color = fg, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun EmptyUpcomingTrips() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = "No trips yet. Create your first one!",
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun UpcomingTripsRow(
    trips: List<Trip>,
    onTripClick: (Trip) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(trips,key={it.id}) { trip ->
            TripCard(
                trip = trip,
                modifier = Modifier.width(260.dp),
                onClick = { onTripClick(trip) }
            )
        }
    }
}

@Composable
fun TripCard(
    trip: Trip,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val chipText = if (trip.status == "completed") "Completed" else "Planning"
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = modifier.height(150.dp)
    ) {
        Column(Modifier.fillMaxSize()) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFEAF6FF), Color(0xFFD9EEFF))
                        )
                    )
                    .padding(10.dp)
            ) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(chipText) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFFEFF3FF),
                        labelColor = Color(0xFF2B4C9A),
                        disabledContainerColor = Color(0xFFEFF3FF),
                        disabledLabelColor = Color(0xFF2B4C9A)
                    ),
                    border = BorderStroke(0.dp, Color.Transparent)
                )
            }

            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = trip.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = buildTripSubtitle(trip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "₹—",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Tap to open",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun buildTripSubtitle(trip: Trip): String {
    return runCatching {
        val range = formatDateRange(trip.startDate, trip.endDate)
        "${trip.destination} • $range"
    }.getOrElse {
        trip.destination
    }
}

private fun formatDateRange(startMillis: Long, endMillis: Long): String {
    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
    val s = fmt.format(Date(startMillis))
    val e = fmt.format(Date(endMillis))
    return if (s == e) s else "$s–$e"
}

data class ActivityUi(
    val name: String,
    val action: String,
    val detail: String,
    val meta: String,
    val createdAt: Long
)

@Composable
private fun RecentActivityList(items: List<ActivityUi>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (items.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No recent activity yet.",
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        items.forEachIndexed { i, item ->
            ActivityRow(item)
            if (i != items.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityUi) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.name.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.action,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = item.meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun JoinTripDialog(
    code: String,
    onCodeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onJoin: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Trip") },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text("Trip code") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Enter the code shared by the trip organizer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onJoin,
                enabled = code.trim().isNotEmpty()
            ) { Text("Join") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

