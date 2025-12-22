package com.example.aiagentchat.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.aiagentchat.core.database.dao.ChatMessageDao
import com.example.aiagentchat.core.database.dao.ContextSummaryDao
import com.example.aiagentchat.core.database.dao.VectorDao
import com.example.aiagentchat.core.database.entity.ChatMessageEntity
import com.example.aiagentchat.core.database.entity.ContextSummaryEntity
import com.example.aiagentchat.core.database.entity.VectorEntity

@Database(
    entities = [ChatMessageEntity::class, ContextSummaryEntity::class, VectorEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(ContextConverters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun contextSummaryDao(): ContextSummaryDao
    abstract fun vectorDao(): VectorDao
}

