package com.mdyerapis.sable.feature.chat

import com.mdyerapis.sable.backendclient.ChatApiClient
import com.mdyerapis.sable.core.database.chat.ConversationSummary
import com.mdyerapis.sable.core.model.ChatState
import com.mdyerapis.sable.feature.localmodel.LocalModelDownloadState
import com.mdyerapis.sable.feature.localmodel.LocalModelInfo
import com.mdyerapis.sable.feature.localmodel.LocalModelSpec
import com.mdyerapis.sable.feature.localmodel.LocalModelState

data class ChatUiState(
    val chatState: ChatState = ChatState(),
    val availableSessions: List<ConversationSummary> = emptyList(),
    val activeConversationId: String? = null,
    val isGoogleConnected: Boolean = false,
    val models: List<ChatApiClient.ModelOption> = emptyList(),
    val selectedModelId: String? = null,
    /** MRU cloud model ids (newest first, max 3) for the top-bar quick-switch. */
    val recentModelIds: List<String> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelError: String? = null,
    val appModelMode: AppModelMode = AppModelMode.Backend,
    val localModelState: LocalModelState = LocalModelState.NotInstalled,
    val installedLocalModels: List<LocalModelInfo> = emptyList(),
    val availableLocalSpecs: List<LocalModelSpec> = emptyList(),
    val localDownloadState: LocalModelDownloadState = LocalModelDownloadState.Idle,
    val showLocalModelDialog: Boolean = false,
    val ttsEnabled: Boolean = false,
    val pendingComposerText: String? = null,
    val serverUnreachable: Boolean = false,
    /** True after a threads-list fetch failed — sessions UI shows offline state. */
    val sessionsOffline: Boolean = false,
    /** Sends attempted while a reply streams; drained in order when it ends. */
    val pendingMessages: List<String> = emptyList(),
    /** Full server provider registry; empty until fetched (or when unsupported). */
    val providerStatuses: List<ChatApiClient.ProviderStatus> = emptyList(),
)
