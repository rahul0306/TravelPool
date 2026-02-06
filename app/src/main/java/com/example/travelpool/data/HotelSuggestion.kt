package com.example.travelpool.data

import kotlinx.serialization.Serializable

@Serializable
data class HotelSuggestion(
    val hotelId: String = "",
    val name: String="",
    val cityName: String="",
    val countryName: String="",
    val address: String? = null,
    val placeId: String? = null
)
