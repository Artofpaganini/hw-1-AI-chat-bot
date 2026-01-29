package com.example.aiagentchat.feature.chat.domain.usecase

import android.util.Log
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto
import com.example.aiagentchat.feature.chat.data.api.ToolCallDto
import com.example.aiagentchat.feature.chat.data.converter.McpToolConverter
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.feature.chat.domain.model.McpServer
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository
import com.example.aiagentchat.feature.chat.domain.repository.McpRepository
import com.example.aiagentchat.feature.chat.domain.repository.MultiMcpRepository
import com.example.aiagentchat.feature.chat.domain.repository.MetricsRepository
import com.example.aiagentchat.core.common.preferences.PreferencesManager
import com.google.gson.Gson

class SendMessageUseCase(
    private val aiModelRepository: AiModelRepository,
    private val metricsRepository: MetricsRepository,
    private val mcpRepository: McpRepository? = null,
    private val multiMcpRepository: MultiMcpRepository? = null,
    private val preferencesManager: PreferencesManager? = null,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "SendMessageUseCase"
        private const val MAX_TOOL_CALL_ITERATIONS = 5
    }

    suspend operator fun invoke(
        model: AiModel,
        messages: List<ChatMessageDto>,
        enabledMcpTools: Set<String> = emptySet(),
        enabledMcpServerTools: Map<String, Set<String>> = emptyMap(),
        remoteControlDeviceId: String? = null
    ): Result<Message> {
        val startTime = System.currentTimeMillis()
        
        return try {
            Log.d(TAG, "SendMessageUseCase invoked with enabledMcpTools: $enabledMcpTools, enabledMcpServerTools: $enabledMcpServerTools")
            
            val allTools = mutableListOf<com.example.aiagentchat.feature.chat.data.api.ToolDto>()
            
            if (enabledMcpTools.isNotEmpty() && mcpRepository != null) {
                Log.d(TAG, "Loading MCP tools for: $enabledMcpTools")
                val loadedTools = loadMcpTools(enabledMcpTools)
                allTools.addAll(loadedTools)
            }
            
            if (enabledMcpServerTools.isNotEmpty() && multiMcpRepository != null) {
                Log.d(TAG, "Loading MCP server tools for: $enabledMcpServerTools")
                val serverTools = loadMcpServerTools(enabledMcpServerTools, remoteControlDeviceId)
                allTools.addAll(serverTools)
            }
            
            val requestMessages = if (allTools.isNotEmpty()) {
                messages.map { it.copy() } + listOf(
                    ChatMessageDto(
                        role = "system",
                        content = "You have access to the following tools. Use them when appropriate."
                    )
                )
            } else {
                messages
            }
            
            val request = com.example.aiagentchat.feature.chat.data.api.ChatRequest(
                model = model.modelId,
                messages = requestMessages,
                tools = if (allTools.isNotEmpty()) allTools else null
            )
            
            val response = aiModelRepository.sendMessage(model, requestMessages)
            
            return response.map { msg ->
                    val responseTimeMs = System.currentTimeMillis() - startTime
                    val metrics = metricsRepository.calculateMetrics(
                        model = model,
                        responseTimeMs = responseTimeMs,
                        inputTokens = msg.inputTokens,
                        outputTokens = msg.outputTokens
                    )
                    Message(
                        content = msg.content,
                        isUser = false,
                        model = model,
                        metrics = metrics
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in SendMessageUseCase", e)
            Result.failure(e)
        }
    }
    
    private suspend fun loadMcpTools(enabledTools: Set<String>): List<com.example.aiagentchat.feature.chat.data.api.ToolDto> {
        return try {
            val allTools = mcpRepository?.listTools()?.getOrNull() ?: emptyList()
            val filteredTools = allTools.filter { enabledTools.contains(it.name) }
            McpToolConverter.convertToAiTools(filteredTools)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading MCP tools", e)
            emptyList()
        }
    }
    
    private suspend fun loadMcpServerTools(
        enabledServerTools: Map<String, Set<String>>,
        remoteControlDeviceId: String?
    ): List<com.example.aiagentchat.feature.chat.data.api.ToolDto> {
        return try {
            val allTools = mutableListOf<com.example.aiagentchat.feature.chat.domain.model.McpTool>()
            
            enabledServerTools.forEach { (serverId, toolNames) ->
                multiMcpRepository?.listToolsForServer(serverId)?.getOrNull()?.let { serverTools ->
                    val filteredTools = serverTools.filter { toolNames.contains(it.name) }
                    allTools.addAll(filteredTools)
                }
            }
            
            McpToolConverter.convertToAiTools(allTools, remoteControlDeviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading MCP server tools", e)
            emptyList()
        }
    }
    
    private suspend fun processToolCalls(
        toolCalls: List<ToolCallDto>,
        remoteControlDeviceId: String?
    ): List<ChatMessageDto> {
        val results = mutableListOf<ChatMessageDto>()
        
        for (toolCall in toolCalls) {
            try {
                val functionName = toolCall.function.name
                val arguments = gson.fromJson(toolCall.function.arguments, Map::class.java) as? Map<String, Any> ?: emptyMap()
                
                val result = when {
                    functionName.startsWith("weather_") -> {
                        mcpRepository?.callTool(functionName, arguments)?.getOrNull() ?: "Error calling tool"
                    }
                    else -> {
                        val serverId = findServerIdForTool(functionName)
                        if (serverId != null) {
                            multiMcpRepository?.callTool(serverId, functionName, arguments)?.getOrNull() ?: "Error calling tool"
                        } else {
                            mcpRepository?.callTool(functionName, arguments)?.getOrNull() ?: "Error calling tool"
                        }
                    }
                }
                
                results.add(
                    ChatMessageDto(
                        role = "tool",
                        content = result,
                        toolCallId = toolCall.id
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error processing tool call ${toolCall.id}", e)
                results.add(
                    ChatMessageDto(
                        role = "tool",
                        content = "Error: ${e.message}",
                        toolCallId = toolCall.id
                    )
                )
            }
        }
        
        return results
    }
    
    private suspend fun findServerIdForTool(toolName: String): String? {
        return try {
            multiMcpRepository?.listServers()?.firstOrNull { server ->
                server.tools.any { it.name == toolName }
            }?.id
        } catch (e: Exception) {
            null
        }
    }
}

