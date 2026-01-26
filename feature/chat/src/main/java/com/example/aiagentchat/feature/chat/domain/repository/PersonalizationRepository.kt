package com.example.aiagentchat.feature.chat.domain.repository

import com.example.aiagentchat.feature.chat.domain.model.UserContext
import com.example.aiagentchat.feature.chat.domain.model.UserProfile

interface PersonalizationRepository {
    suspend fun findOrCreateUserProfile(context: String, userContext: String = ""): UserProfile
    suspend fun findSimilarContexts(context: String): List<String>
    suspend fun saveContext(userId: String, context: String)
    suspend fun getUserProfile(userId: String): UserProfile?
    suspend fun updateUserName(userId: String, userName: String)
}
