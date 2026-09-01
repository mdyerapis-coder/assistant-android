package com.mdyerapis.sable.feature.chat

import com.mdyerapis.sable.backendclient.ChatApiClient

private val FAST_PROVIDERS = setOf("groq", "minimax")
private val REASONING_PROVIDERS = setOf("gemini", "deepseek", "mistral")
private val UNCENSORED_PROVIDERS = setOf("hermes-3-405b", "dolphin-uncensored", "euryale-70b")

/**
 * Category filter for the settings model list. Backend-owned tags win when present;
 * otherwise fall back to provider membership (never qualified, unlike id) plus a
 * description keyword check.
 */
fun matchesModelCategory(model: ChatApiClient.ModelOption, category: String): Boolean {
    if (category == "ALL") return true
    if (model.tags.isNotEmpty()) {
        return model.tags.any { it.equals(category, ignoreCase = true) }
    }
    return when (category) {
        "FAST" -> model.provider in FAST_PROVIDERS || model.description.contains("fast", ignoreCase = true)
        "REASONING" -> model.provider in REASONING_PROVIDERS || model.description.contains("reasoning", ignoreCase = true)
        "UNCENSORED" -> model.provider in UNCENSORED_PROVIDERS || model.description.contains("uncensored", ignoreCase = true)
        else -> true
    }
}

/** Label for a catalog entry: backend-curated name when present, else client derivation. */
fun displayNameFor(model: ChatApiClient.ModelOption): String =
    model.display_name.ifBlank { formatModelDisplayName(model.id, model.model) }

/** Free-text search over display name, raw model id, and provider. Blank query matches all. */
fun matchesModelQuery(model: ChatApiClient.ModelOption, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return displayNameFor(model).contains(trimmed, ignoreCase = true) ||
        model.model.contains(trimmed, ignoreCase = true) ||
        model.provider.contains(trimmed, ignoreCase = true)
}

/**
 * Display label for a catalog entry. Qualified ids ("provider:model") derive from the raw
 * model string so each live variant gets a distinct label; legacy single-model ids map to
 * the provider's current default (keep in sync with backend app/providers.py).
 */
fun formatModelDisplayName(modelId: String, rawModel: String): String {
    if (modelId.contains(":")) {
        return prettifyModelName(rawModel)
    }
    return when {
        modelId == "hermes-3-405b" || rawModel.contains("hermes-3", ignoreCase = true) -> "Hermes 3 (405B)"
        modelId == "dolphin-uncensored" || rawModel.contains("dolphin", ignoreCase = true) -> "Dolphin 2.9 (Venice)"
        modelId == "euryale-70b" || rawModel.contains("euryale", ignoreCase = true) -> "L3.3 Euryale (70B)"
        modelId == "groq" -> "GPT-OSS 120B (Groq)"
        modelId == "openrouter" -> "Claude Sonnet 4.6"
        modelId == "gemini" -> "Gemini 3.1 Pro"
        modelId == "deepseek" -> "DeepSeek V4 Pro"
        modelId == "mistral" -> "Mistral Large 3"
        modelId == "minimax" -> "MiniMax M3"
        modelId == "mimo" -> "MiMo Lite"
        modelId == "ollama" -> "Llama 3.2 3B (Ollama)"
        else -> prettifyModelName(rawModel)
    }
}

private fun prettifyModelName(rawModel: String): String {
    val namePart = if (rawModel.contains("/")) rawModel.substringAfterLast("/") else rawModel
    return namePart.replace("-", " ").replace("_", " ")
        .split(" ")
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}
