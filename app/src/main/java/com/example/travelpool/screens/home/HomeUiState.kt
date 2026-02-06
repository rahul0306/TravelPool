package com.example.travelpool.screens.home

import com.example.travelpool.data.Trip

data class HomeUiState(
    val trips:List<Trip> = emptyList(),
    val isLoading: Boolean=true,
    val error:String?=null,
    val recentActivity: List<ActivityUi> = emptyList()
)