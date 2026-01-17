package com.example.releaseautomation.rag

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class IndexedChunk(
    val id: Long,
    val filePath: String,
    val chunkText: String,
    val startLine: Int,
    val endLine: Int,
    val chunkType: ChunkType,
    val embedding: FloatArray
)

class RagIndexer(private val projectRoot: File, private val indexDbPath: File) {
    private val logger = Logger.getLogger(RagIndexer::class.java.name)
    private val embedder = CodeEmbedder()
    private val chunker = CodeChunker()
    
    init {
        // Инициализируем SQLite драйвер
        Class.forName("org.sqlite.JDBC")
    }
    
    fun indexCodebase(): Int {
        val connection = getConnection()
        try {
            createTables(connection)
            
            val kotlinFiles = findKotlinFiles(projectRoot)
            logger.info("Found ${kotlinFiles.size} Kotlin files to index")
            
            var totalChunks = 0
            
            kotlinFiles.forEach { file ->
                try {
                    val chunks = chunker.chunkFile(file, projectRoot)
                    chunks.forEach { chunk ->
                        val embedding = embedder.generateEmbedding(chunk.chunkText)
                        insertChunk(connection, chunk, embedding)
                        totalChunks++
                    }
                } catch (e: Exception) {
                    logger.warning("Failed to index file ${file.absolutePath}: ${e.message}")
                }
            }
            
            logger.info("Indexed $totalChunks chunks from ${kotlinFiles.size} files")
            return totalChunks
        } finally {
            connection.close()
        }
    }
    
    fun searchContext(query: String, topK: Int = 5): List<IndexedChunk> {
        val connection = getConnection()
        try {
            val queryEmbedding = embedder.generateEmbedding(query)
            // queryEmbeddingBytes не используется, можно удалить
            
            val chunks = mutableListOf<Pair<IndexedChunk, Float>>()
            
            val statement = connection.createStatement()
            val resultSet = statement.executeQuery("""
                SELECT id, file_path, chunk_text, start_line, end_line, chunk_type, embedding
                FROM code_chunks
            """.trimIndent())
            
            while (resultSet.next()) {
                val embeddingBytes = resultSet.getBytes("embedding")
                val chunkEmbedding = embedder.toFloatArray(embeddingBytes)
                val similarity = embedder.cosineSimilarity(queryEmbedding, chunkEmbedding)
                
                val chunk = IndexedChunk(
                    id = resultSet.getLong("id"),
                    filePath = resultSet.getString("file_path"),
                    chunkText = resultSet.getString("chunk_text"),
                    startLine = resultSet.getInt("start_line"),
                    endLine = resultSet.getInt("end_line"),
                    chunkType = ChunkType.valueOf(resultSet.getString("chunk_type")),
                    embedding = chunkEmbedding
                )
                
                chunks.add(chunk to similarity)
            }
            
            resultSet.close()
            statement.close()
            
            return chunks
                .sortedByDescending { it.second }
                .take(topK)
                .map { it.first }
        } finally {
            connection.close()
        }
    }
    
    fun clearIndex() {
        val connection = getConnection()
        try {
            val statement = connection.createStatement()
            statement.execute("DELETE FROM code_chunks")
            statement.close()
        } finally {
            connection.close()
        }
    }
    
    private fun getConnection(): Connection {
        val dbFile = indexDbPath.also { it.parentFile?.mkdirs() }
        return DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
    }
    
    private fun createTables(connection: Connection) {
        val statement = connection.createStatement()
        statement.execute("""
            CREATE TABLE IF NOT EXISTS code_chunks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_path TEXT NOT NULL,
                chunk_text TEXT NOT NULL,
                start_line INTEGER NOT NULL,
                end_line INTEGER NOT NULL,
                chunk_type TEXT NOT NULL,
                embedding BLOB NOT NULL,
                created_at INTEGER DEFAULT (strftime('%s', 'now'))
            )
        """.trimIndent())
        statement.execute("CREATE INDEX IF NOT EXISTS idx_file_path ON code_chunks(file_path)")
        statement.close()
    }
    
    private fun insertChunk(connection: Connection, chunk: CodeChunk, embedding: FloatArray) {
        val statement = connection.prepareStatement("""
            INSERT INTO code_chunks (file_path, chunk_text, start_line, end_line, chunk_type, embedding)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent())
        
        statement.setString(1, chunk.filePath)
        statement.setString(2, chunk.chunkText)
        statement.setInt(3, chunk.startLine)
        statement.setInt(4, chunk.endLine)
        statement.setString(5, chunk.chunkType.name)
        statement.setBytes(6, embedder.toByteArray(embedding))
        
        statement.executeUpdate()
        statement.close()
    }
    
    private fun findKotlinFiles(directory: File): List<File> {
        val files = mutableListOf<File>()
        
        fun scanDir(dir: File) {
            if (!dir.exists() || !dir.isDirectory) return
            
            dir.listFiles()?.forEach { file ->
                when {
                    file.isDirectory -> {
                        // Пропускаем служебные директории
                        if (file.name !in listOf("build", ".git", ".gradle", ".idea", "node_modules")) {
                            scanDir(file)
                        }
                    }
                    file.isFile && file.extension == "kt" -> {
                        files.add(file)
                    }
                }
            }
        }
        
        scanDir(directory)
        return files
    }
    
}
