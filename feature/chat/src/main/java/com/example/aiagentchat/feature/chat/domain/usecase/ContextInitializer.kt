package com.example.aiagentchat.feature.chat.domain.usecase

import com.example.aiagentchat.feature.chat.domain.model.ContextSummary
import com.example.aiagentchat.feature.chat.domain.model.SessionContext
import com.example.aiagentchat.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.first

class ContextInitializer(
    private val chatRepository: ChatRepository
) {
    suspend fun loadLatest(): SessionContext {
        val user = chatRepository.getContextSummaries(ContextSummary.SummaryType.USER_PACK).first()
        val ai = chatRepository.getContextSummaries(ContextSummary.SummaryType.AI_PACK).first()
        return SessionContext(userSummaries = user, aiSummaries = ai)
    }
}




