package com.example.aiagentchat.core.network.dto

import com.google.gson.annotations.SerializedName

data class DeepSeekChatResponseDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("choices")
    val choices: List<DeepSeekChoiceDto>,
    @SerializedName("usage")
    val usage: UsageDto?
)

data class DeepSeekChoiceDto(
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

