package com.example.aiagentchat.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "indexed_books")
data class IndexedBookEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "book_id")
    val bookId: Long = 0,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "file_hash")
    val fileHash: String,
    
    @ColumnInfo(name = "file_path")
    val filePath: String? = null,
    
    @ColumnInfo(name = "chunk_count")
    val chunkCount: Int = 0,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

