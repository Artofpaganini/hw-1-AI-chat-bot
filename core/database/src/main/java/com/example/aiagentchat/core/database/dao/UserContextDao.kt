package com.example.aiagentchat.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.aiagentchat.core.database.entity.UserContextEntity

@Dao
interface UserContextDao {
    @Query("SELECT * FROM user_contexts WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getContextsByUserId(userId: String): List<UserContextEntity>

    @Query("SELECT DISTINCT userId FROM user_contexts WHERE context LIKE :contextPattern")
    suspend fun findSimilarContexts(contextPattern: String): List<String>

    @Query("SELECT * FROM user_contexts WHERE context LIKE :contextPattern LIMIT 1")
    suspend fun findExactContext(contextPattern: String): UserContextEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContext(context: UserContextEntity)

    @Query("SELECT * FROM user_contexts")
    suspend fun getAllContexts(): List<UserContextEntity>

    @Query("DELETE FROM user_contexts")
    suspend fun deleteAllContexts()
}
