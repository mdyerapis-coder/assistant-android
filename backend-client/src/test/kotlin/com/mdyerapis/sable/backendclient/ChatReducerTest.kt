package com.mdyerapis.sable.backendclient

import com.mdyerapis.sable.core.model.ChatEvent
import com.mdyerapis.sable.core.model.ChatState
import com.mdyerapis.sable.core.model.ToolCallStatus
import org.junit.Assert.*
import org.junit.Test

class ChatReducerTest {

    // --- delta before message started (synthesizes message) ---

    @Test
    fun `delta before any marker synthesizes message`() {
        val state = ChatState()
        val next = ChatReducer.reduce(state, ChatEvent.Delta(conversationId = "c1", content = "hello"))
        assertEquals("synthesized", next.currentMessageId)
        assertEquals("hello", next.currentContent)
        assertEquals("c1", next.conversationId)
    }

    @Test
    fun `multiple deltas accumulate content`() {
        var state = ChatState()
        state = ChatReducer.reduce(state, ChatEvent.Delta(content = "hel"))
        state = ChatReducer.reduce(state, ChatEvent.Delta(content = "lo"))
        assertEquals("hello", state.currentContent)
    }

    // --- message_completed ---

    @Test
    fun `messageCompleted appends message and clears current`() {
        val state = ChatState(
            conversationId = "c1",
            currentMessageId = "m1",
            currentContent = "hi there",
        )
        val next = ChatReducer.reduce(state, ChatEvent.MessageCompleted(conversationId = "c1", messageId = "m1"))
        assertEquals(1, next.messages.size)
        assertEquals("hi there", next.messages[0].content)
        assertEquals("assistant", next.messages[0].role)
        assertNull(next.currentMessageId)
        assertEquals("", next.currentContent)
        assertFalse(next.isLoading)
    }

    @Test
    fun `messageCompleted with synthesized message`() {
        var state = ChatState()
        state = ChatReducer.reduce(state, ChatEvent.Delta(content = "text"))
        state = ChatReducer.reduce(state, ChatEvent.MessageCompleted())
        assertEquals(1, state.messages.size)
        assertEquals("text", state.messages[0].content)
    }

    // --- tool calls ---

    @Test
    fun `toolCallStarted adds tool call`() {
        val state = ChatState()
        val next = ChatReducer.reduce(state, ChatEvent.ToolCallStarted(id = "tc1", name = "remember"))
        assertEquals(1, next.activeToolCalls.size)
        assertEquals("tc1", next.activeToolCalls[0].id)
        assertEquals("remember", next.activeToolCalls[0].name)
        assertEquals(ToolCallStatus.Started, next.activeToolCalls[0].status)
    }

    @Test
    fun `toolCallProgress updates status`() {
        var state = ChatState()
        state = ChatReducer.reduce(state, ChatEvent.ToolCallStarted(id = "tc1", name = "x"))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallProgress(id = "tc1"))
        assertEquals(ToolCallStatus.Progress, state.activeToolCalls[0].status)
    }

    @Test
    fun `toolCallFinished ok=true sets Finished status`() {
        var state = ChatState()
        state = ChatReducer.reduce(state, ChatEvent.ToolCallStarted(id = "tc1", name = "x"))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallFinished(id = "tc1", ok = true))
        assertEquals(ToolCallStatus.Finished, state.activeToolCalls[0].status)
    }

    @Test
    fun `toolCallFinished ok=false sets Error status`() {
        var state = ChatState()
        state = ChatReducer.reduce(state, ChatEvent.ToolCallStarted(id = "tc1", name = "x"))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallFinished(id = "tc1", ok = false))
        assertEquals(ToolCallStatus.Error, state.activeToolCalls[0].status)
    }

    @Test
    fun `parallel tool calls tracked independently`() {
        var state = ChatState()
        state = ChatReducer.reduce(state, ChatEvent.ToolCallStarted(id = "tc1", name = "a"))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallStarted(id = "tc2", name = "b"))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallFinished(id = "tc1", ok = true))
        assertEquals(ToolCallStatus.Finished, state.activeToolCalls[0].status)
        assertEquals(ToolCallStatus.Started, state.activeToolCalls[1].status)
    }

    // --- error ---

    @Test
    fun `error sets error and clears loading`() {
        val state = ChatState(isLoading = true)
        val next = ChatReducer.reduce(state, ChatEvent.Error(message = "fail"))
        assertEquals("fail", next.error)
        assertFalse(next.isLoading)
    }

    // --- conversation_id propagation ---

    @Test
    fun `conversationId propagates from events to state`() {
        val state = ChatState()
        val next = ChatReducer.reduce(state, ChatEvent.Delta(conversationId = "c1", content = "x"))
        assertEquals("c1", next.conversationId)
    }

    // --- interleaved delta/tool-call sequences (per CONTRACT.md ordering guarantees) ---

    @Test
    fun `delta before tool_call_started accumulates content`() {
        var state = ChatState()
        state = ChatReducer.reduce(state, ChatEvent.Delta(content = "Let me "))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallStarted(id = "tc1", name = "remember"))
        assertEquals("Let me ", state.currentContent)
        assertEquals(1, state.activeToolCalls.size)
        assertEquals("tc1", state.activeToolCalls[0].id)
    }

    @Test
    fun `delta between tool_call_started and tool_call_finished`() {
        var state = ChatState()
        state = ChatReducer.reduce(state, ChatEvent.ToolCallStarted(id = "tc1", name = "remember"))
        state = ChatReducer.reduce(state, ChatEvent.Delta(content = "working..."))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallFinished(id = "tc1", ok = true))
        assertEquals("working...", state.currentContent)
        assertEquals(ToolCallStatus.Finished, state.activeToolCalls[0].status)
    }

    @Test
    fun `delta after tool_call_finished appends to content`() {
        var state = ChatState()
        state = ChatReducer.reduce(state, ChatEvent.ToolCallStarted(id = "tc1", name = "x"))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallFinished(id = "tc1", ok = true))
        state = ChatReducer.reduce(state, ChatEvent.Delta(content = "Done!"))
        assertEquals("Done!", state.currentContent)
        assertEquals(ToolCallStatus.Finished, state.activeToolCalls[0].status)
    }

    @Test
    fun `full lifecycle delta-tool-delta-tool-delta-completed`() {
        var state = ChatState()
        // Text before first tool
        state = ChatReducer.reduce(state, ChatEvent.Delta(content = "I'll "))
        // First tool call
        state = ChatReducer.reduce(state, ChatEvent.ToolCallStarted(id = "tc1", name = "remember"))
        state = ChatReducer.reduce(state, ChatEvent.Delta(content = "remember that."))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallFinished(id = "tc1", ok = true))
        // Second tool call
        state = ChatReducer.reduce(state, ChatEvent.ToolCallStarted(id = "tc2", name = "create_reminder"))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallFinished(id = "tc2", ok = true, summary = "Reminder created"))
        // Text after tools + completion
        state = ChatReducer.reduce(state, ChatEvent.Delta(content = " All done!"))
        state = ChatReducer.reduce(state, ChatEvent.MessageCompleted(messageId = "m1"))
        assertEquals("I'll remember that. All done!", state.messages[0].content)
        assertEquals(2, state.activeToolCalls.size)
        assertEquals(ToolCallStatus.Finished, state.activeToolCalls[0].status)
        assertEquals(ToolCallStatus.Finished, state.activeToolCalls[1].status)
        assertNull(state.currentMessageId)
        assertEquals("", state.currentContent)
    }

    @Test
    fun `parallel tool calls with interleaved deltas`() {
        var state = ChatState()
        state = ChatReducer.reduce(state, ChatEvent.ToolCallStarted(id = "tc1", name = "a"))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallStarted(id = "tc2", name = "b"))
        state = ChatReducer.reduce(state, ChatEvent.Delta(content = "both running"))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallFinished(id = "tc1", ok = true))
        state = ChatReducer.reduce(state, ChatEvent.Delta(content = " done"))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallFinished(id = "tc2", ok = true))
        assertEquals("both running done", state.currentContent)
        assertEquals(ToolCallStatus.Finished, state.activeToolCalls[0].status)
        assertEquals(ToolCallStatus.Finished, state.activeToolCalls[1].status)
    }

    @Test
    fun `tool_call_progress between started and finished preserves content`() {
        var state = ChatState()
        state = ChatReducer.reduce(state, ChatEvent.Delta(content = "before"))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallStarted(id = "tc1", name = "x"))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallProgress(id = "tc1", note = "running"))
        state = ChatReducer.reduce(state, ChatEvent.ToolCallFinished(id = "tc1", ok = true))
        state = ChatReducer.reduce(state, ChatEvent.Delta(content = "after"))
        assertEquals("beforeafter", state.currentContent)
        assertEquals(ToolCallStatus.Finished, state.activeToolCalls[0].status)
    }

    @Test
    fun `error mid-stream stops content accumulation`() {
        var state = ChatState()
        state = ChatReducer.reduce(state, ChatEvent.Delta(content = "partial"))
        state = ChatReducer.reduce(state, ChatEvent.Error(message = "timeout"))
        assertEquals("partial", state.currentContent) // content preserved
        assertEquals("timeout", state.error)
        assertFalse(state.isLoading)
    }

    // --- unknown ---

    @Test
    fun `unknown event returns state unchanged`() {
        val state = ChatState(conversationId = "c1", currentContent = "x")
        val next = ChatReducer.reduce(state, ChatEvent.Unknown())
        assertEquals(state, next)
    }
}
