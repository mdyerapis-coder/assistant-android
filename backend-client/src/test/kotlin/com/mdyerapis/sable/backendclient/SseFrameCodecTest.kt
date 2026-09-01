package com.mdyerapis.sable.backendclient

import com.mdyerapis.sable.core.model.ChatEvent
import org.junit.Assert.*
import org.junit.Test

class SseFrameCodecTest {

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
}
