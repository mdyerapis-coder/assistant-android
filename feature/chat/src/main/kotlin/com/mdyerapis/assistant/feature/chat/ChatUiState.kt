package com.mdyerapis.assistant.feature.chat

import com.mdyerapis.assistant.backendclient.ChatApiClient
import com.mdyerapis.assistant.core.database.chat.ConversationSummary
import com.mdyerapis.assistant.core.model.ChatState
import com.mdyerapis.assistant.feature.localmodel.LocalModelDownloadState
import com.mdyerapis.assistant.feature.localmodel.LocalModelInfo
import com.mdyerapis.assistant.feature.localmodel.LocalModelSpec
import com.mdyerapis.assistant.feature.localmodel.LocalModelState

data class ChatUiState(
    val chatState: ChatState = ChatState(),
    val availableSessions: List<ConversationSummary> = emptyList(),
    val activeConversationId: String? = null,
    val isGoogleConnected: Boolean = false,
    val models: List<ChatApiClient.ModelOption> = emptyList(),
    val selectedModelId: String? = null,
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
)
