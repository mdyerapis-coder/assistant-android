package com.mdyerapis.sable.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentModelsTest {

    @Test
    fun `selecting a model prepends it to recents`() {
        assertEquals(listOf("b", "a"), updateRecentModels(listOf("a"), "b"))
    }

    @Test
    fun `reselecting moves it to front without duplicating`() {
        assertEquals(listOf("b", "a"), updateRecentModels(listOf("a", "b"), "b"))
    }

    @Test
    fun `recents are capped at three`() {
        assertEquals(
            listOf("d", "c", "b"),
            updateRecentModels(listOf("c", "b", "a"), "d"),
        )
    }

    @Test
    fun `clearing selection leaves recents untouched`() {
        assertEquals(listOf("a", "b"), updateRecentModels(listOf("a", "b"), null))
    }
}
