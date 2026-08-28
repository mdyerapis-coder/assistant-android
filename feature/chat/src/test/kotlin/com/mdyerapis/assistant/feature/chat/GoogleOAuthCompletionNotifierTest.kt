package com.mdyerapis.assistant.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleOAuthCompletionNotifierTest {

    @Test
    fun `oauth completion increments the observable version`() {
        val notifier = GoogleOAuthCompletionNotifier()

        notifier.notifyCompletion()

        assertEquals(1L, notifier.completionVersion.value)
    }
}
