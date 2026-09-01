package com.mdyerapis.sable.backendclient

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Registers FCM device tokens with the backend so reminder push
 * notifications can be delivered. See docs/CONTRACT.md and
 * app/routers/device_tokens.py in the backend.
 */
class DeviceTokenApi(private val client: OkHttpClient, private val baseUrl: String) {

    @Serializable
    data class DeviceTokenRequest(
        val token: String,
        val device_id: String? = null,
    )

    @Serializable
    data class DeviceTokenResponse(
        val ok: Boolean = false,
        val message: String = "",
    )

    /**
     * Registers an FCM device token with the backend.
     * Returns true if the registration succeeded.
     */
    fun registerToken(fcmToken: String, deviceId: String? = null): Boolean {
        val body = Json.encodeToString(
            DeviceTokenRequest(token = fcmToken, device_id = deviceId)
        )
        val request = Request.Builder()
            .url("$baseUrl/v1/device-tokens")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
}
