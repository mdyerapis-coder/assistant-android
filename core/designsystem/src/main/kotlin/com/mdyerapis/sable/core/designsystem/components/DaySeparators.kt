package com.mdyerapis.sable.core.designsystem.components

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** True when [previousTimestamp] and [timestamp] fall on different local days. 0L means "unknown" — never separates. */
fun needsDaySeparator(previousTimestamp: Long, timestamp: Long): Boolean {
    if (previousTimestamp <= 0L || timestamp <= 0L) return false
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(previousTimestamp).atZone(zone).toLocalDate() !=
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
}

/** Human label for a chat day divider: Today / Yesterday / e.g. "Mon, Aug 31". */
fun dayLabel(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}
