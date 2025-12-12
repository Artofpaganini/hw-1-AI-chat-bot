package com.example.aiagentchat.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val isUser: Boolean,
    val modelId: String?,
    val responseTimeMs: Long?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val costUsd: Double?,
    val timestamp: Long,
    val isCompressed: Boolean
)

