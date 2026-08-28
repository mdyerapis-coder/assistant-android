package com.mdyerapis.assistant.backendclient

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ChatApiClient(private val client: OkHttpClient, private val baseUrl: String) {

    @Serializable
    data class ChatRequest(
        val conversation_id: String? = null,
        val message: String,
    )

    /**
     * Sends a chat message and returns the raw SSE response body.
     * The caller reads lines from the response body and feeds them to
     * [SseFrameCodec.parse].
     */
    fun streamChat(message: String, conversationId: String? = null): okhttp3.Response {
        val body = Json.encodeToString(ChatRequest(conversation_id = conversationId, message = message))
        val request = Request.Builder()
            .url("$baseUrl/v1/chat")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return client.newCall(request).execute()
    }

    /**
     * Calls GET /v1/health to verify the bearer token is valid.
     * Returns true if the response is 200.
     */
    fun checkHealth(): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/v1/health")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                android.util.Log.i("ChatApiClient", "Health check: URL=$baseUrl/v1/health code=${response.code}")
                response.isSuccessful
            }
        } catch (e: Exception) {
            android.util.Log.w("ChatApiClient", "Health check failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }
}
