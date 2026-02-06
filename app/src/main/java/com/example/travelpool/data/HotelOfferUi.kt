package com.example.travelpool.data

import kotlinx.serialization.Serializable

@Serializable
data class HotelOfferUi(
    val id: String,
    val name: String,
    val price: String,
    val address: String? = null,
    val deepLink: String? = null
)
