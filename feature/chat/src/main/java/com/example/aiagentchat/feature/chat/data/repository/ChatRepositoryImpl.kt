package com.example.aiagentchat.feature.chat.data.repository

import com.example.aiagentchat.core.database.dao.ChatMessageDao
import com.example.aiagentchat.feature.chat.data.mapper.toDomain
import com.example.aiagentchat.feature.chat.data.mapper.toEntity
import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepositoryImpl(
    private val chatMessageDao: ChatMessageDao
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
}

