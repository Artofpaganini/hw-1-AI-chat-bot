package com.example.aiagentchat.feature.chat.data.api

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    @SerializedName("model")
    val model: String,
    @SerializedName("messages")
    val messages: List<ChatMessageDto>,
    @SerializedName("temperature")
    val temperature: Double = 0.7,
    @SerializedName("max_tokens")
    val maxTokens: Int = 2048,
    @SerializedName("tools")
    val tools: List<ToolDto>? = null
)

data class ChatMessageDto(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: String? = null,
    @SerializedName("tool_calls")
    val toolCalls: List<ToolCallDto>? = null,
    @SerializedName("tool_call_id")
    val toolCallId: String? = null
)

data class ChatResponse(
    @SerializedName("id")
    val id: String?,
    @SerializedName("choices")
    val choices: List<ChatChoice>,
    @SerializedName("usage")
    val usage: UsageDto?
)

data class ChatChoice(
    @SerializedName("message")
    val message: ChatMessageDto,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class UsageDto(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

data class ToolDto(
    @SerializedName("type")
    val type: String = "function",
    @SerializedName("function")
    val function: ToolFunctionDto
)

data class ToolFunctionDto(
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("parameters")
    val parameters: ToolParametersDto
)

data class ToolParametersDto(
    @SerializedName("type")
    val type: String = "object",
    @SerializedName("properties")
    val properties: Map<String, ToolPropertyDto>,
    @SerializedName("required")
    val required: List<String>? = null
)

data class ToolPropertyDto(
    @SerializedName("type")
    val type: String,
    @SerializedName("description")
    val description: String? = null
)

data class ToolCallDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("type")
    val type: String = "function",
    @SerializedName("function")
    val function: ToolCallFunctionDto
)

data class ToolCallFunctionDto(
    @SerializedName("name")
    val name: String,
    @SerializedName("arguments")
    val arguments: String
)
