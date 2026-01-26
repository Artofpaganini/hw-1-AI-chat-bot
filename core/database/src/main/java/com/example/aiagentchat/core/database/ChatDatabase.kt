package com.example.aiagentchat.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.aiagentchat.core.database.dao.ChatMessageDao
import com.example.aiagentchat.core.database.dao.ContextSummaryDao
import com.example.aiagentchat.core.database.dao.UserContextDao
import com.example.aiagentchat.core.database.dao.UserDao
import com.example.aiagentchat.core.database.entity.ChatMessageEntity
import com.example.aiagentchat.core.database.entity.ContextSummaryEntity
import com.example.aiagentchat.core.database.entity.UserContextEntity
import com.example.aiagentchat.core.database.entity.UserEntity

@Database(
    entities = [
        ChatMessageEntity::class,
        ContextSummaryEntity::class,
        UserEntity::class,
        UserContextEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(ContextConverters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun contextSummaryDao(): ContextSummaryDao
    abstract fun userDao(): UserDao
    abstract fun userContextDao(): UserContextDao
}

