package com.mdyerapis.sable.feature.chat

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class GoogleOAuthCompletionNotifier @Inject constructor() {
    private val _completionVersion = MutableStateFlow(0L)
    val completionVersion: StateFlow<Long> = _completionVersion.asStateFlow()

    fun notifyCompletion() {
        _completionVersion.update { it + 1 }
    }
}
