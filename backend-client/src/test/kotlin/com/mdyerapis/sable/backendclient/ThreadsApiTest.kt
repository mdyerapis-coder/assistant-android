package com.mdyerapis.sable.backendclient

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadsApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun threadsResponseDecodesContractShape() {
        val payload = """
            {"threads": [{
                "id": "0b6ba6e2-2bd9-4a1e-9a52-93d7cf1e8a11",
                "title": "what's on my calendar today",
                "preview": "You have nothing scheduled.",
                "created_at": "2026-08-31T10:00:00+00:00",
                "last_message_at": "2026-08-31T10:00:02+00:00",
                "message_count": 3
            }]}
        """.trimIndent()
        val decoded = json.decodeFromString<ThreadsApi.ThreadsResponse>(payload)
        assertEquals(1, decoded.threads.size)
        val thread = decoded.threads[0]
        assertEquals("0b6ba6e2-2bd9-4a1e-9a52-93d7cf1e8a11", thread.id)
        assertEquals("what's on my calendar today", thread.title)
        assertEquals("You have nothing scheduled.", thread.preview)
        assertEquals(3, thread.message_count)
    }

    @Test
    fun threadsResponseToleratesUnknownFields() {
        val payload = """
            {"threads": [{"id": "t1", "title": "hi", "preview": "",
                          "created_at": "x", "last_message_at": "y",
                          "message_count": 1, "future_field": {"nested": true}}],
             "future_top_level": 7}
        """.trimIndent()
        val decoded = json.decodeFromString<ThreadsApi.ThreadsResponse>(payload)
        assertEquals("t1", decoded.threads[0].id)
    }

    @Test
    fun threadsResponseDefaultsToEmptyList() {
        val decoded = json.decodeFromString<ThreadsApi.ThreadsResponse>("""{"threads": []}""")
        assertTrue(decoded.threads.isEmpty())
    }

    @Test
    fun threadMessagesResponseDecodesContractShape() {
        val payload = """
            {"thread_id": "t1", "messages": [
                {"id": 12, "role": "user", "content": "hello",
                 "created_at": "2026-08-31T10:00:00+00:00"},
                {"id": 15, "role": "assistant", "content": "Hi there",
                 "created_at": "2026-08-31T10:00:02+00:00"}
            ]}
        """.trimIndent()
        val decoded = json.decodeFromString<ThreadsApi.ThreadMessagesResponse>(payload)
        assertEquals("t1", decoded.thread_id)
        assertEquals(listOf("hello", "Hi there"), decoded.messages.map { it.content })
        assertEquals(listOf(12L, 15L), decoded.messages.map { it.id })
    }

    @Test
    fun threadMessagesResponseToleratesUnknownFields() {
        val payload = """
            {"thread_id": "t1", "messages": [
                {"id": 1, "role": "user", "content": "x",
                 "created_at": "y", "extra": "ignored"}
            ], "another": null}
        """.trimIndent()
        val decoded = json.decodeFromString<ThreadsApi.ThreadMessagesResponse>(payload)
        assertEquals(1, decoded.messages.size)
    }
}
