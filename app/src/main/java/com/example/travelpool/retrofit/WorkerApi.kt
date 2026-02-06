package com.example.travelpool.retrofit

import com.example.travelpool.data.AirportSuggestion
import com.example.travelpool.data.FlightOfferUi
import com.example.travelpool.data.HotelOfferUi
import com.example.travelpool.data.HotelSuggestion
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface WorkerApi {
    @GET("autocomplete/airports")
    suspend fun autocompleteAirports(
        @Query("q") q: String
    ): List<AirportSuggestion>

    @POST("search/flights")
    suspend fun searchFlights(@Body body: Map<String, String>): List<FlightOfferUi>

    @POST("search/hotels")
    suspend fun searchHotels(@Body body: Map<String, String>): List<HotelOfferUi>

    @GET("autocomplete/hotels")
    suspend fun autocompleteHotels(@Query("q") q: String): List<HotelSuggestion>

    @GET("whoami")
    suspend fun whoAmI(): Map<String, String>
}
