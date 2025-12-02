package com.example.aiagentchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.aiagentchat.core.data.di.dataModule
import com.example.aiagentchat.core.domain.di.domainModule
import com.example.aiagentchat.feature.chat.di.chatModule
import com.example.aiagentchat.feature.chat.presentation.event.ChatEvent
import com.example.aiagentchat.feature.chat.presentation.ui.ChatScreen
import com.example.aiagentchat.feature.chat.presentation.ui.SettingsDialog
import com.example.aiagentchat.feature.chat.presentation.viewmodel.ChatViewModel
import com.example.aiagentchat.ui.theme.AIAgentChatTheme
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
          override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    startKoin {
                              androidContext(applicationContext)
                              modules(dataModule, domainModule, chatModule)
                    }
                    setContent {
                              AIAgentChatTheme {
                                        Surface(
                                                modifier = Modifier.fillMaxSize(),
                                                color = MaterialTheme.colorScheme.background
                                        ) { ChatScreenContent() }
                              }
                    }
          }
}

@Composable
fun ChatScreenContent() {
          val viewModel: ChatViewModel = koinViewModel()
          var showSettingsDialog by remember { mutableStateOf(false) }
          val state by viewModel.state.collectAsState()

          ChatScreen(viewModel = viewModel, onSettingsClick = { showSettingsDialog = true })

          if (showSettingsDialog) {
                    SettingsDialog(
                            currentApiKey = state.apiKey,
                            onDismiss = { showSettingsDialog = false },
                            onSave = { key -> viewModel.handleEvent(ChatEvent.SetApiKey(key)) }
                    )
          }
}
