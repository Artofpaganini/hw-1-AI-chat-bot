package com.example.aiagentchat.feature.chat.domain.repository

import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.feature.chat.domain.model.ContextSummary
import com.example.aiagentchat.feature.chat.domain.model.ContextSummary.SummaryType
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getAllMessages(): Flow<List<Message>>
    suspend fun saveMessage(message: Message)
    suspend fun saveMessages(messages: List<Message>)
    suspend fun deleteAllMessages()
    suspend fun deleteMessage(messageId: String)
    suspend fun getLastMessages(isUser: Boolean, limit: Int): List<Message>
    fun getContextSummaries(type: SummaryType): Flow<List<ContextSummary>>
    suspend fun insertContextSummary(summary: ContextSummary)
    suspend fun trimContextSummaries(type: SummaryType, maxItems: Int)
    suspend fun deleteMessagesByIds(ids: List<String>)
}

