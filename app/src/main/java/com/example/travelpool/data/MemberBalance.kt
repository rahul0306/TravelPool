package com.example.travelpool.data

data class MemberBalance(
    val uid: String,
    val name: String,
    val contributedCents: Long,
    val owesCents: Long,
    val netCents: Long
)
