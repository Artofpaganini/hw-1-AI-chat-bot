package com.example.releaseautomation.rag

import java.io.File
import java.util.logging.Logger

data class CodeChunk(
    val filePath: String,
    val chunkText: String,
    val startLine: Int,
    val endLine: Int,
    val chunkType: ChunkType
)

enum class ChunkType {
    CLASS,
    FUNCTION,
    INTERFACE,
    OBJECT,
    COMPANION_OBJECT,
    OTHER
}

class CodeChunker {
    private val logger = Logger.getLogger(CodeChunker::class.java.name)
    
    fun chunkFile(file: File): List<CodeChunk> {
        if (!file.exists() || !file.isFile) {
            return emptyList()
        }
        
        val lines = file.readLines()
        val chunks = mutableListOf<CodeChunk>()
        
        var currentChunkStart = 0
        var currentChunkType = ChunkType.OTHER
        var braceLevel = 0
        var inChunk = false
        
        for (i in lines.indices) {
            val line = lines[i].trim()
            
            // Определяем начало нового чанка
            val chunkType = detectChunkType(line)
            if (chunkType != null && !inChunk) {
                if (currentChunkStart < i && inChunk) {
                    // Сохраняем предыдущий чанк
                    chunks.add(createChunk(file, lines, currentChunkStart, i - 1, currentChunkType))
                }
                currentChunkStart = i
                currentChunkType = chunkType
                inChunk = true
                braceLevel = 0
            }
            
            // Отслеживаем уровень вложенности
            braceLevel += line.count { it == '{' } - line.count { it == '}' }
            
            // Если достигли конца класса/функции
            if (inChunk && braceLevel == 0 && line.isNotEmpty() && !line.startsWith("//")) {
                chunks.add(createChunk(file, lines, currentChunkStart, i, currentChunkType))
                inChunk = false
            }
        }
        
        // Добавляем последний чанк если есть
        if (inChunk && currentChunkStart < lines.size) {
            chunks.add(createChunk(file, lines, currentChunkStart, lines.size - 1, currentChunkType))
        }
        
        // Если не нашли структурированные чанки, создаем один большой
        if (chunks.isEmpty() && lines.isNotEmpty()) {
            chunks.add(createChunk(file, lines, 0, lines.size - 1, ChunkType.OTHER))
        }
        
        return chunks
    }
    
    private fun detectChunkType(line: String): ChunkType? {
        val trimmed = line.trim()
        return when {
            trimmed.startsWith("class ") || trimmed.startsWith("data class ") ||
            trimmed.startsWith("sealed class ") || trimmed.startsWith("abstract class ") -> ChunkType.CLASS
            trimmed.startsWith("interface ") -> ChunkType.INTERFACE
            trimmed.startsWith("object ") && !trimmed.contains("companion") -> ChunkType.OBJECT
            trimmed.startsWith("companion object") -> ChunkType.COMPANION_OBJECT
            trimmed.startsWith("fun ") || trimmed.startsWith("suspend fun ") ||
            trimmed.startsWith("private fun ") || trimmed.startsWith("public fun ") ||
            trimmed.startsWith("internal fun ") || trimmed.startsWith("protected fun ") -> ChunkType.FUNCTION
            else -> null
        }
    }
    
    private fun createChunk(
        file: File,
        lines: List<String>,
        startLine: Int,
        endLine: Int,
        chunkType: ChunkType
    ): CodeChunk {
        val chunkText = lines.subList(startLine, endLine + 1).joinToString("\n")
        val relativePath = file.relativeTo(File(".")).path
        
        return CodeChunk(
            filePath = relativePath,
            chunkText = chunkText,
            startLine = startLine + 1, // 1-based для пользователя
            endLine = endLine + 1,
            chunkType = chunkType
        )
    }
}
