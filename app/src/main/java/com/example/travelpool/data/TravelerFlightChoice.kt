package com.example.travelpool.data

data class TravelerFlightChoice(
    val travelerId: String,
    val travelerName: String,
    val originIata: String,
    val offer: FlightOfferUi
)
