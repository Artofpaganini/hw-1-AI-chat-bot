package com.example.aiagentchat.feature.chat.domain.usecase

import android.util.Log
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto
import com.example.aiagentchat.feature.chat.data.api.ToolCallDto
import com.example.aiagentchat.feature.chat.data.converter.McpToolConverter
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository
import com.example.aiagentchat.feature.chat.domain.repository.McpRepository
import com.example.aiagentchat.feature.chat.domain.repository.MetricsRepository

class SendMessageUseCase(
    private val aiModelRepository: AiModelRepository,
    private val metricsRepository: MetricsRepository,
    private val mcpRepository: McpRepository
) {
    companion object {
        private const val TAG = "SendMessageUseCase"
        private const val MAX_TOOL_CALL_ITERATIONS = 5
    }

    suspend operator fun invoke(
        model: AiModel,
        messages: List<ChatMessageDto>,
        enabledMcpTools: Set<String> = emptySet()
    ): Result<Message> {
        val startTime = System.currentTimeMillis()
        
        return try {
            Log.d(TAG, "SendMessageUseCase invoked with enabledMcpTools: $enabledMcpTools")
            
            val tools = if (enabledMcpTools.isNotEmpty()) {
                Log.d(TAG, "Loading MCP tools for: $enabledMcpTools")
                val loadedTools = loadMcpTools(enabledMcpTools)
                Log.d(TAG, "Loaded ${loadedTools.size} tools: ${loadedTools.map { it.function.name }}")
                loadedTools
            } else {
                Log.d(TAG, "No MCP tools enabled, skipping tool loading")
                emptyList()
            }
            
            val result = processWithToolCalls(model, messages, tools, startTime, 0)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error in SendMessageUseCase", e)
            Result.failure(e)
        }
    }
    
    private suspend fun loadMcpTools(enabledTools: Set<String>): List<com.example.aiagentchat.feature.chat.data.api.ToolDto> {
        return try {
            Log.d(TAG, "Loading MCP tools from repository...")
            val toolsResult = mcpRepository.listTools()
            if (toolsResult.isSuccess) {
                val mcpTools = toolsResult.getOrNull() ?: emptyList()
                Log.d(TAG, "Retrieved ${mcpTools.size} MCP tools: ${mcpTools.map { it.name }}")
                val filteredTools = mcpTools.filter { enabledTools.contains(it.name) }
                Log.d(TAG, "Filtered to ${filteredTools.size} enabled tools: ${filteredTools.map { it.name }}")
                val convertedTools = McpToolConverter.convertToAiTools(filteredTools)
                Log.d(TAG, "Converted to ${convertedTools.size} AI tools")
                convertedTools
            } else {
                val error = toolsResult.exceptionOrNull()
                Log.e(TAG, "Failed to load MCP tools", error)
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading MCP tools", e)
            emptyList()
        }
    }
    
    private suspend fun processWithToolCalls(
        model: AiModel,
        messages: List<ChatMessageDto>,
        tools: List<com.example.aiagentchat.feature.chat.data.api.ToolDto>,
        startTime: Long,
        iteration: Int
    ): Result<Message> {
        if (iteration >= MAX_TOOL_CALL_ITERATIONS) {
            Log.w(TAG, "Maximum tool call iterations ($MAX_TOOL_CALL_ITERATIONS) reached")
            return Result.failure(Exception("Maximum tool call iterations reached"))
        }
        
        Log.d(TAG, "Processing with ${tools.size} tools, iteration: $iteration")
        if (tools.isNotEmpty()) {
            Log.d(TAG, "Tools being sent to AI: ${tools.map { it.function.name }}")
        }
        
        val response = aiModelRepository.sendMessage(model, messages, tools.takeIf { it.isNotEmpty() })
        
        return when {
            response.isSuccess -> {
                val aiResponse = response.getOrNull() ?: return Result.failure(Exception("Empty response"))
                Log.d(TAG, "AI response received. Finish reason: ${aiResponse.finishReason}, Tool calls: ${aiResponse.toolCalls?.size ?: 0}")
                
                val hasToolCalls = aiResponse.toolCalls != null && aiResponse.toolCalls.isNotEmpty()
                val finishReasonIndicatesToolCalls = aiResponse.finishReason == "tool_calls" || 
                                                      aiResponse.finishReason == "function_call" ||
                                                      aiResponse.finishReason == "tool_use"
                
                if (hasToolCalls || finishReasonIndicatesToolCalls) {
                    val toolCallsToProcess = aiResponse.toolCalls ?: emptyList()
                    if (toolCallsToProcess.isNotEmpty()) {
                        Log.d(TAG, "AI model requested ${toolCallsToProcess.size} tool calls: ${toolCallsToProcess.map { it.function.name }}")
                        handleToolCalls(model, messages, toolCallsToProcess, tools, startTime, iteration)
                    } else {
                        Log.w(TAG, "Finish reason indicates tool calls but no tool_calls in response. Finish reason: ${aiResponse.finishReason}")
                        val responseTimeMs = System.currentTimeMillis() - startTime
                        val metrics = metricsRepository.calculateMetrics(
                            model = model,
                            responseTimeMs = responseTimeMs,
                            inputTokens = aiResponse.inputTokens,
                            outputTokens = aiResponse.outputTokens
                        )
                        Result.success(
                            Message(
                                content = aiResponse.content.ifEmpty { "No response content" },
                                isUser = false,
                                model = model,
                                metrics = metrics
                            )
                        )
                    }
                } else {
                    Log.d(TAG, "AI model returned direct response (no tool calls). Content length: ${aiResponse.content.length}")
                    val responseTimeMs = System.currentTimeMillis() - startTime
                    val metrics = metricsRepository.calculateMetrics(
                        model = model,
                        responseTimeMs = responseTimeMs,
                        inputTokens = aiResponse.inputTokens,
                        outputTokens = aiResponse.outputTokens
                    )
                    Result.success(
                        Message(
                            content = aiResponse.content.ifEmpty { "No response content" },
                            isUser = false,
                            model = model,
                            metrics = metrics
                        )
                    )
                }
            }
            else -> {
                val error = response.exceptionOrNull()
                Log.e(TAG, "Error from AI model", error)
                Result.failure(error ?: Exception("Unknown error"))
            }
        }
    }
    
    private suspend fun handleToolCalls(
        model: AiModel,
        currentMessages: List<ChatMessageDto>,
        toolCalls: List<ToolCallDto>,
        tools: List<com.example.aiagentchat.feature.chat.data.api.ToolDto>,
        startTime: Long,
        iteration: Int
    ): Result<Message> {
        val updatedMessages = currentMessages.toMutableList()
        
        toolCalls.forEach { toolCall ->
            val toolName = toolCall.function.name
            val argumentsJson = toolCall.function.arguments
            val arguments = McpToolConverter.parseToolCallArguments(argumentsJson)
            
            Log.d(TAG, "Calling MCP tool: $toolName with arguments: $arguments")
            
            val toolResult = mcpRepository.callTool(toolName, arguments)
            
            val toolResultContent = if (toolResult.isSuccess) {
                toolResult.getOrNull() ?: "Tool execution completed"
            } else {
                val error = toolResult.exceptionOrNull()
                Log.e(TAG, "Tool call failed: $toolName", error)
                "Error: ${error?.message ?: "Unknown error"}"
            }
            
            updatedMessages.add(
                ChatMessageDto(
                    role = "assistant",
                    content = null,
                    toolCalls = listOf(toolCall)
                )
            )
            
            updatedMessages.add(
                ChatMessageDto(
                    role = "tool",
                    content = toolResultContent,
                    toolCallId = toolCall.id
                )
            )
        }
        
        return processWithToolCalls(model, updatedMessages, tools, startTime, iteration + 1)
    }
}

