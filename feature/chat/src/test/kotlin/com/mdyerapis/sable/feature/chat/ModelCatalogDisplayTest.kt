package com.mdyerapis.sable.feature.chat

import com.mdyerapis.sable.backendclient.ChatApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogDisplayTest {

    private fun option(
        id: String,
        model: String = id,
        provider: String = id.substringBefore(":"),
        description: String = "",
    ) = ChatApiClient.ModelOption(id = id, model = model, provider = provider, description = description)

    @Test
    fun `reasoning category matches qualified live ids via provider`() {
        // Live catalogs qualify ids as "provider:model" — the category must
        // still match (regression: matching on id dropped these).
        val live = option(id = "deepseek:deepseek-v4-pro", model = "deepseek-v4-pro", provider = "deepseek")
        assertTrue(matchesModelCategory(live, "REASONING"))
    }

    @Test
    fun `reasoning category matches legacy unqualified id`() {
        val legacy = option(id = "mistral", model = "mistral-large-3", provider = "mistral")
        assertTrue(matchesModelCategory(legacy, "REASONING"))
    }

    @Test
    fun `fast category matches qualified groq id`() {
        val live = option(id = "groq:openai/gpt-oss-120b", model = "openai/gpt-oss-120b", provider = "groq")
        assertTrue(matchesModelCategory(live, "FAST"))
    }

    @Test
    fun `uncensored category matches via description tag`() {
        val hermes = option(
            id = "hermes-3-405b",
            model = "nousresearch/hermes-3-llama-3.1-405b",
            provider = "hermes-3-405b",
            description = "[Uncensored] Nous Research steerable frontier intelligence.",
        )
        assertTrue(matchesModelCategory(hermes, "UNCENSORED"))
    }

    @Test
    fun `category does not match unrelated provider`() {
        val ollama = option(id = "ollama:llama3.2:3b", model = "llama3.2:3b", provider = "ollama")
        assertFalse(matchesModelCategory(ollama, "REASONING"))
        assertFalse(matchesModelCategory(ollama, "FAST"))
    }

    @Test
    fun `all category matches everything`() {
        val any = option(id = "mimo:mimo-lite", model = "mimo-lite", provider = "mimo")
        assertTrue(matchesModelCategory(any, "ALL"))
    }

    @Test
    fun `backend tags win over provider heuristic`() {
        // Provider not in any client-side set, but backend tags it fast.
        val tagged = option(
            id = "newprov:newprov-x1",
            model = "newprov/x1",
            provider = "newprov",
        ).copy(tags = listOf("fast"))
        assertTrue(matchesModelCategory(tagged, "FAST"))
        assertFalse(matchesModelCategory(tagged, "REASONING"))
    }

    @Test
    fun `backend tags suppress misleading description fallback`() {
        // Description mentions reasoning but backend only tags fast — tags win.
        val tagged = option(
            id = "groq:openai/gpt-oss-120b",
            model = "openai/gpt-oss-120b",
            provider = "groq",
            description = "fast, some reasoning ability",
        ).copy(tags = listOf("fast"))
        assertFalse(matchesModelCategory(tagged, "REASONING"))
    }

    @Test
    fun `displayNameFor prefers backend display name`() {
        val named = option(id = "gemini", model = "gemini-3.1-pro", provider = "gemini")
            .copy(display_name = "Gemini 3.1 Pro Max")
        assertEquals("Gemini 3.1 Pro Max", displayNameFor(named))
    }

    @Test
    fun `displayNameFor falls back to client derivation when blank`() {
        val unnamed = option(
            id = "openrouter:anthropic/claude-sonnet-4-6",
            model = "anthropic/claude-sonnet-4-6",
            provider = "openrouter",
        )
        assertEquals("Claude Sonnet 4 6", displayNameFor(unnamed))
    }

    @Test
    fun `query matches raw model id case-insensitively`() {
        val m = option(id = "deepseek:deepseek-v4-pro", model = "deepseek-v4-pro", provider = "deepseek")
        assertTrue(matchesModelQuery(m, "V4-PRO"))
    }

    @Test
    fun `query matches backend display name`() {
        val m = option(id = "gemini", model = "gemini-3.1-pro", provider = "gemini")
            .copy(display_name = "Gemini 3.1 Pro")
        assertTrue(matchesModelQuery(m, "3.1 pro"))
    }

    @Test
    fun `query matches provider name`() {
        val m = option(id = "groq:openai/gpt-oss-120b", model = "openai/gpt-oss-120b", provider = "groq")
        assertTrue(matchesModelQuery(m, "Groq"))
    }

    @Test
    fun `blank or mismatching query`() {
        val m = option(id = "mimo", model = "mimo-lite", provider = "mimo")
        assertTrue(matchesModelQuery(m, "  "))
        assertFalse(matchesModelQuery(m, "claude"))
    }

    @Test
    fun `qualified id derives display name from raw model`() {
        assertEquals(
            "Claude Sonnet 4 6",
            formatModelDisplayName("openrouter:anthropic/claude-sonnet-4-6", "anthropic/claude-sonnet-4-6"),
        )
    }

    @Test
    fun `legacy openrouter id shows current default model name`() {
        // Regression: was hardcoded to the stale "Claude 3.5 Sonnet".
        assertEquals("Claude Sonnet 4.6", formatModelDisplayName("openrouter", "anthropic/claude-sonnet-4-6"))
    }

    @Test
    fun `legacy ollama id shows model name not just provider`() {
        assertEquals("Llama 3.2 3B (Ollama)", formatModelDisplayName("ollama", "llama3.2:3b"))
    }

    @Test
    fun `legacy gemini id keeps flagship label`() {
        assertEquals("Gemini 3.1 Pro", formatModelDisplayName("gemini", "gemini-3.1-pro"))
    }
}
