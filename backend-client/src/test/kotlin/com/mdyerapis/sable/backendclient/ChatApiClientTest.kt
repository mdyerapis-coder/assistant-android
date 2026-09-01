package com.mdyerapis.sable.backendclient

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatApiClientTest {
    @Test
    fun chatRequestSerializesSelectedModel() {
        val encoded = Json.encodeToString(
            ChatApiClient.ChatRequest(
                conversation_id = "conversation-1",
                message = "Hello",
                model = "groq",
            )
        )

        assertEquals(
            "{\"conversation_id\":\"conversation-1\",\"message\":\"Hello\",\"model\":\"groq\"}",
            encoded,
        )
    }
}
