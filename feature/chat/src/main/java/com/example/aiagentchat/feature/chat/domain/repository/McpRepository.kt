package com.example.aiagentchat.feature.chat.domain.repository

import com.example.aiagentchat.feature.chat.domain.model.McpTool
import kotlinx.coroutines.flow.Flow

interface McpRepository {
    suspend fun listTools(): Result<List<McpTool>>
    fun observeTools(): Flow<List<McpTool>>
}

