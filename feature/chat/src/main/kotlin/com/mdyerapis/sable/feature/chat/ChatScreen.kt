package com.mdyerapis.sable.feature.chat

import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdyerapis.sable.core.designsystem.components.Composer
import com.mdyerapis.sable.core.designsystem.components.EmberStreamingIndicator
import com.mdyerapis.sable.core.designsystem.components.dayLabel
import com.mdyerapis.sable.core.designsystem.components.needsDaySeparator
import com.mdyerapis.sable.feature.chat.R as ChatR
import com.mdyerapis.sable.core.designsystem.components.ErrorBanner
import com.mdyerapis.sable.core.designsystem.components.MessageBubble
import com.mdyerapis.sable.core.designsystem.components.ModelStatusChip
import com.mdyerapis.sable.core.designsystem.components.ToolCallChip
import com.mdyerapis.sable.feature.localmodel.LocalModelDownloadState
import com.mdyerapis.sable.feature.localmodel.LocalModelInfo
import com.mdyerapis.sable.feature.localmodel.LocalModelSpec
import com.mdyerapis.sable.feature.localmodel.LocalModelState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onNavigateSettings: () -> Unit = {},
    onNavigateSessions: () -> Unit = {},
    onReconfigure: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showClearDialog by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
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
                        active?.let { displayNameFor(it) } ?: "Cloud Assistant"
                    }

                    var showModelSwitcher by remember { mutableStateOf(false) }
                    // Quick-switch: MRU cloud models in a dropdown; falls back to
                    // Settings when on-device or no history yet.
                    val recentOptions = uiState.recentModelIds
                        .mapNotNull { id -> uiState.models.firstOrNull { it.id == id } }
                        .filter { it.id != uiState.selectedModelId }
                    val canQuickSwitch = uiState.appModelMode == AppModelMode.Backend && recentOptions.isNotEmpty()
                    Box {
                        ModelStatusChip(
                            label = activeLabel,
                            isOnDevice = uiState.appModelMode == AppModelMode.OnDevice,
                            onClick = { if (canQuickSwitch) showModelSwitcher = true else onNavigateSettings() },
                        )
                        DropdownMenu(
                            expanded = showModelSwitcher,
                            onDismissRequest = { showModelSwitcher = false }
                        ) {
                            recentOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(displayNameFor(option))
                                            Text(
                                                option.model,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectModel(option.id)
                                        showModelSwitcher = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("All models...") },
                                onClick = {
                                    showModelSwitcher = false
                                    onNavigateSettings()
                                }
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Conversation history") },
                                onClick = {
                                    showOverflow = false
                                    onNavigateSessions()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (uiState.ttsEnabled) "Mute spoken replies" else "Enable spoken replies") },
                                onClick = {
                                    showOverflow = false
                                    viewModel.toggleTts()
                                }
                            )
                            if (uiState.chatState.messages.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Clear conversation") },
                                    onClick = {
                                        showOverflow = false
                                        showClearDialog = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    showOverflow = false
                                    onNavigateSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    val label = if (uiState.appModelMode == AppModelMode.OnDevice) {
                                        uiState.installedLocalModels.firstOrNull { it.isSelected }?.name ?: "On-Device model"
                                    } else {
                                        uiState.models.firstOrNull { it.id == uiState.selectedModelId }
                                            ?.let { displayNameFor(it) } ?: "Cloud model"
                                    }
                                    Text("Model: $label")
                                },
                                onClick = {
                                    showOverflow = false
                                    onNavigateSettings()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
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
                enabled = true,
                placeholder = if (uiState.appModelMode == AppModelMode.OnDevice) "Message local model..." else "Ask assistant...",
                isStreaming = uiState.chatState.isLoading,
                onStop = { viewModel.stopGenerating() },
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
                            text = "Can't reach Sable. Check your connection or re-configure.",
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
                            androidx.compose.material3.TextButton(onClick = { viewModel.reconfigureServer(); onReconfigure() }) {
                                androidx.compose.material3.Text("Re-configure")
                            }
                        }
                    }
                }
            }
            // Messages stream list — layered over a static droid watermark.
            val watermarkDark = androidx.compose.foundation.isSystemInDarkTheme()
            val watermarkEmber = MaterialTheme.colorScheme.primary
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(220.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    watermarkEmber.copy(
                                        alpha = if (watermarkDark) 0.10f else 0.07f
                                    ),
                                    Color.Transparent,
                                ),
                                center = center,
                                radius = size.minDimension / 1.1f,
                            ),
                            radius = size.minDimension / 1.1f,
                        )
                    },
            ) {
                Image(
                    painter = painterResource(ChatR.drawable.ic_droid_avatar),
                    contentDescription = null,
                    alpha = if (watermarkDark) 0.07f else 0.05f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
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
                                    text = if (uiState.appModelMode == AppModelMode.OnDevice) "On-Device Sable ready." else "Sable ready.",
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
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = fadeIn(animationSpec = tween(350, delayMillis = index * 60))
                                    ) {
                                        Surface(
                                            onClick = { viewModel.sendMessage(prompt) },
                                            shape = MaterialTheme.shapes.medium,
                                            color = if (isPrimary) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isPrimary) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            ),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(horizontal = 14.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(
                                                            if (isPrimary) MaterialTheme.colorScheme.primary
                                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                            androidx.compose.foundation.shape.CircleShape
                                                        )
                                                )
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                itemsIndexed(
                    uiState.chatState.messages,
                    key = { _, msg -> msg.id },
                ) { index, msg ->
                    if (msg.timestamp > 0L &&
                        (index == 0 || needsDaySeparator(
                            uiState.chatState.messages[index - 1].timestamp,
                            msg.timestamp,
                        ))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                text = dayLabel(msg.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                    }
                    MessageBubble(
                        message = msg,
                        modifier = Modifier.combinedClickable(
                            onLongClick = {
                                clipboardManager.setText(AnnotatedString(msg.content))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onClick = {},
                        )
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
                            EmberStreamingIndicator()
                        }
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
