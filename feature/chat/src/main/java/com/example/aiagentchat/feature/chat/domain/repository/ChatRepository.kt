package com.example.aiagentchat.feature.chat.domain.repository

import com.example.aiagentchat.feature.chat.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getAllMessages(): Flow<List<Message>>
    suspend fun saveMessage(message: Message)
    suspend fun saveMessages(messages: List<Message>)
    suspend fun deleteAllMessages()
    suspend fun deleteMessage(messageId: String)
}

