package com.mdyerapis.sable.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Chat list day-divider contract: separators appear only when two adjacent
 * timestamped messages cross midnight; timestamp-less messages (0L: pre-timestamp-schema rows or an
 * in-flight stream before completion) never produce one.
 */
class DaySeparatorsTest {

    private fun ts(epochSecond: Long): Long =
        Instant.ofEpochSecond(epochSecond).toEpochMilli()

    private fun localEpochSec(year: Int, month: Int, day: Int, hour: Int): Long =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZoneId.systemDefault()).toEpochSecond()

    @Test
    fun `same day needs no separator`() {
        assertFalse(needsDaySeparator(ts(localEpochSec(2026, 9, 1, 8)), ts(localEpochSec(2026, 9, 1, 21))))
    }

    @Test
    fun `crossing midnight needs a separator`() {
        assertTrue(needsDaySeparator(ts(localEpochSec(2026, 8, 31, 23)), ts(localEpochSec(2026, 9, 1, 6))))
    }

    @Test
    fun `missing timestamps never produce a separator`() {
        assertFalse(needsDaySeparator(0L, ts(localEpochSec(2026, 9, 1, 6))))
        assertFalse(needsDaySeparator(ts(localEpochSec(2026, 9, 1, 6)), 0L))
    }

    @Test
    fun `labels use Today and Yesterday vocabulary`() {
        val now = ts(localEpochSec(2026, 9, 1, 15))
        assertEquals("Today", dayLabel(ts(localEpochSec(2026, 9, 1, 6)), now))
        assertEquals("Yesterday", dayLabel(ts(localEpochSec(2026, 8, 31, 20)), now))
        val older = dayLabel(ts(localEpochSec(2026, 8, 15, 12)), now)
        assertTrue(older.isNotBlank() && older != "Today" && older != "Yesterday")
    }
}
