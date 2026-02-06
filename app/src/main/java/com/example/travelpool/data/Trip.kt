package com.example.travelpool.data

data class Trip(
    val id:String="",
    val name:String="",
    val joinCode: String = "",
    val destination:String="",
    val startDate:Long = 0L,
    val endDate:Long=0L,
    val startingAirport: String? = null,
    val ownerId:String="",
    val members:List<String> = emptyList(),
    val status:String= "Planning"
)
