package com.example.aiagentchat.feature.home.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.aiagentchat.feature.chat.presentation.chat.ChatEvent
import com.example.aiagentchat.feature.chat.presentation.chat.ChatViewModel
import com.example.aiagentchat.feature.chat.domain.model.ContextSummary
import com.example.aiagentchat.feature.chat.presentation.components.ChatInput
import com.example.aiagentchat.feature.chat.presentation.components.MessageBubble
import com.example.aiagentchat.feature.chat.presentation.components.MetricsComparisonCard
import com.example.aiagentchat.feature.chat.presentation.components.ModelSwitcher
import com.example.aiagentchat.feature.chat.presentation.components.McpToolsDialog
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ChatViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Launcher для запроса разрешения на уведомления
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                snackbarHostState.showSnackbar("Notification permission granted")
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Notification permission denied. Please enable it in settings.")
            }
        }
    }
    
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }
    
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(ChatEvent.OnDismissError)
        }
    }
    
    state.exportedToon?.let { toonData ->
        ToonExportDialog(
            toonData = toonData,
            onDismiss = { viewModel.onEvent(ChatEvent.OnDismissExport) },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("TOON Export", toonData)
                clipboard.setPrimaryClip(clip)
                scope.launch {
                    snackbarHostState.showSnackbar("Copied to clipboard")
                }
            }
        )
    }
    
    if (state.showMcpToolsDialog) {
        McpToolsDialog(
            tools = state.mcpTools,
            enabledTools = state.enabledMcpTools,
            onToolToggle = { toolName, enabled ->
                viewModel.onAction(com.example.aiagentchat.feature.chat.presentation.chat.ChatAction.ToggleMcpTool(toolName, enabled))
            },
            onDismiss = {
                viewModel.onAction(com.example.aiagentchat.feature.chat.presentation.chat.ChatAction.DismissMcpTools)
            },
            weatherNotificationsEnabled = state.weatherNotificationsEnabled,
            onWeatherNotificationsToggle = { enabled ->
                if (enabled) {
                    // Проверяем разрешение на уведомления (Android 13+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        
                        if (!hasPermission) {
                            // Запрашиваем разрешение
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            return@McpToolsDialog
                        }
                    }
                }
                viewModel.onAction(com.example.aiagentchat.feature.chat.presentation.chat.ChatAction.ToggleWeatherNotifications(enabled))
            },
            testModeEnabled = state.testModeEnabled,
            onTestModeToggle = { enabled ->
                viewModel.onAction(com.example.aiagentchat.feature.chat.presentation.chat.ChatAction.ToggleTestMode(enabled))
            },
            mcpServers = state.mcpServers,
            enabledMcpServerTools = state.enabledMcpServerTools,
            onServerToolToggle = { serverId, toolName, enabled ->
                viewModel.onAction(com.example.aiagentchat.feature.chat.presentation.chat.ChatAction.ToggleMcpServerTool(serverId, toolName, enabled))
            }
        )
    }
    
    Scaffold(
        modifier = Modifier.navigationBarsPadding(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    ModelSwitcher(
                        currentModel = state.selectedModel,
                        availableModels = state.availableModels,
                        configuredModels = state.configuredModels,
                        onModelChange = { viewModel.onEvent(ChatEvent.OnModelSelected(it)) }
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onAction(com.example.aiagentchat.feature.chat.presentation.chat.ChatAction.ShowMcpTools) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "MCP Tools",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (state.messages.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.onEvent(ChatEvent.OnExportChat) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export as TOON",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { viewModel.onEvent(ChatEvent.OnClearChat) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear chat",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    actionColor = MaterialTheme.colorScheme.error
                )
            }
        },
        bottomBar = {
            Column {
                MetricsComparisonCard(comparison = state.metricsComparison)
                ChatInput(
                    value = state.currentInput,
                    onValueChange = { viewModel.onEvent(ChatEvent.OnInputChange(it)) },
                    onSend = { viewModel.onEvent(ChatEvent.OnSendMessage) },
                    isLoading = state.isLoading
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                            )
                        )
                    )
            )
            
            AnimatedVisibility(
                visible = state.messages.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                EmptyStateContent()
            }
            
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (state.sessionContext.userSummaries.isNotEmpty() || state.sessionContext.aiSummaries.isNotEmpty()) {
                    item {
                        ContextSummarySection(
                            userSummaries = state.sessionContext.userSummaries,
                            aiSummaries = state.sessionContext.aiSummaries
                        )
                    }
                }
                items(
                    items = state.messages,
                    key = { it.id }
                ) { message ->
                    MessageBubble(message = message)
                }
            }
        }
    }
}

@Composable
private fun ContextSummarySection(
    userSummaries: List<ContextSummary>,
    aiSummaries: List<ContextSummary>
) {
    val summaryPairs = remember(userSummaries, aiSummaries) {
        buildSummaryPairs(userSummaries, aiSummaries)
    }
    
    val hasAnySummaries = userSummaries.isNotEmpty() || aiSummaries.isNotEmpty()
    if (!hasAnySummaries) return
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "История обсуждений",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        if (summaryPairs.isNotEmpty()) {
            summaryPairs.forEach { pair ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    QuestionAnswerCard(
                        question = pair.userSummary,
                        answer = pair.aiSummary
                    )
                }
            }
        } else {
            if (userSummaries.isNotEmpty()) {
                userSummaries.sortedByDescending { it.timestamp }.forEach { summary ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        SingleSummaryCard(
                            title = "Вопрос (сжатый) - последние ${userSummaries.size} сообщений",
                            summary = summary,
                            icon = "❓",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (aiSummaries.isNotEmpty()) {
                aiSummaries.sortedByDescending { it.timestamp }.forEach { summary ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        SingleSummaryCard(
                            title = "Ответ (сжатый) - последние ${aiSummaries.size} сообщений",
                            summary = summary,
                            icon = "💬",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

private data class SummaryPair(
    val userSummary: ContextSummary,
    val aiSummary: ContextSummary
)

private fun buildSummaryPairs(
    userSummaries: List<ContextSummary>,
    aiSummaries: List<ContextSummary>
): List<SummaryPair> {
    if (userSummaries.isEmpty() || aiSummaries.isEmpty()) return emptyList()
    
    val sortedUser = userSummaries.sortedByDescending { it.timestamp }
    val sortedAi = aiSummaries.sortedByDescending { it.timestamp }
    
    val pairs = mutableListOf<SummaryPair>()
    val usedAiIndices = mutableSetOf<Int>()
    
    for (userSummary in sortedUser) {
        val closestAi = sortedAi
            .mapIndexedNotNull { index, aiSummary ->
                if (index in usedAiIndices) null
                else {
                    val timeDiff = kotlin.math.abs(userSummary.timestamp - aiSummary.timestamp)
                    Triple(index, aiSummary, timeDiff)
                }
            }
            .minByOrNull { it.third }
        
        if (closestAi != null && closestAi.third < 3600000) {
            usedAiIndices.add(closestAi.first)
            pairs.add(SummaryPair(userSummary, closestAi.second))
        }
    }
    
    return pairs.sortedByDescending { 
        kotlin.math.max(it.userSummary.timestamp, it.aiSummary.timestamp) 
    }
}

@Composable
private fun SingleSummaryCard(
    title: String,
    summary: ContextSummary,
    icon: String,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = summary.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
            if (summary.keyFacts.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    summary.keyFacts.take(3).forEach { fact ->
                        Text(
                            text = "• $fact",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionAnswerCard(
    question: ContextSummary,
    answer: ContextSummary
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "❓",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Вопрос (сжатый)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = question.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
                if (question.keyFacts.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        question.keyFacts.take(3).forEach { fact ->
                            Text(
                                text = "• $fact",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                thickness = 1.dp
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "💬",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Ответ (сжатый)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = answer.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
                if (answer.keyFacts.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        answer.keyFacts.take(3).forEach { fact ->
                            Text(
                                text = "• $fact",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToonExportDialog(
    toonData: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📄 TOON Export",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Token-Oriented Object Notation",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = toonData,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onCopy()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text("Copy TOON")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🤖",
            style = MaterialTheme.typography.displayLarge
        )
        Text(
            text = "AI Chat",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "Start a conversation with AI.\nSwitch between models to compare responses.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
