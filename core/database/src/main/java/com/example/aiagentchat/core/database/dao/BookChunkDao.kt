package com.example.aiagentchat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.aiagentchat.core.database.entity.BookChunkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookChunkDao {
    @Query("SELECT * FROM book_chunks WHERE book_id = :bookId ORDER BY chunk_index ASC")
    suspend fun getChunksByBookId(bookId: Long): List<BookChunkEntity>
    
    @Query("SELECT * FROM book_chunks WHERE book_id = :bookId ORDER BY chunk_index ASC")
    fun getChunksByBookIdFlow(bookId: Long): Flow<List<BookChunkEntity>>
    
    @Query("SELECT * FROM book_chunks WHERE book_id IN (:bookIds) ORDER BY book_id, chunk_index ASC")
    suspend fun getChunksByBookIds(bookIds: List<Long>): List<BookChunkEntity>
    
    @Query("SELECT * FROM book_chunks WHERE chunk_id = :chunkId")
    suspend fun getChunkById(chunkId: Long): BookChunkEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: BookChunkEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<BookChunkEntity>)
    
    @Query("SELECT COUNT(*) FROM book_chunks WHERE book_id = :bookId")
    suspend fun getChunkCountForBook(bookId: Long): Int
    
    @Query("DELETE FROM book_chunks WHERE book_id = :bookId")
    suspend fun deleteChunksByBookId(bookId: Long)
    
    @Query("DELETE FROM book_chunks")
    suspend fun deleteAllChunks()
}
