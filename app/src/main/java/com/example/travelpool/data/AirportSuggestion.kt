package com.example.travelpool.data

import kotlinx.serialization.Serializable

@Serializable
data class AirportSuggestion(
    val iataCode: String = "",
    val name: String = "",
    val cityName: String = "",
    val countryName: String = ""
){
    val label: String get() =
        buildString {
            if (cityName.isNotBlank()) append(cityName)
            if (countryName.isNotBlank()) append(", ").append(countryName)
            if (iataCode.isNotBlank()) append(" (").append(iataCode).append(")")
        }.ifBlank { name.ifBlank { iataCode } }
}
