package com.mdyerapis.assistant.backendclient

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Phone-side half of the SMS relay (backend phase 06 / Android phase 10).
 * The backend pushes `send_sms`/`read_sms` FCM data actions; the phone
 * executes and reports the outcome here. Shapes must match
 * docs/CONTRACT.md "SMS relay (phase 06)" exactly.
 *
 * Methods are open so unit tests can stub them without a network.
 */
open class SmsRelayApi(private val client: OkHttpClient, private val baseUrl: String) {

    @Serializable
    data class SmsResultMessage(
        val from_number: String,
        val message: String,
        val received_at: String,
    )

    @Serializable
    data class SmsResultRequest(
        val request_id: String,
        val ok: Boolean,
        val error: String? = null,
        val messages: List<SmsResultMessage>? = null,
    )

    /**
     * POST /v1/sms/results — report a relayed SMS outcome to the backend.
     * Returns true on a 2xx response.
     */
    open fun reportResult(request: SmsResultRequest): Boolean {
        val body = Json.encodeToString(
            SmsResultRequest.serializer(),
            request,
        )
        val httpRequest = Request.Builder()
            .url("$baseUrl/v1/sms/results")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("SMS result report failed with HTTP ${response.code}")
            }
            true
        }
    }

    companion object {
        private val Json = Json { ignoreUnknownKeys = true }
    }
}
