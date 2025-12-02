package com.example.aiagentchat.core.domain.usecase

import com.example.aiagentchat.core.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class GetApiKeyUseCase(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<String?> {
        return chatRepository.getApiKey()
    }
}

