package com.example.aiagentchat.core.domain.repository

import com.example.aiagentchat.core.common.Result
import com.example.aiagentchat.core.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun sendMessage(message: String): Flow<Result<Message>>
    fun getMessages(): Flow<List<Message>>
    fun clearHistory()
    suspend fun setApiKey(apiKey: String)
    fun getApiKey(): Flow<String?>
}

