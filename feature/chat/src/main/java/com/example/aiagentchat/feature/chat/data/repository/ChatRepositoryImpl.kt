package com.example.aiagentchat.feature.chat.data.repository

import com.example.aiagentchat.core.database.dao.ChatMessageDao
import com.example.aiagentchat.core.database.dao.ContextSummaryDao
import com.example.aiagentchat.feature.chat.data.mapper.toDomain
import com.example.aiagentchat.feature.chat.data.mapper.toEntity
import com.example.aiagentchat.feature.chat.domain.model.ContextSummary
import com.example.aiagentchat.feature.chat.domain.model.ContextSummary.SummaryType
import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepositoryImpl(
    private val chatMessageDao: ChatMessageDao,
    private val contextSummaryDao: ContextSummaryDao
) : ChatRepository {

    override fun getAllMessages(): Flow<List<Message>> {
        return chatMessageDao.getAllMessages().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveMessage(message: Message) {
        chatMessageDao.insertMessage(message.toEntity())
    }

    override suspend fun saveMessages(messages: List<Message>) {
        chatMessageDao.insertMessages(messages.map { it.toEntity() })
    }

    override suspend fun deleteAllMessages() {
        chatMessageDao.deleteAllMessages()
    }

    override suspend fun deleteMessage(messageId: String) {
        chatMessageDao.deleteMessage(messageId)
    }

    override suspend fun getLastMessages(isUser: Boolean, limit: Int): List<Message> {
        return chatMessageDao.getLastMessages(isUser = isUser, limit = limit).map { it.toDomain() }
    }

    override fun getContextSummaries(type: SummaryType): Flow<List<ContextSummary>> {
        return contextSummaryDao.getLatestSummaries(type.name, limit = 10).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertContextSummary(summary: ContextSummary) {
        contextSummaryDao.insertSummary(summary.toEntity())
    }

    override suspend fun trimContextSummaries(type: SummaryType, maxItems: Int) {
        val existing = contextSummaryDao.getAllByType(type.name)
        if (existing.size <= maxItems) return
        val idsToDelete = existing
            .sortedBy { it.timestamp }
            .take(existing.size - maxItems)
            .mapNotNull { entity -> entity.id.takeIf { id -> id != 0L } }
        if (idsToDelete.isNotEmpty()) {
            contextSummaryDao.deleteByIds(idsToDelete)
        }
    }

    override suspend fun deleteMessagesByIds(ids: List<String>) {
        ids.forEach { chatMessageDao.deleteMessage(it) }
    }
}

