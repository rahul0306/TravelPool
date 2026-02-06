package com.example.travelpool.utils

import java.math.BigDecimal
import java.math.RoundingMode

object MoneyUtils {
    fun parseToCents(input: String): Long? {
        val cleaned = input.trim()
            .replace("₹", "")
            .replace(",", "")

        if (cleaned.isBlank()) return null

        return try {
            val bd = BigDecimal(cleaned)
                .setScale(2, RoundingMode.HALF_UP)
            bd.movePointRight(2).longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    fun formatCents(amountCents: Long): String {
        val bd = BigDecimal(amountCents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP)
        return bd.toPlainString()
    }
}