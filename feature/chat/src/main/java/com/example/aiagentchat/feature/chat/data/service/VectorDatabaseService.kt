package com.example.aiagentchat.feature.chat.data.service

import android.util.Log
import com.example.aiagentchat.core.database.dao.BookChunkDao
import com.example.aiagentchat.core.database.dao.IndexedBookDao
import com.example.aiagentchat.core.database.entity.BookChunkEntity
import com.example.aiagentchat.core.database.entity.IndexedBookEntity
import com.example.aiagentchat.core.database.util.findSimilarChunks
import com.example.aiagentchat.core.database.util.BookChunkWithSimilarity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

data class MatchedChunkWithBook(
    val text: String,
    val chunkIndex: Int,
    val similarity: Float,
    val bookId: Long,
    val bookTitle: String
)

class VectorDatabaseService(
    private val indexedBookDao: IndexedBookDao,
    private val bookChunkDao: BookChunkDao
) {
    companion object {
        private const val TAG = "VectorDatabaseService"
        private const val MAX_BOOKS = 5
    }


    /**
     * Вычисляет SHA-256 хеш содержимого файла
     */
    fun calculateFileHash(fileContent: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(fileContent.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Проверяет, существует ли книга с таким хешем
     */
    suspend fun hasBook(fileHash: String): Boolean {
        return indexedBookDao.getBookByHash(fileHash) != null
    }

    /**
     * Получает книгу по хешу
     */
    suspend fun getBookByHash(fileHash: String): IndexedBookEntity? {
        return indexedBookDao.getBookByHash(fileHash)
    }

    /**
     * Получает все проиндексированные книги
     */
    fun getAllBooks(): Flow<List<IndexedBookEntity>> {
        return indexedBookDao.getAllBooks()
    }

    /**
     * Получает все проиндексированные книги синхронно
     */
    suspend fun getAllBooksSync(): List<IndexedBookEntity> {
        return indexedBookDao.getAllBooksSync()
    }

    /**
     * Проверяет, можно ли добавить еще книгу (максимум 5)
     */
    suspend fun canAddBook(): Boolean {
        val count = indexedBookDao.getBookCount()
        return count < MAX_BOOKS
    }

    /**
     * Добавляет книгу в БД
     * @return bookId новой книги или null если книга уже существует
     */
    suspend fun addBook(
        title: String,
        filePath: String?,
        fileHash: String,
        chunks: List<BookChunkEntity>
    ): Long? {
        // Проверяем, не существует ли уже книга с таким хешем
        val existingBook = indexedBookDao.getBookByHash(fileHash)
        if (existingBook != null) {
            Log.d(TAG, "Book with hash $fileHash already exists: ${existingBook.title}")
            return null
        }

        // Проверяем лимит книг
        if (!canAddBook()) {
            throw IllegalStateException("Cannot add more than $MAX_BOOKS books")
        }

        // Создаем запись о книге
        val book = IndexedBookEntity(
            title = title,
            filePath = filePath,
            fileHash = fileHash,
            chunkCount = chunks.size
        )
        val bookId = indexedBookDao.insertBook(book)

        // Сохраняем чанки
        val chunksWithBookId = chunks.map { it.copy(bookId = bookId) }
        bookChunkDao.insertChunks(chunksWithBookId)

        Log.d(TAG, "Added book: $title with ${chunks.size} chunks, bookId=$bookId")
        return bookId
    }

    /**
     * Удаляет книгу и все её чанки
     */
    suspend fun deleteBook(bookId: Long) {
        bookChunkDao.deleteChunksByBookId(bookId)
        indexedBookDao.deleteBook(bookId)
        Log.d(TAG, "Deleted book: bookId=$bookId")
    }

    /**
     * Находит похожие чанки по косинусному сходству
     * @param queryEmbedding вектор запроса (нормализованный)
     * @param bookIds список ID книг для поиска (null = все книги)
     * @param limit максимальное количество результатов
     */
    suspend fun findSimilarChunks(
        queryEmbedding: List<Float>,
        bookIds: List<Long>? = null,
        limit: Int = 10
    ): List<MatchedChunkWithBook> {
        val chunks = if (bookIds != null && bookIds.isNotEmpty()) {
            bookChunkDao.getChunksByBookIds(bookIds)
        } else {
            val allBooks = indexedBookDao.getAllBooksSync()
            if (allBooks.isEmpty()) {
                return emptyList()
            }
            val allBookIds = allBooks.map { it.bookId }
            bookChunkDao.getChunksByBookIds(allBookIds)
        }

        if (chunks.isEmpty()) {
            return emptyList()
        }

        // Вычисляем косинусное сходство
        val similarChunks = findSimilarChunks(queryEmbedding, chunks, limit)

        // Получаем информацию о книгах для каждого чанка
        val bookMap = indexedBookDao.getAllBooksSync().associateBy { it.bookId }

        return similarChunks.map { chunk ->
            val book = bookMap[chunk.bookId]
            MatchedChunkWithBook(
                text = chunk.chunkText,
                chunkIndex = chunk.chunkIndex,
                similarity = chunk.similarity,
                bookId = chunk.bookId,
                bookTitle = book?.title ?: "Unknown"
            )
        }
    }

    /**
     * Получает чанки для конкретной книги
     */
    suspend fun getChunksByBookId(bookId: Long): List<BookChunkEntity> {
        return bookChunkDao.getChunksByBookId(bookId)
    }
}

