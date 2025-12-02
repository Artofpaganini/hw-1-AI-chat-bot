package com.example.aiagentchat.core.domain.usecase

import com.example.aiagentchat.core.domain.repository.ChatRepository

class SetApiKeyUseCase(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(apiKey: String) {
        chatRepository.setApiKey(apiKey)
    }
}

