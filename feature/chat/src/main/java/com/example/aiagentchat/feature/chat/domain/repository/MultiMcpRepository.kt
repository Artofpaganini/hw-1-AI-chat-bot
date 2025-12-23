package com.example.aiagentchat.feature.chat.domain.repository

import com.example.aiagentchat.feature.chat.domain.model.McpServer
import com.example.aiagentchat.feature.chat.domain.model.McpTool
import kotlinx.coroutines.flow.Flow

interface MultiMcpRepository {
    suspend fun listServers(): List<McpServer>
    suspend fun listToolsForServer(serverId: String): Result<List<McpTool>>
    suspend fun callTool(serverId: String, toolName: String, arguments: Map<String, Any>): Result<String>
    fun observeServers(): Flow<List<McpServer>>
}


