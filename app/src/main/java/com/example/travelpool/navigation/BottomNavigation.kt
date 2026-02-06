package com.example.travelpool.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun BottomNavigation(
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedIndex == 0,
            onClick = { onSelected(0) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = selectedIndex == 1,
            onClick = { onSelected(1) },
            icon = { Icon(Icons.Default.TravelExplore, contentDescription = "Trips") },
            label = { Text("Trips") }
        )
        NavigationBarItem(
            selected = selectedIndex == 2,
            onClick = { onSelected(2) },
            icon = { Icon(Icons.Default.SystemUpdateAlt, contentDescription = "Itinerary") },
            label = { Text("Itinerary") }
        )
        NavigationBarItem(
            selected = selectedIndex == 3,
            onClick = { onSelected(3) },
            icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Chat") },
            label = { Text("Chat") }
        )
    }
}
