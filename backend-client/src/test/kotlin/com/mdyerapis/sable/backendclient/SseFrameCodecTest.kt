package com.mdyerapis.sable.backendclient

import com.mdyerapis.sable.core.model.ChatEvent
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class SseFrameCodecTest {

    // --- chat request shape ---

    @Test
    fun `chat request carries the device timezone`() {
        val body = Json.encodeToString(
            ChatApiClient.ChatRequest(
                message = "what time is it",
                timezone = "Australia/Brisbane",
            )
        )
        assertTrue(body.contains(""""timezone":"Australia/Brisbane""""))
    }

    @Test
    fun `providers response parses configured flags`() {
        val json = """{"providers":[
            {"name":"ollama","default_model":"llama3.2:3b","note":"local","configured":true,"selectable":true},
        {"name":"groq","default_model":"gpt-oss","note":"fast","configured":false,"selectable":true}
        ]}""".trimIndent().replace("\n", "")
        val parsed = Json.decodeFromString<ChatApiClient.ProvidersResponse>(json)
        assertEquals(2, parsed.providers.size)
        assertTrue(parsed.providers[0].configured)
        assertTrue(!parsed.providers[1].configured)
    }

    // --- delta ---

    @Test
    fun `delta parses content and conversation_id`() {
        val line = """data: {"type":"delta","conversation_id":"conv1","content":"hello"}"""
        val event = SseFrameCodec.parse(line)
        assertTrue(event is ChatEvent.Delta)
        val d = event as ChatEvent.Delta
        assertEquals("conv1", d.conversationId)
        assertEquals("hello", d.content)
    }

    @Test
    fun `delta with empty content`() {
        val line = """data: {"type":"delta","conversation_id":"c","content":""}"""
        val event = SseFrameCodec.parse(line)
        assertTrue(event is ChatEvent.Delta)
        assertEquals("", (event as ChatEvent.Delta).content)
    }

    // --- tool_call_started ---

    @Test
    fun `tool_call_started parses all fields`() {
        val line = """data: {"type":"tool_call_started","conversation_id":"c1","id":"tc1","name":"remember","args_json":"{\"fact\":\"x\"}"}"""
        val event = SseFrameCodec.parse(line)
        assertTrue(event is ChatEvent.ToolCallStarted)
        val tc = event as ChatEvent.ToolCallStarted
        assertEquals("tc1", tc.id)
        assertEquals("remember", tc.name)
        assertEquals("{\"fact\":\"x\"}", tc.argsJson)
    }

    // --- tool_call_progress ---

    @Test
    fun `tool_call_progress parses id and note`() {
        val line = """data: {"type":"tool_call_progress","conversation_id":"c","id":"tc1","note":"running..."}"""
        val event = SseFrameCodec.parse(line)
        assertTrue(event is ChatEvent.ToolCallProgress)
        assertEquals("tc1", (event as ChatEvent.ToolCallProgress).id)
        assertEquals("running...", event.note)
    }

    // --- tool_call_finished ---

    @Test
    fun `tool_call_finished ok=true`() {
        val line = """data: {"type":"tool_call_finished","conversation_id":"c","id":"tc1","ok":"true","summary":"done"}"""
        val event = SseFrameCodec.parse(line)
        assertTrue(event is ChatEvent.ToolCallFinished)
        val tf = event as ChatEvent.ToolCallFinished
        assertEquals("tc1", tf.id)
        assertTrue(tf.ok)
        assertEquals("done", tf.summary)
    }

    @Test
    fun `tool_call_finished ok=false`() {
        val line = """data: {"type":"tool_call_finished","conversation_id":"c","id":"tc1","ok":"false"}"""
        val event = SseFrameCodec.parse(line)
        assertTrue(event is ChatEvent.ToolCallFinished)
        assertFalse((event as ChatEvent.ToolCallFinished).ok)
    }

    // --- message_completed ---

    @Test
    fun `message_completed parses message_id`() {
        val line = """data: {"type":"message_completed","conversation_id":"c","message_id":"msg1"}"""
        val event = SseFrameCodec.parse(line)
        assertTrue(event is ChatEvent.MessageCompleted)
        assertEquals("msg1", (event as ChatEvent.MessageCompleted).messageId)
    }

    // --- error ---

    @Test
    fun `error parses message and retryable`() {
        val line = """data: {"type":"error","conversation_id":"c","message":"oops","retryable":"true"}"""
        val event = SseFrameCodec.parse(line)
        assertTrue(event is ChatEvent.Error)
        val err = event as ChatEvent.Error
        assertEquals("oops", err.message)
        assertTrue(err.retryable)
    }

    // --- DONE ---

    @Test
    fun `DONE returns MessageCompleted`() {
        val event = SseFrameCodec.parse("[DONE]")
        assertTrue(event is ChatEvent.MessageCompleted)
    }

    // --- unknown / edge cases ---

    @Test
    fun `unknown type returns Unknown`() {
        val line = """data: {"type":"future_event","conversation_id":"c"}"""
        assertTrue(SseFrameCodec.parse(line) is ChatEvent.Unknown)
    }

    @Test
    fun `non-data line returns Unknown`() {
        assertTrue(SseFrameCodec.parse("event: ping") is ChatEvent.Unknown)
    }

    @Test
    fun `empty data returns Unknown`() {
        assertTrue(SseFrameCodec.parse("data:") is ChatEvent.Unknown)
    }

    @Test
    fun `malformed JSON returns Unknown`() {
        assertTrue(SseFrameCodec.parse("data: {broken") is ChatEvent.Unknown)
    }

    @Test
    fun `missing conversation_id defaults to empty string`() {
        val line = """data: {"type":"delta","content":"hi"}"""
        val event = SseFrameCodec.parse(line)
        assertEquals("", (event as ChatEvent.Delta).conversationId)
    }
    @Test
    fun `events reads SSE body off the caller thread`() = runBlocking {
        val callerThread = Thread.currentThread()
        var bodyThread: Thread? = null
        val body = object : ResponseBody() {
            override fun contentType() = "text/event-stream".toMediaType()
            override fun contentLength() = -1L
            override fun source() = Buffer().apply {
                bodyThread = Thread.currentThread()
                writeUtf8(
                    """data: {"type":"delta","conversation_id":"c","content":"hi"}

[DONE]
"""
                )
            }
        }
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.test/chat").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()

        val events = SseFrameCodec.events(response).toList()

        assertNotEquals(callerThread, bodyThread)
        assertEquals(2, events.size)
        assertTrue(events.first() is ChatEvent.Delta)
        assertTrue(events.last() is ChatEvent.MessageCompleted)
    }
}
