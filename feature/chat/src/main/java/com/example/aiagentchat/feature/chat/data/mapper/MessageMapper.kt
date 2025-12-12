package com.example.aiagentchat.feature.chat.data.mapper

import com.example.aiagentchat.core.database.entity.ChatMessageEntity
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.feature.chat.domain.model.MessageMetrics

fun ChatMessageEntity.toDomain(): Message {
    val responseTime = responseTimeMs
    val input = inputTokens
    val output = outputTokens
    val cost = costUsd
    return Message(
        id = id,
        content = content,
        isUser = isUser,
        model = modelId?.let { modelId ->
            AiModel.entries.find { it.modelId == modelId }
        },
        metrics = if (responseTime != null && input != null && output != null && cost != null) {
            MessageMetrics(
                responseTimeMs = responseTime,
                inputTokens = input,
                outputTokens = output,
                costUsd = cost
            )
        } else null,
        timestamp = timestamp,
        isCompressed = isCompressed
    )
}

fun Message.toEntity(): ChatMessageEntity {
    return ChatMessageEntity(
        id = id,
        content = content,
        isUser = isUser,
        modelId = model?.modelId,
        responseTimeMs = metrics?.responseTimeMs,
        inputTokens = metrics?.inputTokens,
        outputTokens = metrics?.outputTokens,
        costUsd = metrics?.costUsd,
        timestamp = timestamp,
        isCompressed = isCompressed
    )
}

