package com.example.aiagentchat.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.TypeConverters
import com.example.aiagentchat.core.database.ContextConverters

@Entity(tableName = "vectors")
@TypeConverters(ContextConverters::class)
data class VectorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "chunk_text")
    val chunkText: String,
    @ColumnInfo(name = "embedding")
    val embedding: List<Float>,
    @ColumnInfo(name = "source_file")
    val sourceFile: String,
    @ColumnInfo(name = "file_hash")
    val fileHash: String,
    @ColumnInfo(name = "chunk_index")
    val chunkIndex: Int,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

