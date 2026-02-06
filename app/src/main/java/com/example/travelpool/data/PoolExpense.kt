package com.example.travelpool.data

data class PoolExpense(
    val id: String = "",
    val tripId: String = "",
    val title: String = "",
    val amountCents: Long = 0L,


    val paidByUid: String = "",
    val paidByName: String = "",


    val splitBetweenUids: List<String> = emptyList(),


    val splitType: String = "equal",


    val splitExactCents: Map<String, Long> = emptyMap(),


    val splitPercentBps: Map<String, Int> = emptyMap(),

    val createdAt: Long = System.currentTimeMillis()
)

