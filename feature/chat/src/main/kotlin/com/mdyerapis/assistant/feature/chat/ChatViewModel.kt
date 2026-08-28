package com.mdyerapis.assistant.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdyerapis.assistant.backendclient.ChatApiClient
import com.mdyerapis.assistant.backendclient.ChatReducer
import com.mdyerapis.assistant.backendclient.SseFrameCodec
import com.mdyerapis.assistant.core.model.ChatMessage
import com.mdyerapis.assistant.core.model.ChatState
import com.mdyerapis.assistant.core.network.OkHttpClientFactory
import com.mdyerapis.assistant.core.security.BearerTokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val tokenRepository: BearerTokenRepository,
    private val googleAccountManager: GoogleAccountManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private var apiClient: ChatApiClient? = null
    private var baseUrl: String = "https://assistant.llmclouds.au"

    fun initClient(baseUrl: String) {
        if (apiClient != null) return
        val token = tokenRepository.getToken() ?: return
        this.baseUrl = baseUrl
        googleAccountManager.configureBaseUrl(baseUrl)
        val client = OkHttpClientFactory.create().newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                )
            }
            .build()
        apiClient = ChatApiClient(client, baseUrl)
        refreshGoogleStatus()
    }

    /** Re-fetch whether Google is connected. Call on deep-link return. */
    fun refreshGoogleStatus() {
        viewModelScope.launch {
            val connected = googleAccountManager.status()
            _uiState.value = _uiState.value.copy(isGoogleConnected = connected)
        }
    }

    fun connectGoogle() {
        googleAccountManager.launchOAuthFlow()
    }

    fun disconnectGoogle() {
        viewModelScope.launch {
            val ok = googleAccountManager.disconnect()
            if (ok) {
                _uiState.value = _uiState.value.copy(isGoogleConnected = false)
            }
        }
    }

    fun sendMessage(text: String) {
        val client = apiClient ?: return
        val currentState = _uiState.value.chatState

        val withUserMessage = currentState.copy(
            messages = currentState.messages + ChatMessage(
                id = "user-${System.currentTimeMillis()}",
                role = "user",
                content = text,
            ),
            isLoading = true,
            error = null,
        )
        _uiState.value = _uiState.value.copy(chatState = withUserMessage)

        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    client.streamChat(text, _uiState.value.chatState.conversationId)
                }
                if (!response.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        chatState = _uiState.value.chatState.copy(
                            error = "Server error: ${response.code}",
                            isLoading = false,
                        )
                    )
                    return@launch
                }

                val reader = response.body?.source() ?: return@launch
                var state = _uiState.value.chatState

                while (!reader.exhausted()) {
                    val line = reader.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    val event = SseFrameCodec.parse(line)
                    state = ChatReducer.reduce(state, event)
                    _uiState.value = _uiState.value.copy(chatState = state)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    chatState = _uiState.value.chatState.copy(
                        error = "Connection error: ${e.message}",
                        isLoading = false,
                    )
                )
            }
        }
    }
}
