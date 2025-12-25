package com.example.aiagentchat.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import com.example.aiagentchat.core.database.ContextConverters

@Entity(
    tableName = "book_chunks",
    foreignKeys = [
        ForeignKey(
            entity = IndexedBookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["book_id"]), Index(value = ["chunk_index"])]
)
@TypeConverters(ContextConverters::class)
data class BookChunkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "chunk_id")
    val chunkId: Long = 0,
    
    @ColumnInfo(name = "book_id")
    val bookId: Long,
    
    @ColumnInfo(name = "chunk_index")
    val chunkIndex: Int,
    
    @ColumnInfo(name = "chunk_text")
    val chunkText: String,
    
    @ColumnInfo(name = "embedding")
    val embedding: List<Float>,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

