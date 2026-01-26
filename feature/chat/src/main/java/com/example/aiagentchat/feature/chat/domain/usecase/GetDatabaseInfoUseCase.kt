package com.example.aiagentchat.feature.chat.domain.usecase

import com.example.aiagentchat.core.database.dao.ChatMessageDao
import com.example.aiagentchat.core.database.dao.ContextSummaryDao
import com.example.aiagentchat.core.database.dao.UserContextDao
import com.example.aiagentchat.core.database.dao.UserDao
import kotlinx.coroutines.flow.first

class GetDatabaseInfoUseCase(
    private val chatMessageDao: ChatMessageDao,
    private val contextSummaryDao: ContextSummaryDao,
    private val userDao: UserDao,
    private val userContextDao: UserContextDao
) {
    suspend operator fun invoke(): String {
        return buildString {
            appendLine("=== Database Information ===")
            appendLine()
            
            appendLine("📊 Tables Overview:")
            appendLine()
            
            val allMessages = chatMessageDao.getAllMessages().first()
            appendLine("📝 chat_messages: ${allMessages.size} records")
            if (allMessages.isNotEmpty()) {
                appendLine("   Latest: ${allMessages.lastOrNull()?.content?.take(50)}...")
            }
            appendLine()
            
            val allUsers = userDao.getAllUsers()
            appendLine("👤 users: ${allUsers.size} records")
            allUsers.take(5).forEach { user ->
                appendLine("   - ID: ${user.id}")
                appendLine("     Prompt: ${user.personalizationPrompt.take(80)}...")
                appendLine("     Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(user.createdAt))}")
            }
            if (allUsers.size > 5) {
                appendLine("   ... and ${allUsers.size - 5} more")
            }
            appendLine()
            
            val allContexts = userContextDao.getAllContexts()
            appendLine("🔗 user_contexts: ${allContexts.size} records")
            allContexts.take(10).forEach { context ->
                appendLine("   - UserID: ${context.userId}")
                appendLine("     Context: ${context.context}")
                appendLine("     Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(context.createdAt))}")
            }
            if (allContexts.size > 10) {
                appendLine("   ... and ${allContexts.size - 10} more")
            }
            appendLine()
            
            val userSummaries = contextSummaryDao.getAllByType("USER_PACK")
            val aiSummaries = contextSummaryDao.getAllByType("AI_PACK")
            appendLine("📋 context_summaries: ${userSummaries.size + aiSummaries.size} records")
            appendLine("   - USER_PACK: ${userSummaries.size}")
            appendLine("   - AI_PACK: ${aiSummaries.size}")
            appendLine()
            
            appendLine("=== Statistics ===")
            appendLine("Total Messages: ${allMessages.size}")
            appendLine("Total Users: ${allUsers.size}")
            appendLine("Total Contexts: ${allContexts.size}")
            appendLine("Total Summaries: ${userSummaries.size + aiSummaries.size}")
            
            if (allUsers.isNotEmpty()) {
                appendLine()
                appendLine("=== User-Context Relationships ===")
                allUsers.forEach { user ->
                    val userContexts = userContextDao.getContextsByUserId(user.id)
                    if (userContexts.isNotEmpty()) {
                        appendLine("User: ${user.id}")
                        appendLine("  Contexts (${userContexts.size}):")
                        userContexts.take(3).forEach { ctx ->
                            appendLine("    - ${ctx.context}")
                        }
                        if (userContexts.size > 3) {
                            appendLine("    ... and ${userContexts.size - 3} more")
                        }
                        appendLine()
                    }
                }
            }
        }
    }
}
