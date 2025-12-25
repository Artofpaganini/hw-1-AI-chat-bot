package com.example.aiagentchat.data.api

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    @SerializedName("model")
    val model: String,
    @SerializedName("messages")
    val messages: List<ChatMessageDto>,
    @SerializedName("temperature")
    val temperature: Double = 0.7,
    @SerializedName("max_tokens")
    val maxTokens: Int = 2048
)

data class ChatMessageDto(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: String
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







