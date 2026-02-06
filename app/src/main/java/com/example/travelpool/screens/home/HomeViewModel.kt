package com.example.travelpool.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelpool.data.Trip
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HomeViewModel(
    private val repo: HomeRepository = HomeRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeTrips()
    }

    private fun observeTrips() {
        viewModelScope.launch {
            repo.tripsForCurrentUserFlow()
                .onStart { _uiState.value = _uiState.value.copy(isLoading = true, error = null) }
                .catch { e ->
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        error = e.message,
                        trips = emptyList(),
                        recentActivity = emptyList()
                    )
                }
                .collect { trips ->
                    _uiState.value = _uiState.value.copy(
                        trips = trips,
                        isLoading = false,
                        error = null
                    )

                    loadRecentActivity(trips)
                }
        }
    }

    fun createTrip(
        name: String,
        destination: String,
        startDate: Long,
        endDate: Long,
        startingAirport: String?
    ) {
        viewModelScope.launch {
            val result = repo.createTrip(name, destination, startDate, endDate, startingAirport)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }


    fun joinTrip(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Enter a trip code")
            return
        }

        viewModelScope.launch {
            val result = repo.joinTripByCode(trimmed)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    private fun loadRecentActivity(trips: List<Trip>) {
        viewModelScope.launch {
            try {
                val db = Firebase.firestore
                val all = mutableListOf<ActivityUi>()
                val perTripLimit = 3

                for (trip in trips) {
                    val tripRef = db.collection("trips").document(trip.id)

                    val memberSnap = tripRef.collection("members").get().await()
                    val nameByUid = memberSnap.documents.associate { doc ->
                        val uid = doc.getString("uid") ?: doc.id
                        val name = doc.getString("name") ?: "Member"
                        uid to name
                    }

                    val expSnap = tripRef.collection("expenses")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(perTripLimit.toLong())
                        .get()
                        .await()

                    expSnap.documents.forEach { d ->
                        val title = d.getString("title") ?: "Expense"
                        val amountCents = d.getLong("amountCents") ?: 0L
                        val paidByUid = d.getString("paidByUid") ?: ""
                        val createdAt = d.getLong("createdAt") ?: 0L

                        val who = nameByUid[paidByUid] ?: "Member"
                        val amount = "₹${amountCents / 100}"

                        all += ActivityUi(
                            name = who,
                            action = "New expense",
                            detail = title,
                            meta = "$amount • ${trip.name}",
                            createdAt = createdAt
                        )
                    }

                    val conSnap = tripRef.collection("contributions")
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(perTripLimit.toLong())
                        .get()
                        .await()

                    conSnap.documents.forEach { d ->
                        val uid = d.getString("uid") ?: ""
                        val amountCents = d.getLong("amountCents") ?: 0L
                        val createdAt = d.getLong("createdAt") ?: 0L

                        val who = nameByUid[uid] ?: "Member"
                        val amount = "₹${amountCents / 100}"

                        all += ActivityUi(
                            name = who,
                            action = "Contribution",
                            detail = "For ${trip.name}",
                            meta = amount,
                            createdAt = createdAt
                        )
                    }
                }

                val merged = all.sortedByDescending { it.createdAt }.take(5)

                _uiState.value = _uiState.value.copy(recentActivity = merged)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(recentActivity = emptyList())
            }
        }
    }
}
