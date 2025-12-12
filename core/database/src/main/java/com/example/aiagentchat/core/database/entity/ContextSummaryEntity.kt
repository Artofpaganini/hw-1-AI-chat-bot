package com.example.aiagentchat.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "context_summaries")
data class ContextSummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val summary: String,
    @ColumnInfo(name = "key_facts")
    val keyFacts: List<String>,
    val timestamp: Long
)

