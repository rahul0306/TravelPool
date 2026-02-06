package com.example.travelpool.utils

import kotlin.random.Random

object CodeUtils {
    fun generate6DigitCode(): String {
        val n = Random.nextInt(100000, 1000000)
        return n.toString()
    }
}