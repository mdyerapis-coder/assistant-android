package com.mdyerapis.sable.core.network

import kotlin.math.min
import kotlin.random.Random

object BackoffPolicy {
    fun exponentialBackoff(attempt: Int, baseMs: Long = 1000L, maxMs: Long = 30_000L): Long {
        val exp = min(attempt, 6)
        val jitter = Random.nextLong(0, baseMs)
        return min((baseMs shl exp) + jitter, maxMs)
    }
}