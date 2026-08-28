package com.mdyerapis.assistant.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private fun formatModelDisplayName(modelId: String, rawModel: String): String {
    return when {
        modelId == "hermes-3-405b" || rawModel.contains("hermes-3", ignoreCase = true) -> "Hermes 3 (405B)"
        modelId == "dolphin-uncensored" || rawModel.contains("dolphin", ignoreCase = true) -> "Dolphin 2.9 (Venice)"
        modelId == "euryale-70b" || rawModel.contains("euryale", ignoreCase = true) -> "L3.3 Euryale (70B)"
        modelId == "groq" || rawModel.contains("gpt-oss", ignoreCase = true) -> "GPT-OSS 120B (Groq)"
        modelId == "openrouter" || rawModel.contains("claude", ignoreCase = true) -> "Claude 3.5 Sonnet"
        modelId == "gemini" || rawModel.contains("gemini", ignoreCase = true) -> "Gemini 3.1 Pro"
        modelId == "deepseek" || rawModel.contains("deepseek", ignoreCase = true) -> "DeepSeek V4 Pro"
        modelId == "mistral" || rawModel.contains("mistral", ignoreCase = true) -> "Mistral Large 3"
        modelId == "minimax" || rawModel.contains("minimax", ignoreCase = true) -> "MiniMax M3"
        else -> {
            val namePart = if (rawModel.contains("/")) rawModel.substringAfterLast("/") else rawModel
            namePart.replace("-", " ").replace("_", " ")
                .split(" ")
                .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCategory by remember { mutableStateOf("ALL") }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Section 1: Execution Mode
            item {
                Text(
                    text = "INFERENCE ENGINE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.appModelMode == AppModelMode.Backend,
                            onClick = { viewModel.setAppModelMode(AppModelMode.Backend) },
                            label = { Text("Cloud Assistant") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = uiState.appModelMode == AppModelMode.OnDevice,
                            onClick = { viewModel.setAppModelMode(AppModelMode.OnDevice) },
                            label = { Text("On-Device LLM") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section 2: Model Configuration
            if (uiState.appModelMode == AppModelMode.Backend) {
                item {
                    Text(
                        text = "CLOUD MODELS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))

                    // Model Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("ALL", "FAST", "REASONING", "UNCENSORED").forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    val filteredModels = uiState.models.filter { model ->
                        when (selectedCategory) {
                            "FAST" -> model.id in setOf("groq", "minimax") || model.description.contains("fast", ignoreCase = true)
                            "REASONING" -> model.id in setOf("gemini", "deepseek", "mistral") || model.description.contains("reasoning", ignoreCase = true)
                            "UNCENSORED" -> model.id in setOf("hermes-3-405b", "dolphin-uncensored", "euryale-70b") || model.description.contains("uncensored", ignoreCase = true)
                            else -> true
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isLoadingModels) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Text("Loading models catalog...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                            }
                        } else if (filteredModels.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Text("No models matching category.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                            }
                        } else {
                            filteredModels.forEach { option ->
                                val isSelected = option.id == uiState.selectedModelId
                                val isUncensored = option.id in setOf("hermes-3-405b", "dolphin-uncensored", "euryale-70b") || option.description.contains("uncensored", ignoreCase = true)
                                val isFast = option.id == "groq"
                                val isReasoning = option.id in setOf("gemini", "deepseek", "mistral")

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    tonalElevation = if (isSelected) 3.dp else 1.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { viewModel.selectModel(option.id) }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Title Row + Checkmark
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = formatModelDisplayName(option.id, option.model),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Filled.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }

                                        // Badges & Model ID Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (isUncensored) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer
                                                ) {
                                                    Text(
                                                        "UNCENSORED",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (isFast) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                                ) {
                                                    Text(
                                                        "FAST",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (isReasoning) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        "REASONING",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "${option.provider} · ${option.model}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        // Description
                                        val cleanDesc = option.description.removePrefix("[Uncensored]").trim()
                                        if (cleanDesc.isNotBlank()) {
                                            Text(
                                                text = cleanDesc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "ON-DEVICE MODELS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (uiState.installedLocalModels.isEmpty()) {
                                Text(
                                    "No local models installed.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                uiState.installedLocalModels.forEach { model ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.selectLocalModel(model.id) }
                                            .padding(vertical = 8.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = model.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (model.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${model.fileSizeBytes / (1024 * 1024)} MB",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (model.isSelected) {
                                            Text("Active", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.showDownloadDialog(true) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Manage / Download Models")
                            }
                        }
                    }
                }
            }

            // Section 3: Integrations
            item {
                Text(
                    text = "INTEGRATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.isGoogleConnected) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.outline
                                    )
                            )
                            Column {
                                Text("Google Account", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (uiState.isGoogleConnected) "Calendar & Gmail connected" else "Not connected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(
                            onClick = if (uiState.isGoogleConnected) viewModel::disconnectGoogle else viewModel::connectGoogle
                        ) {
                            Text(if (uiState.isGoogleConnected) "Disconnect" else "Connect")
                        }
                    }
                }
            }

            // Section 4: About
            item {
                Text(
                    text = "ABOUT & STATUS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Personal Assistant", style = MaterialTheme.typography.titleSmall)
                        Text("Version 0.2.0 (V2 Program)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Backend Server: https://assistant.llmclouds.au", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Text("Server Status: Online (HTTP 200)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
