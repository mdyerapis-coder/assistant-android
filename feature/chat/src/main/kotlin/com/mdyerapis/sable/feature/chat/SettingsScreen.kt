package com.mdyerapis.sable.feature.chat

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdyerapis.sable.core.designsystem.theme.SableMonoFont
import androidx.hilt.navigation.compose.hiltViewModel

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
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
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

                    var searchQuery by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search models...", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    val filteredModels = uiState.models
                        .filter { matchesModelCategory(it, selectedCategory) }
                        .filter { matchesModelQuery(it, searchQuery) }

                    // Group by provider for collapsible per-provider menus
                    val providerStatusMap = uiState.providerStatuses.associateBy { it.name }
                    val grouped = filteredModels.groupBy { it.provider }
                    // Keep expanded set in composition — survives recomposition, resets on screen exit
                    var expandedProviders by remember { mutableStateOf(setOf<String>()) }
                    // Auto-expand the group holding the active model once the catalog arrives
                    // (and again when the user picks a model from a different provider).
                    val selectedProvider = uiState.models.firstOrNull { it.id == uiState.selectedModelId }?.provider
                    LaunchedEffect(selectedProvider) {
                        if (selectedProvider != null) expandedProviders = expandedProviders + selectedProvider
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isLoadingModels) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Loading models catalog...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                            }
                        } else if (uiState.modelError != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    Text(
                                        "Couldn't load models: ${uiState.modelError}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.refreshModels() }) {
                                        Text("Retry")
                                    }
                                }
                            }
                        } else if (grouped.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("No models matching filters.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                            }
                        } else {
                            grouped.entries.sortedBy { it.key }.forEach { (providerName, providerModels) ->
                                val status = providerStatusMap[providerName]
                                val isConfigured = status?.configured ?: true
                                val isExpanded = providerName in expandedProviders
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    tonalElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    expandedProviders = if (isExpanded) expandedProviders - providerName
                                                    else expandedProviders + providerName
                                                }
                                                .padding(14.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isConfigured) ConfiguredGreen
                                                        else MaterialTheme.colorScheme.outline
                                                    )
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    providerName.replaceFirstChar { it.uppercase() },
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    "${providerModels.size} model${if (providerModels.size == 1) "" else "s"}${if (!isConfigured) " · not configured" else ""}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp
                                                else Icons.Filled.KeyboardArrowDown,
                                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        if (isExpanded) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(12.dp)
                                            ) {
                                                providerModels.forEach { option ->
                                                    ModelOptionCard(
                                                        displayName = displayNameFor(option),
                                                        rawModel = option.model,
                                                        isSelected = option.id == uiState.selectedModelId,
                                                        isEnabled = isConfigured,
                                                        onClick = { viewModel.selectModel(option.id) },
                                                    )
                                                }
                                            }
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

            // Section 3: Model providers
            item {
                Text(
                    text = "MODEL PROVIDERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (uiState.providerStatuses.isEmpty()) {
                            Text("Provider registry unavailable", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Your Sable server is unreachable or too old for /v1/providers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            uiState.providerStatuses.sortedBy { it.name }.forEach { provider ->
                                ProviderStatusRow(
                                    name = provider.name,
                                    note = provider.note,
                                    configured = provider.configured,
                                )
                            }
                            Text(
                                "Status: Configured = key wired in Bitwarden; Needs key = add one on the server. Tap a configured model at the top to make it your active one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Section: Device Info
            item {
                Text(
                    text = "DEVICE INFO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        // Snapshot once — ActivityManager queries on every recomposition are wasted work.
                        val deviceStats = remember {
                            val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                            val memInfo = android.app.ActivityManager.MemoryInfo()
                            activityManager.getMemoryInfo(memInfo)
                            Triple(
                                memInfo.totalMem / (1024 * 1024 * 1024),
                                memInfo.availMem / (1024 * 1024 * 1024),
                                android.os.Build.SOC_MODEL ?: "Unknown",
                            )
                        }
                        val (totalRamGb, availRamGb, soc) = deviceStats
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("RAM", style = MaterialTheme.typography.bodyMedium)
                            Text("${totalRamGb} GB (${availRamGb} GB free)", style = MaterialTheme.typography.bodySmall.copy(fontFamily = SableMonoFont), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("SoC", style = MaterialTheme.typography.bodyMedium)
                            Text(soc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = SableMonoFont), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text(
                    text = "INTEGRATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sable", style = MaterialTheme.typography.titleSmall)
                        val aboutContext = androidx.compose.ui.platform.LocalContext.current
                        val appVersion = remember {
                            try {
                                val pm = aboutContext.packageManager
                                val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    pm.getPackageInfo(aboutContext.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                                } else {
                                    @Suppress("DEPRECATION") pm.getPackageInfo(aboutContext.packageName, 0)
                                }
                                info.versionName ?: "unknown"
                            } catch (_: Exception) {
                                "unknown"
                            }
                        }
                        Text("Version $appVersion (V2 Program)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Backend Server: https://sable.llmclouds.au", style = MaterialTheme.typography.bodySmall.copy(fontFamily = SableMonoFont), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val serverOnline = !uiState.serverUnreachable && uiState.modelError == null
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (serverOnline) ConfiguredGreen else MaterialTheme.colorScheme.error)
                            )
                            Text(
                                if (serverOnline) "Server Status: Reachable"
                                else "Server Status: Unreachable — check connection or re-login",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (serverOnline) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
