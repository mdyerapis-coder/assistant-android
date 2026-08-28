package com.mdyerapis.assistant.backendclient

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ChatApiClient(private val client: OkHttpClient, private val baseUrl: String) {

    @Serializable
    data class ChatRequest(
        val conversation_id: String? = null,
        val message: String,
        val model: String? = null,
    )

    @Serializable
    data class ModelOption(
        val id: String,
        val model: String,
        val provider: String,
        val description: String,
    )

    @Serializable
    data class ModelsResponse(
        val default_model_id: String? = null,
        val models: List<ModelOption>,
    )

    /**
     * Sends a chat message and returns the raw SSE response body.
     * The caller reads lines from the response body and feeds them to
     * [SseFrameCodec.parse].
     */
    fun streamChat(
        message: String,
        conversationId: String? = null,
        model: String? = null,
    ): okhttp3.Response {
        val body = Json.encodeToString(
            ChatRequest(
                conversation_id = conversationId,
                message = message,
                model = model,
            )
        )
        val request = Request.Builder()
            .url("$baseUrl/v1/chat")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return client.newCall(request).execute()
    }

    fun listModels(): ModelsResponse {
        val request = Request.Builder()
            .url("$baseUrl/v1/models")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Model catalog failed with HTTP ${response.code}")
            }
            val payload = response.body?.string()
                ?: throw IOException("Model catalog response was empty")
            Json.decodeFromString(payload)
        }
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
