package com.mdyerapis.sable.backendclient

import com.mdyerapis.sable.core.model.ChatEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteChatTransportTest {
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun capabilitiesAreCloud() {
        val transport = RemoteChatTransport(ChatApiClient(OkHttpClient(), "http://localhost"))
        assertEquals(TransportCapabilities.CLOUD, transport.capabilities)
        assertTrue(transport.capabilities.tools)
        assertTrue(transport.capabilities.google)
    }

    @Test
    fun streamMapsSseFramesToChatEvents() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"type\":\"delta\",\"conversation_id\":\"c1\",\"content\":\"Hi\"}\n\n" +
                        "data: {\"type\":\"message_completed\",\"conversation_id\":\"c1\",\"message_id\":\"m1\"}\n\n",
                ),
        )
        val client = ChatApiClient(OkHttpClient(), server.url("/").toString().trimEnd('/'))
        val events = RemoteChatTransport(client)
            .stream(ChatTurnRequest(message = "hello", conversationId = "c1"))
            .toList()
        assertEquals("Hi", (events[0] as ChatEvent.Delta).content)
        assertEquals("m1", (events[1] as ChatEvent.MessageCompleted).messageId)
        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.endsWith("/v1/chat"))
        assertTrue(recorded.body.readUtf8().contains("\"message\":\"hello\""))
    }

    @Test
    fun httpErrorEmitsRetryableChatEvent() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("down"))
        val client = ChatApiClient(OkHttpClient(), server.url("/").toString().trimEnd('/'))
        val events = RemoteChatTransport(client)
            .stream(ChatTurnRequest(message = "hello"))
            .toList()
        val error = events.single() as ChatEvent.Error
        assertTrue(error.message.contains("503"))
        assertTrue(error.retryable)
    }
}
