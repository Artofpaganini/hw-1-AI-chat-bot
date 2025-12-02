package com.example.aiagentchat.core.domain.usecase

import com.example.aiagentchat.core.domain.model.Message
import com.example.aiagentchat.core.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class GetMessagesUseCase(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<List<Message>> {
        return chatRepository.getMessages()
    }
}

