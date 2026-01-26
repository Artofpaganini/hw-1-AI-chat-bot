package com.example.aiagentchat.feature.chat.domain.model

data class UserProfile(
    val userId: String,
    val personalizationPrompt: String,
    val userName: String? = null
)

data class UserContext(
    val id: Long = 0,
    val userId: String,
    val context: String,
    val createdAt: Long = System.currentTimeMillis()
)
