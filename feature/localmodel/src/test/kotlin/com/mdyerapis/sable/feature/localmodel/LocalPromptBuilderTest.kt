package com.mdyerapis.sable.feature.localmodel

import com.mdyerapis.sable.core.model.ChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPromptBuilderTest {
    @Test
    fun includesSystemPreambleAndTranscript() {
        val prompt = LocalPromptBuilder.build(
            history = listOf(
                ChatMessage(id = "1", role = "user", content = "hi"),
                ChatMessage(id = "2", role = "assistant", content = "hello"),
                ChatMessage(id = "3", role = "user", content = "what's on my calendar?"),
            ),
            fallbackUserMessage = "ignored",
        )
        assertTrue(prompt.startsWith(LocalPromptBuilder.SYSTEM_PREAMBLE))
        assertTrue(prompt.contains("User: hi"))
        assertTrue(prompt.contains("Assistant: hello"))
        assertTrue(prompt.contains("User: what's on my calendar?"))
        assertTrue(prompt.contains("switch to Cloud Assistant"))
        assertTrue(prompt.endsWith("Assistant:"))
        assertFalse(prompt.contains("ignored"))
    }

    @Test
    fun fallsBackToUserMessageWhenHistoryEmpty() {
        val prompt = LocalPromptBuilder.build(emptyList(), "just this")
        assertTrue(prompt.contains("User: just this"))
    }
}
