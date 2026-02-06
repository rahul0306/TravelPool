package com.example.travelpool.data

data class PoolContribution(
    val id: String = "",
    val tripId: String = "",
    val uid: String = "",
    val name: String = "",
    val amountCents: Long = 0L,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
