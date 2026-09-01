package com.mdyerapis.sable.backendclient

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
        /** IANA zone id; backend falls back to UTC when absent/invalid. */
        val timezone: String? = null,
    )

    @Serializable
    data class ModelOption(
        val id: String,
        val model: String,
        val provider: String,
        val description: String,
        /** Curated label from the backend; blank on older servers — derive client-side. */
        val display_name: String = "",
        /** Backend-owned filter categories; empty on older servers — use heuristic. */
        val tags: List<String> = emptyList(),
    )

    @Serializable
    data class ModelsResponse(
        val default_model_id: String? = null,
        val models: List<ModelOption>,
    )

    @Serializable
    data class ProviderStatus(
        val name: String,
        val default_model: String,
        val note: String,
        val configured: Boolean,
        val selectable: Boolean,
    )

    @Serializable
    data class ProvidersResponse(
        val providers: List<ProviderStatus>,
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
        timezone: String? = java.util.TimeZone.getDefault().id,
    ): okhttp3.Response {
        val body = Json.encodeToString(
            ChatRequest(
                conversation_id = conversationId,
                message = message,
                model = model,
                timezone = timezone,
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

    /** Server-side provider registry with per-provider configuration status. */
    fun listProviders(): ProvidersResponse {
        val request = Request.Builder()
            .url("$baseUrl/v1/providers")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Provider status failed with HTTP ${response.code}")
            }
            val payload = response.body?.string()
                ?: throw IOException("Provider status response was empty")
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
