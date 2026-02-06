package com.example.travelpool.screens.search

import com.example.travelpool.data.AirportSuggestion
import com.example.travelpool.data.CoordinatedFlightOption
import com.example.travelpool.data.FlightOfferUi
import com.example.travelpool.data.HotelOfferUi
import com.example.travelpool.data.HotelSuggestion
import com.example.travelpool.data.TravelerOriginRow

data class TravelSearchUiState(
    val tab: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,

    val originQuery: String = "",
    val destinationQuery: String = "",
    val originSelected: AirportSuggestion? = null,
    val destinationSelected: AirportSuggestion? = null,
    val departDate: String = "",
    val returnDate: String = "",
    val adults: Int = 1,
    val originSuggestions: List<AirportSuggestion> = emptyList(),
    val destinationSuggestions: List<AirportSuggestion> = emptyList(),
    val flightResults: List<FlightOfferUi> = emptyList(),

    val hotelPlaceQuery: String = "",
    val hotelPlaceId: String = "",
    val checkIn: String = "",
    val checkOut: String = "",
    val rooms: Int = 1,
    val hotelResults: List<HotelOfferUi> = emptyList(),
    val hotelSuggestions: List<HotelSuggestion> = emptyList(),
    val hotelSelected: HotelSuggestion? = null,

    val travelers: List<TravelerOriginRow> = listOf(
        TravelerOriginRow(id = "t1", name = "Traveler 1")
    ),
    val flightResultsByTraveler: Map<String, List<FlightOfferUi>> = emptyMap(),
    val coordinatedOptions: List<CoordinatedFlightOption> = emptyList(),

    val tripMembers: List<TripMemberUi> = emptyList(),
    val selectedTravelerUids: Set<String> = emptySet(),
    val originByTraveler: Map<String, AirportSuggestion?> = emptyMap()
    )
