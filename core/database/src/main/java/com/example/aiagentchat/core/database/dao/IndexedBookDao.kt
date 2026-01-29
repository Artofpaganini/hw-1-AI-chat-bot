package com.example.aiagentchat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.aiagentchat.core.database.entity.IndexedBookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IndexedBookDao {
    @Query("SELECT * FROM indexed_books ORDER BY timestamp DESC")
    fun getAllBooks(): Flow<List<IndexedBookEntity>>
    
    @Query("SELECT * FROM indexed_books ORDER BY timestamp DESC")
    suspend fun getAllBooksSync(): List<IndexedBookEntity>
    
    @Query("SELECT * FROM indexed_books WHERE book_id = :bookId")
    suspend fun getBookById(bookId: Long): IndexedBookEntity?
    
    @Query("SELECT * FROM indexed_books WHERE file_hash = :fileHash")
    suspend fun getBookByHash(fileHash: String): IndexedBookEntity?
    
    @Query("SELECT COUNT(*) FROM indexed_books")
    suspend fun getBookCount(): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: IndexedBookEntity): Long
    
    @Query("UPDATE indexed_books SET chunk_count = :chunkCount WHERE book_id = :bookId")
    suspend fun updateChunkCount(bookId: Long, chunkCount: Int)
    
    @Query("DELETE FROM indexed_books WHERE book_id = :bookId")
    suspend fun deleteBook(bookId: Long)
    
    @Query("DELETE FROM indexed_books")
    suspend fun deleteAllBooks()
}

