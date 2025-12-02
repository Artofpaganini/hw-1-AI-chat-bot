package com.example.aiagentchat.core.domain.usecase

import com.example.aiagentchat.core.domain.repository.ChatRepository

class ClearHistoryUseCase(
    private val chatRepository: ChatRepository
) {
    operator fun invoke() {
        chatRepository.clearHistory()
    }
}

