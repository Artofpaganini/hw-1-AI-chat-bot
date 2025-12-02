package com.example.aiagentchat.core.domain.usecase

import com.example.aiagentchat.core.common.Result
import com.example.aiagentchat.core.domain.model.Message
import com.example.aiagentchat.core.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class SendMessageUseCase(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(message: String): Flow<Result<Message>> {
        return chatRepository.sendMessage(message)
    }
}

