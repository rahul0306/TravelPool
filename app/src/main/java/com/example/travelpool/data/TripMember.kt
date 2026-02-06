package com.example.travelpool.data

data class TripMember(
    val uid:String="",
    val name:String="",
    val email:String="",
    val role:String="member",
    val joinedAt:Long= System.currentTimeMillis()
)
