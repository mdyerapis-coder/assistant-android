package com.mdyerapis.sable.feature.localmodel

import android.content.ContextWrapper
import com.mdyerapis.sable.backendclient.ChatApiClient
import com.mdyerapis.sable.backendclient.ChatTurnRequest
import com.mdyerapis.sable.backendclient.RemoteChatTransport
import com.mdyerapis.sable.core.model.ChatEvent
import com.mdyerapis.sable.core.security.BearerTokenRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class LocalGoogleGatewayTest {
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
    fun unrelatedReturnsNull() {
        val gateway = DefaultLocalGoogleGateway(FakeTokens(token = "t"))
        assertNull(gateway.handle(ChatTurnRequest(message = "hello", conversationId = "local:1")))
        assertNull(gateway.handle(ChatTurnRequest(message = "Remind me to stretch in 30 minutes")))
    }

    @Test
    fun noTokenIsHonestFailureNotFakeEvents() = runTest {
        val gateway = DefaultLocalGoogleGateway(FakeTokens(token = null))
        gateway.configureRelay(null, "https://assistant.llmclouds.au")
        val events = gateway.handle(
            ChatTurnRequest(message = "what's on my calendar?", conversationId = "local:1"),
        )!!.toList()
        assertTrue(events.any { it is ChatEvent.ToolCallStarted && it.name == "calendar" })
        assertTrue(events.any { it is ChatEvent.ToolCallFinished && !it.ok })
        val spoken = events.filterIsInstance<ChatEvent.Delta>().single().content
        assertTrue(spoken.contains("O1 relay"))
        assertTrue(spoken.contains("client_secret"))
        assertTrue(spoken.contains("did not invent"))
        assertTrue(events.none { it is ChatEvent.Error && it.message.contains("not installed") })
        assertEquals("local:1", events.filterIsInstance<ChatEvent.MessageCompleted>().single().conversationId)
    }

    @Test
    fun streamsRelayChatWithoutLocalConversationIdOrModel() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"type\":\"tool_call_started\",\"conversation_id\":\"relay-c\",\"id\":\"t1\",\"name\":\"list_events\",\"args_json\":\"{}\"}\n\n" +
                        "data: {\"type\":\"delta\",\"conversation_id\":\"relay-c\",\"content\":\"Lunch at 1\"}\n\n" +
                        "data: {\"type\":\"message_completed\",\"conversation_id\":\"relay-c\",\"message_id\":\"m1\"}\n\n",
                ),
        )
        val tokens = FakeTokens(token = "bearer-xyz")
        val gateway = DefaultLocalGoogleGateway(tokens)
        val relayUrl = server.url("/").toString().trimEnd('/')
        val http = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        gateway.configureRelay(RemoteChatTransport(ChatApiClient(http, relayUrl)), relayUrl)

        val events = gateway.handle(
            ChatTurnRequest(
                message = "what's on my calendar?",
                conversationId = "local:abc",
                model = "gemma-3n-E2B-it",
            ),
        )!!.toList()

        assertEquals("Lunch at 1", (events.filterIsInstance<ChatEvent.Delta>().single()).content)
        assertTrue(events.any { it is ChatEvent.ToolCallStarted && it.name == "list_events" })
        assertTrue(events.all { it.conversationId == "local:abc" })

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.endsWith("/v1/chat"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("what's on my calendar?"))
        assertTrue(!body.contains("local:abc"))
        assertTrue(!body.contains("gemma-3n-E2B-it"))
    }

    @Test
    fun unreachableRelayIsHonest() = runTest {
        val tokens = FakeTokens(token = "t")
        val gateway = DefaultLocalGoogleGateway(tokens)
        val http = OkHttpClient.Builder()
            .connectTimeout(150, TimeUnit.MILLISECONDS)
            .readTimeout(150, TimeUnit.MILLISECONDS)
            .callTimeout(300, TimeUnit.MILLISECONDS)
            .build()
        gateway.configureRelay(
            RemoteChatTransport(ChatApiClient(http, "http://127.0.0.1:1")),
            "http://127.0.0.1:1",
        )
        val events = gateway.handle(
            ChatTurnRequest(message = "check my email", conversationId = "local:1"),
        )!!.toList()
        val error = events.filterIsInstance<ChatEvent.Error>().single()
        assertTrue(error.message.contains("OAuth relay") || error.message.contains("127.0.0.1:1"))
        assertTrue(!error.message.contains("not installed"))
        assertEquals("local:1", error.conversationId)
    }

    private class FakeTokens(private val token: String?) : BearerTokenRepository(ContextWrapper(null)) {
        override fun getToken(): String? = token
    }
}
