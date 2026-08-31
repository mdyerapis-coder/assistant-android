package com.mdyerapis.assistant.backendclient

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsRelayApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun resultRequestEncodesContractShape() {
        val request = SmsRelayApi.SmsResultRequest(
            request_id = "abc-123",
            ok = true,
        )
        val encoded = json.encodeToString(SmsRelayApi.SmsResultRequest.serializer(), request)
        // Contract shape: request_id + ok; error/messages default to null/absent.
        assertTrue(encoded.contains("\"request_id\":\"abc-123\""))
        assertTrue(encoded.contains("\"ok\":true"))
    }

    @Test
    fun readResultWithMessagesEncodesContractShape() {
        val request = SmsRelayApi.SmsResultRequest(
            request_id = "req-2",
            ok = true,
            messages = listOf(
                SmsRelayApi.SmsResultMessage(
                    from_number = "+61498765432",
                    message = "See you at 5",
                    received_at = "2026-09-01T01:00:00Z",
                )
            ),
        )
        val encoded = json.encodeToString(SmsRelayApi.SmsResultRequest.serializer(), request)
        assertTrue(encoded.contains("\"from_number\":\"+61498765432\""))
        assertTrue(encoded.contains("\"message\":\"See you at 5\""))
        assertTrue(encoded.contains("\"received_at\":\"2026-09-01T01:00:00Z\""))
    }

    @Test
    fun failureResultDecodesErrorField() {
        val payload = """
            {"request_id": "req-1", "ok": false, "error": "SIM missing"}
        """.trimIndent()
        val decoded = json.decodeFromString<SmsRelayApi.SmsResultRequest>(payload)
        assertEquals("req-1", decoded.request_id)
        assertEquals(false, decoded.ok)
        assertEquals("SIM missing", decoded.error)
        assertNull(decoded.messages)
    }

    @Test
    fun resultRequestToleratesUnknownFields() {
        val payload = """
            {"request_id": "req-1", "ok": true, "future_field": 42}
        """.trimIndent()
        val decoded = json.decodeFromString<SmsRelayApi.SmsResultRequest>(payload)
        assertEquals("req-1", decoded.request_id)
        assertTrue(decoded.ok)
    }
}
