package com.example.aiagentchat.feature.chat.presentation.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * ChatScreen is now integrated into HomeScreen.
 * This is kept for backward compatibility.
 */
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Chat functionality is now in HomeScreen",
            modifier = Modifier.padding(16.dp)
        )
    }
}

