package com.mdyerapis.assistant.feature.chat

import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdyerapis.assistant.core.designsystem.components.Composer
import com.mdyerapis.assistant.core.designsystem.components.ErrorBanner
import com.mdyerapis.assistant.core.designsystem.components.LoadingIndicator
import com.mdyerapis.assistant.core.designsystem.components.MessageBubble
import com.mdyerapis.assistant.core.designsystem.components.ModelStatusChip
import com.mdyerapis.assistant.core.designsystem.components.ToolCallChip
import com.mdyerapis.assistant.feature.localmodel.LocalModelDownloadState
import com.mdyerapis.assistant.feature.localmodel.LocalModelInfo
import com.mdyerapis.assistant.feature.localmodel.LocalModelSpec
import com.mdyerapis.assistant.feature.localmodel.LocalModelState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateSettings: () -> Unit = {},
    onNavigateSessions: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showClearDialog by remember { mutableStateOf(false) }
    val voiceController = remember { VoiceController(context) }
    DisposableEffect(Unit) {
        onDispose { voiceController.destroy() }
    }

    // Keep the VM's TTS flag in sync with the controller.
    LaunchedEffect(uiState.ttsEnabled) {
        voiceController.updateTtsEnabled(uiState.ttsEnabled)
    }

    // Speak a completed assistant message when TTS is on (both model modes).
    LaunchedEffect(uiState.chatState.messages.size) {
        val last = uiState.chatState.messages.lastOrNull()
        if (last != null && last.role == "assistant" && last.content.isNotBlank()) {
            voiceController.speak(last.content)
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceController.startListening { recognized ->
                inputText = recognized
            }
        }
    }

    fun startVoiceInput() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            voiceController.startListening { recognized ->
                inputText = recognized
            }
        } else {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshGoogleStatus()
    }

    LaunchedEffect(uiState.chatState.messages.size, uiState.chatState.currentContent) {
        if (uiState.chatState.messages.isNotEmpty() || uiState.chatState.currentContent.isNotEmpty()) {
            listState.animateScrollToItem(
                uiState.chatState.messages.size + uiState.chatState.activeToolCalls.size
            )
        }
    }

    LaunchedEffect(uiState.pendingComposerText) {
        val pending = uiState.pendingComposerText ?: return@LaunchedEffect
        inputText = pending
        viewModel.consumePendingComposerText()
    }

    if (uiState.showLocalModelDialog) {
        LocalModelDownloadDialog(
            specs = uiState.availableLocalSpecs,
            installedModels = uiState.installedLocalModels,
            downloadState = uiState.localDownloadState,
            onDismiss = { viewModel.showDownloadDialog(false) },
            onDownloadSpec = { spec -> viewModel.installLocalModelSpec(spec) },
            onDownloadCustom = { url, sha, id -> viewModel.installLocalModel(url, sha, id) },
            onSelectModel = { id -> viewModel.selectLocalModel(id) },
            onDeleteModel = { id -> viewModel.deleteLocalModel(id) },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Conversation") },
            text = { Text("Are you sure you want to clear current messages?") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearConversation()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val activeLabel = if (uiState.appModelMode == AppModelMode.OnDevice) {
                        val active = uiState.installedLocalModels.firstOrNull { it.isSelected }
                        active?.name ?: "On-Device LLM"
                    } else {
                        val active = uiState.models.firstOrNull { it.id == uiState.selectedModelId }
                        active?.model ?: "Cloud Assistant"
                    }

                    ModelStatusChip(
                        label = activeLabel,
                        isOnDevice = uiState.appModelMode == AppModelMode.OnDevice,
                        onClick = onNavigateSettings,
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateSessions) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "Conversation history"
                        )
                    }
                    if (uiState.chatState.messages.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Clear conversation"
                            )
                        }
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                    IconButton(onClick = { viewModel.toggleTts() }) {
                        Icon(
                            imageVector = if (uiState.ttsEnabled) {
                                Icons.Filled.VolumeUp
                            } else {
                                Icons.Filled.VolumeOff
                            },
                            contentDescription = if (uiState.ttsEnabled) {
                                "Mute spoken replies"
                            } else {
                                "Enable spoken replies"
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Composer(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = !uiState.chatState.isLoading,
                placeholder = if (uiState.appModelMode == AppModelMode.OnDevice) "Message local model..." else "Ask assistant...",
                modifier = Modifier.imePadding(),
                isListening = voiceController.isListening,
                onMicClick = {
                    if (voiceController.isListening) {
                        voiceController.stopListening()
                    } else {
                        startVoiceInput()
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.serverUnreachable) {
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        androidx.compose.material3.Text(
                            text = "Can't reach your assistant server. Check your connection or re-configure the server URL.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.TextButton(onClick = { viewModel.clearServerUnreachable() }) {
                                androidx.compose.material3.Text("Retry")
                            }
                            androidx.compose.material3.TextButton(onClick = { viewModel.reconfigureServer() }) {
                                androidx.compose.material3.Text("Re-configure")
                            }
                        }
                    }
                }
            }
            // Messages stream list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                if (uiState.chatState.messages.isEmpty() && uiState.chatState.currentContent.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (uiState.appModelMode == AppModelMode.OnDevice) "On-Device Assistant Ready" else "Assistant Ready",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (uiState.appModelMode == AppModelMode.OnDevice) "Chat privately and offline on your phone" else "Ask anything or check calendar, email, and reminders",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Quick Prompt Suggestion Chips — horizontally scrollable, never clips; primary CTA prominent, secondary muted
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val suggestions = if (uiState.appModelMode == AppModelMode.OnDevice) {
                                    listOf(
                                        "Say hello" to "Say hello and introduce yourself!",
                                        "Write a haiku" to "Write a haiku about technology.",
                                        "Explain quantum computing" to "Explain quantum computing in simple terms.",
                                    )
                                } else {
                                    listOf(
                                        "Morning brief" to "Give me a morning brief",
                                        "Unread emails" to "List my unread emails",
                                        "Today's schedule" to "What is on my calendar today?",
                                        "Remind me" to "Remind me to stretch in 30 minutes",
                                    )
                                }

                                suggestions.forEachIndexed { index, (label, prompt) ->
                                    val isPrimary = index == 0
                                    SuggestionChip(
                                        onClick = {
                                            viewModel.sendMessage(prompt)
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        shape = MaterialTheme.shapes.medium,
                                        colors = if (isPrimary) {
                                            SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        } else {
                                            SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                items(uiState.chatState.messages) { msg ->
                    MessageBubble(
                        message = msg,
                        modifier = Modifier.clickable {
                            clipboardManager.setText(AnnotatedString(msg.content))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                if (uiState.chatState.currentContent.isNotEmpty()) {
                    item {
                        MessageBubble(
                            content = uiState.chatState.currentContent,
                            role = "assistant"
                        )
                    }
                }

                items(uiState.chatState.activeToolCalls) { tc ->
                    ToolCallChip(toolCall = tc)
                }

                if (uiState.chatState.isLoading && uiState.chatState.currentContent.isEmpty() && uiState.chatState.activeToolCalls.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            LoadingIndicator()
                        }
                    }
                }
            }

            uiState.chatState.error?.let { error ->
                ErrorBanner(
                    message = error,
                    onRetry = if (uiState.appModelMode == AppModelMode.OnDevice && uiState.localModelState !is LocalModelState.Ready) {
                        { viewModel.showDownloadDialog(true) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun LocalModelDownloadDialog(
    specs: List<LocalModelSpec>,
    installedModels: List<LocalModelInfo>,
    downloadState: LocalModelDownloadState,
    onDismiss: () -> Unit,
    onDownloadSpec: (LocalModelSpec) -> Unit,
    onDownloadCustom: (String, String?, String) -> Unit,
    onSelectModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
) {
    var customUrl by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var customSha by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("On-Device Models") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Download on-device LLMs to chat offline with zero latency and full privacy.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                items(specs) { spec ->
                    val installed = installedModels.firstOrNull { it.id == spec.id }
                    val isDownloading = downloadState is LocalModelDownloadState.Downloading && downloadState.modelId == spec.id

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(spec.name, style = MaterialTheme.typography.titleSmall)
                                        if (spec.isUncensored) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.errorContainer
                                            ) {
                                                Text(
                                                    "UNCENSORED",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(spec.sizeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (installed != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (!installed.isSelected) {
                                            TextButton(onClick = { onSelectModel(spec.id) }) {
                                                Text("Use")
                                            }
                                        } else {
                                            Text(
                                                "Active",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            )
                                        }
                                        TextButton(onClick = { onDeleteModel(spec.id) }) {
                                            Text("Delete", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                } else if (isDownloading) {
                                    val prog = (downloadState as LocalModelDownloadState.Downloading).progress
                                    Text("${(prog * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            Text(
                                spec.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )

                            if (installed == null && !isDownloading) {
                                var modelUrlInput by remember { mutableStateOf(spec.defaultUrl) }
                                OutlinedTextField(
                                    value = modelUrlInput,
                                    onValueChange = { modelUrlInput = it },
                                    label = { Text("Model Download URL (.task / .bin)") },
                                    placeholder = {
                                        Text(
                                            "https://huggingface.co/.../resolve/main/<file>.task",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                                if (modelUrlInput.isBlank()) {
                                    Text(
                                        "Direct file URL required (must end in .task or .bin).",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                Button(
                                    onClick = { onDownloadCustom(modelUrlInput.trim(), spec.expectedSha256, spec.id) },
                                    enabled = modelUrlInput.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                ) {
                                    Text("Download ${spec.name}")
                                }
                            }

                            if (isDownloading) {
                                val prog = (downloadState as LocalModelDownloadState.Downloading).progress
                                LinearProgressIndicator(
                                    progress = { prog },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    var showCustom by remember { mutableStateOf(false) }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Custom Model URL", style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = { showCustom = !showCustom }) {
                                    Text(if (showCustom) "Hide" else "Enter URL")
                                }
                            }
                            if (showCustom) {
                                OutlinedTextField(
                                    value = customName,
                                    onValueChange = { customName = it },
                                    label = { Text("Model Name / ID") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                                OutlinedTextField(
                                    value = customUrl,
                                    onValueChange = { customUrl = it },
                                    label = { Text("Download URL (.task / .bin)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                                OutlinedTextField(
                                    value = customSha,
                                    onValueChange = { customSha = it },
                                    label = { Text("Expected SHA-256 (optional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                                Button(
                                    onClick = {
                                        val id = customName.trim().lowercase().replace(" ", "-").ifBlank { "custom-model" }
                                        if (customUrl.isNotBlank()) {
                                            onDownloadCustom(customUrl.trim(), customSha.ifBlank { null }, id)
                                        }
                                    },
                                    enabled = customUrl.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                ) {
                                    Text("Download Custom Model")
                                }
                            }
                        }
                    }
                }

                if (downloadState is LocalModelDownloadState.Error) {
                    item {
                        Text(
                            "Error: ${downloadState.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
