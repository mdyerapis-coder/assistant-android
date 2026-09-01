package com.mdyerapis.sable.feature.chat

import com.mdyerapis.sable.core.database.chat.ConversationSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sessions search contract: live filter over the already-loaded summaries,
 * matching title or preview case-insensitively; blank query returns all.
 */
class SessionsFilteringTest {

    private fun summary(id: String, title: String, preview: String) = ConversationSummary(
        id = id, title = title, preview = preview,
        modelId = null, mode = "backend", serverConversationId = null,
        updatedAt = 0L,
    )

    @Test
    fun `blank query keeps every session`() {
        val sessions = listOf(
            summary("a", "Morning brief", "hello"),
            summary("b", "Shopping", "milk eggs"),
        )
        assertEquals(listOf("a", "b"), filterSessions(sessions, "").map { it.id })
        assertEquals(listOf("a", "b"), filterSessions(sessions, "   ").map { it.id })
    }

    @Test
    fun `query matches title case-insensitively`() {
        val sessions = listOf(
            summary("a", "Morning Brief", "hello"),
            summary("b", "Shopping", "milk eggs"),
        )
        assertEquals(listOf("a"), filterSessions(sessions, "morning").map { it.id })
    }

    @Test
    fun `query matches preview too`() {
        val sessions = listOf(
            summary("a", "Morning Brief", "eggs later"),
            summary("b", "Shopping", "milk eggs"),
        )
        assertEquals(listOf("a", "b"), filterSessions(sessions, "egg").map { it.id })
    }

    @Test
    fun `no match yields empty list`() {
        val sessions = listOf(summary("a", "Morning Brief", "hello"))
        assertEquals(emptyList<String>(), filterSessions(sessions, "zzzz").map { it.id })
    }
}
