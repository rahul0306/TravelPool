package com.example.travelpool.screens.pool

import com.example.travelpool.data.MemberBalance
import com.example.travelpool.data.PoolContribution
import com.example.travelpool.data.PoolExpense
import com.example.travelpool.data.Settlement
import com.example.travelpool.data.TripMember

data class PoolUiState(
    val contributions: List<PoolContribution> = emptyList(),
    val expenses: List<PoolExpense> = emptyList(),
    val members: List<TripMember> = emptyList(),
    val balances: List<MemberBalance> = emptyList(),
    val suggestedSettlements: List<Settlement> = emptyList(),
    val settlementHistory: List<Settlement> = emptyList(),
    val totalContributedCents: Long = 0L,
    val totalSpentCents: Long = 0L,
    val balanceCents: Long = 0L,
    val isLoading: Boolean = true,
    val error: String? = null
)
