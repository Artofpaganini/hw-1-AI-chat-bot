package com.example.aiagentchat.feature.chat.data.service

import com.example.aiagentchat.core.database.dao.UserContextDao
import com.example.aiagentchat.core.database.dao.UserDao
import com.example.aiagentchat.core.database.entity.UserContextEntity
import com.example.aiagentchat.core.database.entity.UserEntity
import com.example.aiagentchat.feature.chat.domain.model.UserProfile

class McpContextService(
    private val userDao: UserDao,
    private val userContextDao: UserContextDao
) {
    data class ContextMatchResult(
        val userId: String?,
        val userProfile: UserProfile?,
        val matchType: MatchType
    )

    enum class MatchType {
        EXACT,
        SIMILAR,
        NONE
    }

    suspend fun findUserByContext(
        context: String,
        buildPrompt: (UserEntity?, String) -> String
    ): ContextMatchResult {
        val normalizedContext = normalizeContext(context)
        val contextWords = normalizedContext.split(" ").filter { it.isNotBlank() }
        
        if (contextWords.isEmpty()) {
            return ContextMatchResult(null, null, MatchType.NONE)
        }
        
        val exactMatch = findExactContext(normalizedContext)
        if (exactMatch != null) {
            val user = userDao.getUserById(exactMatch.userId)
            if (user != null) {
                val prompt = buildPrompt(user, normalizedContext)
                return ContextMatchResult(
                    userId = user.id,
                    userProfile = UserProfile(
                        userId = user.id,
                        personalizationPrompt = prompt,
                        userName = user.userName
                    ),
                    matchType = MatchType.EXACT
                )
            }
        }
        
        val similarMatch = findSimilarContext(contextWords)
        if (similarMatch != null) {
            val user = userDao.getUserById(similarMatch.userId)
            if (user != null) {
                val prompt = buildPrompt(user, normalizedContext)
                return ContextMatchResult(
                    userId = user.id,
                    userProfile = UserProfile(
                        userId = user.id,
                        personalizationPrompt = prompt,
                        userName = user.userName
                    ),
                    matchType = MatchType.SIMILAR
                )
            }
        }
        
        return ContextMatchResult(null, null, MatchType.NONE)
    }

    suspend fun saveContextForUser(userId: String, context: String) {
        val normalizedContext = normalizeContext(context)
        userContextDao.insertContext(
            UserContextEntity(
                userId = userId,
                context = normalizedContext
            )
        )
    }

    private suspend fun findExactContext(normalizedContext: String): UserContextEntity? {
        return userContextDao.findExactContext("%$normalizedContext%")
    }

    private suspend fun findSimilarContext(contextWords: List<String>): UserContextEntity? {
        val allContexts = userContextDao.getAllContexts()
        var bestMatch: UserContextEntity? = null
        var maxCommonWords = 0
        
        for (contextEntity in allContexts) {
            val entityWords = contextEntity.context.lowercase()
                .split(" ")
                .filter { it.isNotBlank() }
                .toSet()
            
            val commonWords = contextWords.intersect(entityWords)
            val commonCount = commonWords.size
            
            if (commonCount > maxCommonWords && commonCount >= 1) {
                maxCommonWords = commonCount
                bestMatch = contextEntity
            }
        }
        
        return if (maxCommonWords >= 1) bestMatch else null
    }

    private fun normalizeContext(context: String): String {
        val words = context.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(5)
        return words.joinToString(" ")
    }
}
