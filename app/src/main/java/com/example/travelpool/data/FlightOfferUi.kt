package com.example.travelpool.data

import kotlinx.serialization.Serializable

@Serializable
data class FlightOfferUi(
    val id: String,
    val summary: String,
    val price: String,
    val deepLink: String? = null
)
