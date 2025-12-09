package com.example.aiagentchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.aiagentchat.presentation.ui.ChatScreen
import com.example.aiagentchat.presentation.ui.theme.AiAgentChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            AiAgentChatTheme {
                ChatScreen()
            }
        }
    }
}
