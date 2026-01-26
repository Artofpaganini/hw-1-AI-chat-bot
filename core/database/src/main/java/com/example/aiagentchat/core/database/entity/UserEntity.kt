package com.example.aiagentchat.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val personalizationPrompt: String,
    val userName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
