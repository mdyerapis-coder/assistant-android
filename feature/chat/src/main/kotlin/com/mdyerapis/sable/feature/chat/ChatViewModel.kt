package com.mdyerapis.sable.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdyerapis.sable.backendclient.ChatApiClient
import com.mdyerapis.sable.backendclient.ChatReducer
import com.mdyerapis.sable.backendclient.ChatTransport
import com.mdyerapis.sable.backendclient.ChatTurnRequest
import com.mdyerapis.sable.backendclient.RemoteChatTransport
import com.mdyerapis.sable.backendclient.ThreadsApi
import com.mdyerapis.sable.core.database.chat.ConversationStore
import com.mdyerapis.sable.core.database.chat.StoredMessage
import com.mdyerapis.sable.core.model.ChatEvent
import com.mdyerapis.sable.core.model.ChatMessage
import com.mdyerapis.sable.core.model.ChatState
import com.mdyerapis.sable.core.network.OkHttpClientFactory
import com.mdyerapis.sable.core.security.BearerTokenRepository
import com.mdyerapis.sable.feature.localmodel.LlmInferenceService
import com.mdyerapis.sable.feature.localmodel.LocalChatTransport
import com.mdyerapis.sable.feature.localmodel.LocalModelRepository
import com.mdyerapis.sable.feature.localmodel.LocalModelSpec
import com.mdyerapis.sable.feature.localmodel.LocalModelState
import com.mdyerapis.sable.feature.localmodel.DefaultLocalGoogleGateway
import com.mdyerapis.sable.feature.localmodel.LocalGoogleGateway
import com.mdyerapis.sable.feature.localmodel.LocalReminderGateway
import com.mdyerapis.sable.feature.localmodel.NoOpLocalReminderGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
open class ChatViewModel @Inject constructor(
    private val tokenRepository: BearerTokenRepository,
    private val googleAccountManager: GoogleAccountManager,
    private val googleOAuthCompletionNotifier: GoogleOAuthCompletionNotifier,
    private val modelPreferenceRepository: ModelPreferenceRepository,
    private val localModelRepository: LocalModelRepository,
    private val llmInferenceService: LlmInferenceService,
    private val conversationStore: ConversationStore,
    private val externalIntake: ExternalIntake,
    private val localReminderGateway: LocalReminderGateway = NoOpLocalReminderGateway,
    private val localGoogleGateway: LocalGoogleGateway = DefaultLocalGoogleGateway(tokenRepository),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            availableLocalSpecs = localModelRepository.availableSpecs,
            hasCloudSession = tokenRepository.getToken() != null,
            chatBaseUrl = tokenRepository.getBaseUrl()?.takeIf { it.isNotBlank() }
                ?: "https://assistant.llmclouds.au",
            oauthRelayUrl = tokenRepository.getOauthRelayUrl(),
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState

    private var apiClient: ChatApiClient? = null
    private var authedHttpClient: OkHttpClient? = null
    private var remoteTransport: ChatTransport? = null
    private val localTransport = LocalChatTransport(
        llmInferenceService,
        localModelRepository,
        localReminderGateway,
        localGoogleGateway,
    )
    private var baseUrl: String = "https://assistant.llmclouds.au"
    private var streamJob: Job? = null

    private var activeConversationId: String? = null
    private var activeServerConversationId: String? = null
    private var threadsApi: ThreadsApi? = null

    init {
        if (tokenRepository.getToken() == null && tokenRepository.hasOnDeviceAccess()) {
            modelPreferenceRepository.setAppModelMode(AppModelMode.OnDevice)
        }
        bindOauthRelay()
        val saved = tokenRepository.getBaseUrl()?.takeIf { it.isNotBlank() }
        initClient(saved ?: baseUrl)
        viewModelScope.launch {
            externalIntake.events.collect { event ->
                when (event) {
                    is ExternalIntake.IntakeEvent.SharedText ->
                        _uiState.value = _uiState.value.copy(pendingComposerText = event.text)
                    is ExternalIntake.IntakeEvent.OpenConversation ->
                        switchConversation(event.localConversationId)
                }
            }
        }
        viewModelScope.launch {
            googleOAuthCompletionNotifier.completionVersion.drop(1).collect {
                refreshGoogleStatus()
            }
        }
        viewModelScope.launch {
            modelPreferenceRepository.appModelMode.collect { mode ->
                val promptDownload = mode == AppModelMode.OnDevice &&
                    localModelRepository.state.value !is LocalModelState.Ready
                _uiState.value = _uiState.value.copy(
                    appModelMode = mode,
                    showLocalModelDialog = _uiState.value.showLocalModelDialog || promptDownload,
                )
            }
        }
        viewModelScope.launch {
            localModelRepository.state.collect { state ->
                _uiState.value = _uiState.value.copy(localModelState = state)
            }
        }
        viewModelScope.launch {
            localModelRepository.installedModels.collect { models ->
                _uiState.value = _uiState.value.copy(installedLocalModels = models)
            }
        }
        viewModelScope.launch {
            localModelRepository.downloadState.collect { dlState ->
                _uiState.value = _uiState.value.copy(localDownloadState = dlState)
            }
        }
        viewModelScope.launch {
            conversationStore.conversations.collect { sessions ->
                _uiState.value = _uiState.value.copy(availableSessions = sessions)
            }
        }
        // Hydrate the most recent conversation.
        viewModelScope.launch {
            val latest = conversationStore.conversations.first().firstOrNull()
            if (latest != null) {
                activeConversationId = latest.id
                activeServerConversationId = latest.serverConversationId
                _uiState.value = _uiState.value.copy(
                    activeConversationId = latest.id,
                    chatState = _uiState.value.chatState.copy(
                        conversationId = latest.serverConversationId
                    )
                )
                loadMessages(latest.id)
            } else {
                ensureConversation()
            }
        }
    }

    private suspend fun ensureConversation(): String {
        val existing = activeConversationId
        if (existing != null) return existing
        val id = conversationStore.createConversation(
            modelId = modelPreferenceRepository.getSelectedModelId(),
            mode = _uiState.value.appModelMode.name,
        )
        activeConversationId = id
        return id
    }

    private fun loadMessages(conversationId: String) {
        viewModelScope.launch {
            conversationStore.messagesFor(conversationId).collect { stored ->
                val messages = stored.map {
                    ChatMessage(id = it.id, role = it.role, content = it.content, timestamp = it.createdAt)
                }
                _uiState.value = _uiState.value.copy(
                    chatState = _uiState.value.chatState.copy(messages = messages)
                )
            }
        }
    }

    fun initClient(baseUrl: String) {
        if (apiClient != null) return
        val token = tokenRepository.getToken() ?: return
        this.baseUrl = baseUrl
        val client = OkHttpClientFactory.create().newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                )
            }
            .build()
        authedHttpClient = client
        apiClient = ChatApiClient(client, baseUrl)
        remoteTransport = RemoteChatTransport(apiClient!!)
        threadsApi = createThreadsApi(client, baseUrl)
        bindOauthRelay()
        _uiState.value = _uiState.value.copy(hasCloudSession = true, chatBaseUrl = baseUrl)
        refreshGoogleStatus()
        loadModels()
        loadProviderStatuses()
        // Phase 08: hydrate server threads into the Room cache on every
        // client (re)initialization — fresh installs resume from here.
        syncThreads()
    }

    /** Late-bind the cloud client after on-device-only onboarding later adds a token. */
    fun ensureCloudClient() {
        if (apiClient != null) {
            _uiState.value = _uiState.value.copy(hasCloudSession = tokenRepository.getToken() != null)
            return
        }
        val saved = tokenRepository.getBaseUrl()?.takeIf { it.isNotBlank() } ?: baseUrl
        initClient(saved)
        _uiState.value = _uiState.value.copy(hasCloudSession = tokenRepository.getToken() != null)
    }

    /**
     * Test seam (phase 08): unit tests override this to substitute a fake
     * [ThreadsApi] before [initClient] runs from the init block. Kept out
     * of the constructor because Dagger can't provision a lambda.
     */
    protected open fun createThreadsApi(
        client: OkHttpClient,
        baseUrl: String,
    ): ThreadsApi = ThreadsApi(client, baseUrl)

    /**
     * Fetches the server thread list and caches each as a conversation row
     * (no message fetch — messages load on open, phase 08). Server is the
     * source of truth; on failure the cached list stays untouched.
     */
    fun syncThreads() {
        val api = threadsApi ?: return
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { api.listThreads() }
                _uiState.value = _uiState.value.copy(sessionsOffline = false)
                for (thread in response.threads) {
                    conversationStore.cacheServerThread(
                        serverConversationId = thread.id,
                        title = thread.title,
                        preview = thread.preview,
                        createdAtMs = parseIsoEpochMs(thread.created_at),
                        updatedAtMs = parseIsoEpochMs(thread.last_message_at),
                    )
                }
            } catch (_: Exception) {
                // Offline or backend unavailable — cached threads remain.
                _uiState.value = _uiState.value.copy(sessionsOffline = true)
            }
        }
    }

    /**
     * Replaces the local cache for a thread with the server's renderable
     * history. Called when opening a conversation that has a server id;
     * failures leave the cached copy in place.
     */
    private fun refreshThreadMessages(serverConversationId: String, localId: String) {
        val api = threadsApi ?: return
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.listMessages(serverConversationId)
                }
                val cached = response.messages.map { message ->
                    StoredMessage(
                        id = "$serverConversationId:${message.id}",
                        conversationId = localId,
                        role = message.role,
                        content = message.content,
                        toolCallId = null,
                        toolName = null,
                        toolArgsJson = null,
                        toolResult = null,
                        isError = false,
                        createdAt = parseIsoEpochMs(message.created_at),
                    )
                }
                conversationStore.replaceMessages(localId, cached)
            } catch (_: Exception) {
                // Offline — cached messages remain.
            }
        }
    }

    private fun loadProviderStatuses() {
        val client = apiClient ?: return
        viewModelScope.launch {
            try {
                val statuses = withContext(Dispatchers.IO) { client.listProviders() }
                _uiState.value = _uiState.value.copy(providerStatuses = statuses.providers)
            } catch (_: Exception) {
                // Older backends lack /v1/providers — settings hides the section.
            }
        }
    }

    private fun loadModels() {
        val client = apiClient ?: return
        _uiState.value = _uiState.value.copy(isLoadingModels = true, modelError = null)
        viewModelScope.launch {
            try {
                val catalog = withContext(Dispatchers.IO) { client.listModels() }
                val savedModelId = modelPreferenceRepository.getSelectedModelId()
                val availableIds = catalog.models.mapTo(mutableSetOf()) { it.id }
                val selectedModelId = when {
                    savedModelId in availableIds -> savedModelId
                    catalog.default_model_id in availableIds -> catalog.default_model_id
                    else -> catalog.models.firstOrNull()?.id
                }
                modelPreferenceRepository.setSelectedModelId(selectedModelId)
                _uiState.value = _uiState.value.copy(
                    models = catalog.models,
                    selectedModelId = selectedModelId,
                    recentModelIds = modelPreferenceRepository.getRecentModelIds(),
                    isLoadingModels = false,
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingModels = false,
                    modelError = exception.message ?: "Failed to load models",
                )
            }
        }
    }

    fun selectModel(modelId: String) {
        modelPreferenceRepository.setSelectedModelId(modelId)
        _uiState.value = _uiState.value.copy(
            selectedModelId = modelId,
            recentModelIds = modelPreferenceRepository.getRecentModelIds(),
        )
    }

    /** Retry entry point for the settings error card. */
    fun refreshModels() {
        loadModels()
        loadProviderStatuses()
    }

    fun selectLocalModel(id: String) {
        localModelRepository.selectModel(id)
    }

    fun setAppModelMode(mode: AppModelMode) {
        if (mode == AppModelMode.Backend && tokenRepository.getToken() == null) {
            _uiState.value = _uiState.value.copy(
                chatState = _uiState.value.chatState.copy(
                    error = "Cloud assistant needs a bearer token. Connect the server, or stay on-device.",
                ),
            )
            return
        }
        modelPreferenceRepository.setAppModelMode(mode)
    }

    fun clearConversation() {
        _uiState.value = _uiState.value.copy(
            chatState = ChatState(
                conversationId = null,
                messages = emptyList(),
                activeToolCalls = emptyList(),
                currentContent = "",
                isLoading = false,
                error = null,
            )
        )
        activeConversationId = null
        activeServerConversationId = null
        viewModelScope.launch {
            ensureConversation()
        }
    }

    fun consumePendingComposerText() {
        _uiState.value = _uiState.value.copy(pendingComposerText = null)
    }

    fun clearServerUnreachable() {
        _uiState.value = _uiState.value.copy(serverUnreachable = false)
    }

    fun reconfigureServer() {
        tokenRepository.clearToken()
        tokenRepository.clearBaseUrl()
        authedHttpClient = null
        apiClient = null
        remoteTransport = null
        threadsApi = null
        bindOauthRelay()
        _uiState.value = _uiState.value.copy(
            serverUnreachable = false,
            hasCloudSession = false,
        )
    }

    fun toggleTts() {
        _uiState.value = _uiState.value.copy(ttsEnabled = !_uiState.value.ttsEnabled)
    }


    fun startNewConversation() {
        activeConversationId = null
        activeServerConversationId = null

        activeConversationId = null
        activeServerConversationId = null
        _uiState.value = _uiState.value.copy(
            activeConversationId = null,
            chatState = ChatState(
                conversationId = null,
                messages = emptyList(),
                activeToolCalls = emptyList(),
                currentContent = "",
                isLoading = false,
                error = null,
            )
        )
        viewModelScope.launch {
            ensureConversation()
        }
    }

    fun switchConversation(id: String) {
        activeConversationId = id
        _uiState.value = _uiState.value.copy(
            activeConversationId = id,
            chatState = ChatState(
                conversationId = null,
                messages = emptyList(),
                activeToolCalls = emptyList(),
                currentContent = "",
                isLoading = false,
                error = null,
            )
        )
        loadMessages(id)
        viewModelScope.launch {
            val summary = conversationStore.conversations.first().firstOrNull { it.id == id }
            activeServerConversationId = summary?.serverConversationId
            _uiState.value = _uiState.value.copy(
                chatState = _uiState.value.chatState.copy(
                    conversationId = summary?.serverConversationId
                )
            )
            // Phase 08: resync this thread's messages from the server so
            // resume shows authoritative history, not just the cache.
            summary?.serverConversationId?.let { refreshThreadMessages(it, id) }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationStore.deleteConversation(id)
            if (activeConversationId == id) {
                activeConversationId = null
                activeServerConversationId = null
                _uiState.value = _uiState.value.copy(
                    activeConversationId = null,
                    chatState = ChatState(
                        conversationId = null,
                        messages = emptyList(),
                        activeToolCalls = emptyList(),
                        currentContent = "",
                        isLoading = false,
                        error = null,
                    )
                )
                ensureConversation()
            }
        }
    }

    fun showDownloadDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showLocalModelDialog = show)
    }

    fun installLocalModel(url: String, sha256: String? = null, modelId: String = "gemma-3-1b-it") {
        viewModelScope.launch {
            localModelRepository.install(url, sha256, modelId)
        }
    }

    fun installLocalModelSpec(spec: LocalModelSpec) {
        viewModelScope.launch {
            localModelRepository.install(spec.defaultUrl, spec.expectedSha256, spec.id)
        }
    }

    fun deleteLocalModel(id: String) {
        localModelRepository.deleteModel(id)
    }

    /** Re-fetch whether Google is connected. Call on deep-link return. */
    fun refreshGoogleStatus() {
        viewModelScope.launch {
            val connected = googleAccountManager.status()
            _uiState.value = _uiState.value.copy(isGoogleConnected = connected)
        }
    }

    fun connectGoogle() {
        if (apiClient == null && tokenRepository.getToken() != null) {
            ensureCloudClient()
        }
        val relay = tokenRepository.getOauthRelayUrl()
        if (tokenRepository.getToken() == null) {
            _uiState.value = _uiState.value.copy(
                chatState = _uiState.value.chatState.copy(
                    error = "Google Calendar and Gmail stay on the O1 relay at $relay. " +
                        "Paste a bearer token (Connect cloud assistant) even if chat stays on-device — " +
                        "the phone never stores a Google client_secret.",
                ),
            )
            return
        }
        googleAccountManager.launchOAuthFlow()
    }

    fun updateOauthRelayUrl(url: String) {
        val cleaned = url.trim().trimEnd('/').ifBlank {
            BearerTokenRepository.DEFAULT_OAUTH_RELAY_URL
        }
        tokenRepository.saveOauthRelayUrl(cleaned)
        bindOauthRelay()
    }

    private fun bindOauthRelay() {
        val relay = tokenRepository.getOauthRelayUrl()
        googleAccountManager.configureOauthRelayUrl(relay)
        val client = authedHttpClient
        if (client != null && tokenRepository.getToken() != null) {
            localGoogleGateway.configureRelay(
                RemoteChatTransport(ChatApiClient(client, relay)),
                relay,
            )
        } else {
            localGoogleGateway.configureRelay(null, relay)
        }
        _uiState.value = _uiState.value.copy(oauthRelayUrl = relay)
    }

    fun disconnectGoogle() {
        viewModelScope.launch {
            val ok = googleAccountManager.disconnect()
            if (ok) {
                _uiState.value = _uiState.value.copy(isGoogleConnected = false)
            }
        }
    }

    private fun drainNextQueued() {
        val queued = _uiState.value.pendingMessages
        if (queued.isEmpty()) return
        _uiState.value = _uiState.value.copy(pendingMessages = queued.drop(1))
        sendMessage(queued.first())
    }

    /** Cancels the in-flight reply, keeps any partial text, drops the queue. */
    fun stopGenerating() {
        val chat = _uiState.value.chatState
        streamJob?.cancel()
        streamJob = null
        llmInferenceService.cancel()
        val finalized = if (chat.currentContent.isNotBlank()) {
            val partial = ChatMessage(
                id = "stopped-${System.currentTimeMillis()}",
                role = "assistant",
                content = chat.currentContent,
                timestamp = System.currentTimeMillis(),
            )
            activeConversationId?.let { convoId ->
                viewModelScope.launch {
                    conversationStore.appendMessage(convoId, partial.id, partial.role, partial.content)
                }
            }
            chat.copy(
                messages = chat.messages + partial,
                currentMessageId = null,
                currentContent = "",
                isLoading = false,
            )
        } else {
            chat.copy(currentMessageId = null, isLoading = false)
        }
        _uiState.value = _uiState.value.copy(
            chatState = finalized,
            pendingMessages = emptyList(),
        )
    }

    fun sendMessage(text: String) {
        if (apiClient == null && tokenRepository.getToken() != null) {
            ensureCloudClient()
        }
        val currentState = _uiState.value.chatState
        if (currentState.isLoading && text.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                pendingMessages = _uiState.value.pendingMessages + text,
            )
            return
        }

        val onDevice = _uiState.value.appModelMode == AppModelMode.OnDevice

        val transport = resolveTransport()
        if (transport == null) {
            _uiState.value = _uiState.value.copy(
                chatState = currentState.copy(
                    error = "Cloud assistant isn't connected. Connect the server, or switch to on-device chat.",
                    isLoading = false,
                ),
            )
            return
        }

        val userMsg = ChatMessage(
            id = "user-${System.currentTimeMillis()}",
            role = "user",
            content = text,
            timestamp = System.currentTimeMillis(),
        )
        val withUserMessage = currentState.copy(
            messages = currentState.messages + userMsg,
            isLoading = true,
            error = null,
            currentContent = "",
        )
        _uiState.value = _uiState.value.copy(chatState = withUserMessage)
        val knownIds = withUserMessage.messages.map { it.id }.toSet()

        streamJob = viewModelScope.launch {
            val convoId = ensureConversation()
            conversationStore.appendMessage(convoId, userMsg.id, "user", text)
            try {
                val request = ChatTurnRequest(
                    message = text,
                    conversationId = outgoingConversationId(onDevice, convoId),
                    model = _uiState.value.selectedModelId,
                    history = withUserMessage.messages,
                )
                var state = _uiState.value.chatState
                transport.stream(request).collect { event ->
                    state = ChatReducer.reduce(state, event)
                    val unreachable = event is ChatEvent.Error && event.retryable && !onDevice
                    val needsDownload = onDevice &&
                        event is ChatEvent.Error &&
                        event.message.contains("not installed", ignoreCase = true)
                    _uiState.value = _uiState.value.copy(
                        chatState = state,
                        serverUnreachable = if (unreachable) true else _uiState.value.serverUnreachable,
                        showLocalModelDialog = _uiState.value.showLocalModelDialog || needsDownload,
                    )
                    if (event is ChatEvent.Delta && !onDevice) {
                        _uiState.value = _uiState.value.copy(serverUnreachable = false)
                    }
                }

                val serverConversationId = state.conversationId
                    ?.takeUnless { it.startsWith(LOCAL_CONVERSATION_PREFIX) }
                if (!onDevice &&
                    serverConversationId != null &&
                    serverConversationId != activeServerConversationId
                ) {
                    activeServerConversationId = serverConversationId
                    conversationStore.setServerConversationId(convoId, serverConversationId)
                    _uiState.value = _uiState.value.copy(
                        chatState = _uiState.value.chatState.copy(conversationId = serverConversationId),
                    )
                }
                state.messages
                    .filter { it.id !in knownIds }
                    .forEach { msg ->
                        conversationStore.appendMessage(convoId, msg.id, msg.role, msg.content)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    chatState = _uiState.value.chatState.copy(
                        error = "Couldn't reach $baseUrl - check the server is running and reachable.",
                        isLoading = false,
                    ),
                    serverUnreachable = !onDevice,
                )
            }
            streamJob = null
            drainNextQueued()
        }
    }

    private fun resolveTransport(): ChatTransport? =
        if (_uiState.value.appModelMode == AppModelMode.OnDevice) {
            localTransport
        } else {
            remoteTransport
        }

    private fun outgoingConversationId(onDevice: Boolean, localConvoId: String): String? {
        if (onDevice) return "$LOCAL_CONVERSATION_PREFIX$localConvoId"
        return _uiState.value.chatState.conversationId
            ?.takeUnless { it.startsWith(LOCAL_CONVERSATION_PREFIX) }
    }

    private fun parseIsoEpochMs(iso: String): Long = try {
        java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    } catch (_: Exception) {
        0L
    }

    companion object {
        const val LOCAL_CONVERSATION_PREFIX = "local:"
    }
}
