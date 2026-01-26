package com.example.aiagentchat.feature.chat.data.repository

import com.example.aiagentchat.core.database.dao.UserContextDao
import com.example.aiagentchat.core.database.dao.UserDao
import com.example.aiagentchat.core.database.entity.UserContextEntity
import com.example.aiagentchat.core.database.entity.UserEntity
import com.example.aiagentchat.feature.chat.data.service.McpContextService
import com.example.aiagentchat.feature.chat.domain.model.UserContext
import com.example.aiagentchat.feature.chat.domain.model.UserProfile
import com.example.aiagentchat.feature.chat.domain.repository.PersonalizationRepository
import java.util.UUID

class PersonalizationRepositoryImpl(
    private val userDao: UserDao,
    private val userContextDao: UserContextDao,
    private val mcpContextService: McpContextService
) : PersonalizationRepository {

    override suspend fun findOrCreateUserProfile(context: String, userContext: String): UserProfile {
        val matchResult = mcpContextService.findUserByContext(context) { user, normalizedContext ->
            if (user != null) {
                if (userContext.isNotEmpty()) {
                    buildPersonalizationPrompt(context, user.personalizationPrompt, user.userName, userContext)
                } else {
                    user.personalizationPrompt
                }
            } else {
                buildPersonalizationPrompt(context, null, null, userContext)
            }
        }
        
        when (matchResult.matchType) {
            McpContextService.MatchType.EXACT, McpContextService.MatchType.SIMILAR -> {
                val userProfile = matchResult.userProfile ?: return createNewUserProfile(context, userContext)
                val user = userDao.getUserById(userProfile.userId) ?: return createNewUserProfile(context, userContext)
                
                val updatedPrompt = if (userContext.isNotEmpty()) {
                    buildPersonalizationPrompt(context, user.personalizationPrompt, user.userName, userContext)
                } else {
                    user.personalizationPrompt
                }
                
                if (updatedPrompt != user.personalizationPrompt) {
                    val updatedUser = user.copy(personalizationPrompt = updatedPrompt)
                    userDao.insertUser(updatedUser)
                }
                
                val normalizedContext = normalizeContext(context)
                mcpContextService.saveContextForUser(userProfile.userId, normalizedContext)
                
                return UserProfile(
                    userId = userProfile.userId,
                    personalizationPrompt = updatedPrompt,
                    userName = userProfile.userName
                )
            }
            McpContextService.MatchType.NONE -> {
                return createNewUserProfile(context, userContext)
            }
        }
    }
    
    private suspend fun createNewUserProfile(context: String, userContext: String): UserProfile {
        val normalizedContext = normalizeContext(context)
        val newUserId = UUID.randomUUID().toString()
        val newPrompt = buildPersonalizationPrompt(context, null, null, userContext)
        val newUser = UserEntity(
            id = newUserId,
            personalizationPrompt = newPrompt,
            userName = null
        )
        userDao.insertUser(newUser)
        mcpContextService.saveContextForUser(newUserId, normalizedContext)
        return UserProfile(
            userId = newUserId,
            personalizationPrompt = newPrompt,
            userName = null
        )
    }

    override suspend fun findSimilarContexts(context: String): List<String> {
        val normalizedContext = normalizeContext(context)
        return userContextDao.findSimilarContexts("%$normalizedContext%")
    }

    override suspend fun saveContext(userId: String, context: String) {
        mcpContextService.saveContextForUser(userId, context)
    }

    override suspend fun getUserProfile(userId: String): UserProfile? {
        val user = userDao.getUserById(userId)
        return user?.let {
            UserProfile(
                userId = it.id,
                personalizationPrompt = it.personalizationPrompt,
                userName = it.userName
            )
        }
    }

    override suspend fun updateUserName(userId: String, userName: String) {
        val user = userDao.getUserById(userId)
        if (user != null) {
            val existingPrompt = user.personalizationPrompt
            val userContext = extractUserContextFromPrompt(existingPrompt)
            val updatedPrompt = buildPersonalizationPrompt("", existingPrompt, userName, userContext)
            val updatedUser = user.copy(
                userName = userName,
                personalizationPrompt = updatedPrompt
            )
            userDao.insertUser(updatedUser)
        }
    }

    private fun normalizeContext(context: String): String {
        val words = context.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(5)
        return words.joinToString(" ")
    }


    private fun buildPersonalizationPrompt(context: String, existingPrompt: String?, userName: String?, userContext: String = ""): String {
        val namePart = if (userName != null) {
            "Пользователя зовут $userName. Обращайся к нему по имени в своих ответах. "
        } else {
            "Обращайся к пользователю как \"Дружище\" в своих ответах. "
        }
        
        val contextPart = when {
            userContext.isNotEmpty() -> {
                "Контекст пользователя: $userContext. "
            }
            context.isNotEmpty() -> {
                "Пользователь интересуется темой: $context. "
            }
            else -> ""
        }
        
        val basePrompt = when {
            existingPrompt != null -> {
                val promptWithoutName = existingPrompt.replace(Regex("Пользователя зовут [^.]+\\. Обращайся к нему по имени в своих ответах\\. "), "")
                    .replace(Regex("Обращайся к пользователю как \"Дружище\" в своих ответах\\. "), "")
                val promptWithoutContext = promptWithoutName.replace(Regex("Контекст пользователя: [^.]+\\. "), "")
                val promptWithoutTopic = promptWithoutContext.replace(Regex("Пользователь интересуется темой: [^.]+\\. "), "")
                
                val updatedContext = if (userContext.isNotEmpty()) {
                    userContext
                } else if (context.isNotEmpty() && !promptWithoutTopic.contains("интересуется")) {
                    "Пользователь интересуется темой: $context"
                } else {
                    ""
                }
                
                val styleInstruction = if (userContext.isNotEmpty() || context.isNotEmpty()) {
                    " Если контекст пользователя понятен, отвечай в том же разговорном стиле и используй те же слова и терминологию, которые соответствуют его контексту. Адаптируй свою речь под его профессиональную или социальную среду."
                } else {
                    ""
                }
                "$namePart$contextPart$promptWithoutTopic$styleInstruction${if (updatedContext.isNotEmpty() && !promptWithoutTopic.contains(updatedContext)) "\n\n$updatedContext" else ""}"
            }
            else -> {
                "$namePart$contextPart" +
                        "Отвечай в дружелюбном и информативном стиле, учитывая контекст и интересы пользователя. " +
                        "Если контекст пользователя понятен (например, он альпинист, программист, заключенный, врач и т.д.), " +
                        "отвечай в том же разговорном стиле и используй те же слова и терминологию, которые соответствуют его контексту. " +
                        "Адаптируй свою речь под его профессиональную или социальную среду. " +
                        "Предлагай релевантные темы и вопросы для дальнейшего обсуждения."
            }
        }
        return basePrompt
    }
    
    private fun extractUserContextFromPrompt(prompt: String): String {
        val contextMatch = Regex("Контекст пользователя: ([^.]+)\\. ").find(prompt)
        return contextMatch?.groupValues?.get(1)?.trim() ?: ""
    }
}
