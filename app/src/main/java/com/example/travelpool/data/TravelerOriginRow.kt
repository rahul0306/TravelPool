package com.example.travelpool.data

data class TravelerOriginRow(
    val id: String,
    val name: String = "",
    val query: String = "",
    val selected: AirportSuggestion? = null,
    val suggestions: List<AirportSuggestion> = emptyList()
)
