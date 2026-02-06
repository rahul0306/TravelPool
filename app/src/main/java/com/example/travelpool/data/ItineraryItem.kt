package com.example.travelpool.data

data class ItineraryItem(
    val id: String = "",
    val tripId: String = "",

    val type: String = "activity",

    val title: String = "",
    val location: String = "",
    val startTime: Long = 0L,
    val endTime: Long? = null,

    val confirmationNumber: String = "",
    val url: String = "",
    val notes: String = "",

    val createdByUid: String = "",
    val createdByName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
