package com.example.travelpool.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.travelpool.retrofit.FirebaseTokenProvider
import com.example.travelpool.screens.auth.AuthGate
import com.example.travelpool.screens.auth.AuthScreen
import com.example.travelpool.screens.chat.ChatInboxScreen
import com.example.travelpool.screens.chat.TripChatScreen
import com.example.travelpool.screens.home.HomeScreen
import com.example.travelpool.screens.itinerary.ItineraryDetailScreen
import com.example.travelpool.screens.itinerary.ItineraryScreen
import com.example.travelpool.screens.itinerary.ItineraryTripPickerScreen
import com.example.travelpool.screens.notification.NotificationsScreen
import com.example.travelpool.screens.pool.PoolScreen
import com.example.travelpool.screens.profile.ProfileScreen
import com.example.travelpool.screens.search.TravelSearchScreen
import com.example.travelpool.screens.settleup.SettleUpScreen
import com.example.travelpool.screens.tripDetail.TripDetailScreen
import com.example.travelpool.screens.trips.TripsScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TravelNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    val u = Firebase.auth.currentUser
    android.util.Log.d("TravelNav", "currentUser=${u?.uid} email=${u?.email} verified=${u?.isEmailVerified}")

    fun navigateSingleTop(route: String) {
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
        }
    }

    NavHost(navController, startDestination = "gate") {

        composable("gate") {
            AuthGate(
                navToAuth = {
                    navController.navigate("auth") {
                        popUpTo("gate") { inclusive = true }
                    }
                },
                navToMain = {
                    FirebaseTokenProvider.refreshAsync(force = true)
                    navController.navigate("home") {
                        popUpTo("gate") { inclusive = true }
                    }
                }
            )
        }

        composable("auth") {
            AuthScreen(
                onLoggedIn = {
                    FirebaseTokenProvider.refreshAsync(force = true)
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                },
            )
        }

        composable("home") {
            HomeScreen(
                onTripSelected = { trip ->
                    navController.navigate("tripDetail/${trip.id}")
                },
                navController = navController,
                onOpenChat = { navController.navigate("chat") },
            )
        }

        composable("tripDetail/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""

            TripDetailScreen(
                tripId = tripId,
                onBack = { navController.popBackStack() },
                onOpenPool = { id -> navController.navigate("pool/$id") },
                onOpenChat = { id -> navController.navigate("chat/$id") },
                onOpenTravelSearch = { id -> navController.navigate("travelSearch/$id") },
            )
        }

        composable("travelSearch/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            TravelSearchScreen(
                tripId = tripId,
                onBack = { navController.popBackStack() }
            )
        }

        composable("pool/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            PoolScreen(
                tripId = tripId,
                onBack = { navController.popBackStack() },
                onOpenSettleUp = { id -> navController.navigate("settleup/$id") }
            )
        }

        composable("chat") {
            ChatInboxScreen(
                onOpenTripChat = { tripId -> navController.navigate("chat/$tripId") },
                onBack = { navController.popBackStack() },

                onHome = { navigateSingleTop("home") },
                onTrips = { navigateSingleTop("trips") },

                onItinerary = { navigateSingleTop("itinerary") },

                onChat = {  }
            )
        }

        composable("chat/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            TripChatScreen(
                tripId = tripId,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settleup/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            SettleUpScreen(
                tripId = tripId,
                onBack = { navController.popBackStack() }
            )
        }

        composable("notifications") {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpenDeepLink = { deepLink ->
                    navController.navigate(deepLink)
                }
            )
        }

        composable("profile") {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate("auth") {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("itinerary") {
            ItineraryTripPickerScreen(
                onTripSelected = { tripId ->
                    navController.navigate("itinerary/$tripId")
                },
                onBack = { navController.popBackStack() },
                navController=navController
            )
        }

        composable("itinerary/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            ItineraryScreen(
                tripId = tripId,
                onOpenDetail = { itemId ->
                    navController.navigate("itinerary/$tripId/item/$itemId")
                },
                onBack = { navController.popBackStack() },
                navController = navController,
                onOpenChat = { navController.navigate("chat") }
            )
        }

        composable("itinerary/{tripId}/item/{itemId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            ItineraryDetailScreen(
                tripId = tripId,
                itemId = itemId,
                onBack = { navController.popBackStack() }
            )
        }

        composable("trips") {
            TripsScreen(
                onTripSelected = { trip ->
                    navController.navigate("tripDetail/${trip.id}")
                },
                navController = navController
            )
        }


    }
}