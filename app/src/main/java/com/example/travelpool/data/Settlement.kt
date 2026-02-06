package com.example.travelpool.data

data class Settlement(
    val id: String = "",
    val tripId: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val toUid: String = "",
    val toName: String = "",
    val amountCents: Long = 0L,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)