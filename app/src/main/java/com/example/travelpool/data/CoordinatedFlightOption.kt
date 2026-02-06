package com.example.travelpool.data

data class CoordinatedFlightOption(
    val id: String,
    val scoreMinutes: Long,
    val travelers: List<TravelerFlightChoice>
)
