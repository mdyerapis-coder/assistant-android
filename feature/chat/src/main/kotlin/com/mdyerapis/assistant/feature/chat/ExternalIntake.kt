package com.mdyerapis.assistant.feature.chat

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries external intents (share-sheet text, deep links) from
 * MainActivity into the chat UI. The chat screen collects these and
 * consumes them — a shared text fills the composer, a session link opens
 * that conversation.
 */
@Singleton
class ExternalIntake @Inject constructor() {

    private val _events = MutableSharedFlow<IntakeEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<IntakeEvent> = _events.asSharedFlow()

    /** Queue shared text (ACTION_SEND) to prefill the composer. */
    fun offerSharedText(text: String) {
        if (text.isNotBlank()) _events.tryEmit(IntakeEvent.SharedText(text))
    }

    /** Queue a deep link to open a specific local conversation. */
    fun offerOpenConversation(localConversationId: String) {
        if (localConversationId.isNotBlank()) {
            _events.tryEmit(IntakeEvent.OpenConversation(localConversationId))
        }
    }

    sealed interface IntakeEvent {
        data class SharedText(val text: String) : IntakeEvent
        data class OpenConversation(val localConversationId: String) : IntakeEvent
    }
}
