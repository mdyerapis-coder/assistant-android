package com.mdyerapis.sable.feature.chat

import com.mdyerapis.sable.core.database.chat.ConversationSummary

/** Live client-side filter over loaded session summaries: title or preview, case-insensitive. */
fun filterSessions(
    sessions: List<ConversationSummary>,
    query: String,
): List<ConversationSummary> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return sessions
    return sessions.filter { session ->
        session.title.contains(trimmed, ignoreCase = true) ||
            session.preview.contains(trimmed, ignoreCase = true)
    }
}
