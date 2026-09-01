package com.mdyerapis.sable.backendclient

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Server-synced conversation history (phase 05 backend / phase 08 here).
 * The server is the source of truth; the caller caches responses into
 * Room and never treats local rows as authoritative. See docs/CONTRACT.md
 * Part 2 — shapes here must match it exactly.
 *
 * Methods are open so unit tests can stub them without a network.
 */
open class ThreadsApi(private val client: OkHttpClient, private val baseUrl: String) {

    @Serializable
    data class ThreadSummary(
        val id: String,
        val title: String,
        val preview: String,
        val created_at: String,
        val last_message_at: String,
        val message_count: Int,
    )

    @Serializable
    data class ThreadsResponse(val threads: List<ThreadSummary> = emptyList())

    @Serializable
    data class ThreadMessage(
        val id: Long,
        val role: String,
        val content: String,
        val created_at: String,
    )

    @Serializable
    data class ThreadMessagesResponse(
        val thread_id: String,
        val messages: List<ThreadMessage> = emptyList(),
    )

    /** GET /v1/threads — most-recent-first thread list. */
    open fun listThreads(): ThreadsResponse {
        val request = Request.Builder().url("$baseUrl/v1/threads").get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Thread list failed with HTTP ${response.code}")
            }
            val payload = response.body?.string()
                ?: throw IOException("Thread list response was empty")
            json.decodeFromString(payload)
        }
    }

    /** GET /v1/threads/{id}/messages — renderable history, oldest-first. */
    open fun listMessages(threadId: String): ThreadMessagesResponse {
        val request = Request.Builder()
            .url("$baseUrl/v1/threads/$threadId/messages")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Thread messages failed with HTTP ${response.code}")
            }
            val payload = response.body?.string()
                ?: throw IOException("Thread messages response was empty")
            json.decodeFromString(payload)
        }
    }

    companion object {
        // Contract tolerance rule: unknown fields must be ignored, never
        // rejected — decode failures are only for genuinely malformed
        // responses (missing required fields).
        private val json = Json { ignoreUnknownKeys = true }
    }
}
