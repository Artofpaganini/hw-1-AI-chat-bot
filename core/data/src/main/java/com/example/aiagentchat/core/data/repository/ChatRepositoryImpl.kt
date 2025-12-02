package com.example.aiagentchat.core.data.repository

import com.example.aiagentchat.core.common.Result
import com.example.aiagentchat.core.data.datasource.ChatLocalDataSource
import com.example.aiagentchat.core.data.datasource.ChatRemoteDataSource
import com.example.aiagentchat.core.data.mapper.MessageMapper
import com.example.aiagentchat.core.domain.model.Message
import com.example.aiagentchat.core.domain.model.ParsedAiResponse
import com.example.aiagentchat.core.domain.repository.ChatRepository
import com.example.aiagentchat.core.network.dto.AiResponseDataDto
import com.example.aiagentchat.core.network.dto.ChatMessageDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class ChatRepositoryImpl(
    private val remoteDataSource: ChatRemoteDataSource,
    private val localDataSource: ChatLocalDataSource
) : ChatRepository {
    private val conversationHistory = mutableListOf<ChatMessageDto>()
    private val messagesFlow = MutableStateFlow<List<Message>>(emptyList())

    override fun sendMessage(message: String): Flow<Result<Message>> = flow {
        emit(Result.Loading)
        val apiKey = localDataSource.getApiKey().first()
        if (apiKey.isNullOrEmpty()) {
            emit(Result.Error(Exception("API ключ не указан. Получите ключ на https://platform.deepseek.com/")))
            return@flow
        }
        val userMessage = MessageMapper.toDomain(text = message, isUser = true)
        val currentMessages = messagesFlow.value.toMutableList()
        currentMessages.add(userMessage)
        messagesFlow.value = currentMessages
        conversationHistory.add(ChatMessageDto("user", message))
        val result = remoteDataSource.sendMessage(apiKey, conversationHistory)
        result.fold(
            onSuccess = { content ->
                conversationHistory.add(ChatMessageDto("assistant", content))
                val parsedData = AiResponseDataDto.fromJson(content)?.let { dto ->
                    MessageMapper.toParsedAiResponse(dto)
                }
                val displayText = parsedData?.toJsonText() ?: content
                val aiMessage = MessageMapper.toDomain(
                    text = displayText,
                    isUser = false,
                    rawResponse = content,
                    parsedData = parsedData
                )
                val updatedMessages = messagesFlow.value.toMutableList()
                updatedMessages.add(aiMessage)
                messagesFlow.value = updatedMessages
                emit(Result.Success(aiMessage))
            },
            onFailure = { exception ->
                emit(Result.Error(exception))
            }
        )
    }

    override fun getMessages(): Flow<List<Message>> {
        return messagesFlow.asStateFlow()
    }

    override fun clearHistory() {
        conversationHistory.clear()
        messagesFlow.value = emptyList()
    }

    override suspend fun setApiKey(apiKey: String) {
        localDataSource.setApiKey(apiKey)
    }

    override fun getApiKey(): Flow<String?> {
        return localDataSource.getApiKey()
    }
}

