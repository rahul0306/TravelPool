package com.example.travelpool.screens.search

import com.example.travelpool.data.AirportSuggestion
import com.example.travelpool.data.FlightOfferUi
import com.example.travelpool.data.HotelOfferUi
import com.example.travelpool.data.HotelSuggestion
import com.example.travelpool.retrofit.WorkerApi
import com.example.travelpool.retrofit.WorkerApiFactory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class TravelSearchRepository(
    private val api: WorkerApi = WorkerApiFactory.create(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun autocompleteAirports(query: String): List<AirportSuggestion> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return api.autocompleteAirports(q)
    }

    suspend fun autocompleteHotels(query: String): List<HotelSuggestion> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return api.autocompleteHotels(q)
    }

    suspend fun whoAmI(): String {
        return api.whoAmI()["uid"].orEmpty()
    }

    suspend fun getTripMembers(tripId: String): List<TripMemberUi> {
        val snap = db.collection("trips")
            .document(tripId)
            .collection("members")
            .get()
            .await()

        return snap.documents.mapNotNull { d ->
            val name =
                d.getString("name")
                    ?: d.getString("displayName")
                    ?: "Member"

            TripMemberUi(uid = d.id, name = name)
        }
    }




    suspend fun searchFlights(
        originIata: String,
        destIata: String,
        departDate: String,
        returnDate: String?,
        adults: Int
    ): List<FlightOfferUi> {
        val body = mutableMapOf(
            "origin" to originIata,
            "destination" to destIata,
            "departureDate" to departDate,
            "adults" to adults.toString()
        )
        returnDate?.let { body["returnDate"] = it }

        return api.searchFlights(body)
    }

    suspend fun searchHotels(
        cityOrPlaceId: String,
        checkIn: String,
        checkOut: String,
        rooms: Int
    ): List<HotelOfferUi> {
        val body = mapOf(
            "placeId" to cityOrPlaceId,
            "checkIn" to checkIn,
            "checkOut" to checkOut,
            "rooms" to rooms.toString()
        )

        return api.searchHotels(body)
    }

}
