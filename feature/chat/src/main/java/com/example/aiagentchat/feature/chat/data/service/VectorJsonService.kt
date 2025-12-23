package com.example.aiagentchat.feature.chat.data.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

data class VectorChunk(
    val text: String,
    val embedding: List<Float>,
    val chunkIndex: Int
)

data class IndexedDocument(
    val fileName: String,
    val fileHash: String,
    val chunks: List<VectorChunk>,
    val indexedAt: Long = System.currentTimeMillis()
)

data class VectorIndexJson(
    val documents: List<IndexedDocument> = emptyList(),
    val lastQuery: String? = null,
    val lastQueryEmbedding: List<Float>? = null,
    val lastQueryTime: Long? = null,
    val queryHistory: List<QueryRecord> = emptyList()
)

data class QueryRecord(
    val query: String,
    val queryEmbedding: List<Float>,
    val timestamp: Long = System.currentTimeMillis(),
    val matchedChunks: List<MatchedChunk> = emptyList()
)

data class MatchedChunk(
    val text: String,
    val chunkIndex: Int,
    val similarity: Float
)

class VectorJsonService(
    private val context: android.content.Context
) {
    companion object {
        private const val TAG = "VectorJsonService"
        private const val JSON_FILE_NAME = "vector_index.json"
    }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private fun getJsonFile(): File {
        val filesDir = context.filesDir
        return File(filesDir, JSON_FILE_NAME)
    }

    fun loadVectorIndex(): VectorIndexJson {
        return try {
            val jsonFile = getJsonFile()
            if (!jsonFile.exists()) {
                Log.d(TAG, "JSON file does not exist, creating new index")
                return VectorIndexJson()
            }

            val jsonContent = jsonFile.readText()
            val index = gson.fromJson(jsonContent, VectorIndexJson::class.java)
            Log.d(TAG, "Loaded vector index: ${index.documents.size} documents, ${index.queryHistory.size} queries")
            index
        } catch (e: Exception) {
            Log.e(TAG, "Error loading vector index", e)
            VectorIndexJson()
        }
    }

    fun saveVectorIndex(index: VectorIndexJson) {
        try {
            val jsonFile = getJsonFile()
            val jsonContent = gson.toJson(index)
            jsonFile.writeText(jsonContent)
            Log.d(TAG, "Saved vector index to: ${jsonFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving vector index", e)
            throw e
        }
    }

    fun addDocument(document: IndexedDocument) {
        val currentIndex = loadVectorIndex()
        val updatedDocuments = currentIndex.documents.filter { it.fileName != document.fileName } + document
        val updatedIndex = currentIndex.copy(documents = updatedDocuments)
        saveVectorIndex(updatedIndex)
        Log.d(TAG, "Added document: ${document.fileName} with ${document.chunks.size} chunks")
    }

    fun updateQuery(query: String, queryEmbedding: List<Float>, matchedChunks: List<MatchedChunk>) {
        val currentIndex = loadVectorIndex()
        val queryRecord = QueryRecord(
            query = query,
            queryEmbedding = queryEmbedding,
            matchedChunks = matchedChunks
        )
        val updatedQueryHistory = (currentIndex.queryHistory + queryRecord).takeLast(100) // Keep last 100 queries
        val updatedIndex = currentIndex.copy(
            lastQuery = query,
            lastQueryEmbedding = queryEmbedding,
            lastQueryTime = System.currentTimeMillis(),
            queryHistory = updatedQueryHistory
        )
        saveVectorIndex(updatedIndex)
        Log.d(TAG, "Updated query: $query with ${matchedChunks.size} matched chunks")
    }

    fun getJsonContent(): String {
        return try {
            val jsonFile = getJsonFile()
            if (!jsonFile.exists()) {
                return "{}"
            }
            jsonFile.readText()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading JSON content", e)
            "{}"
        }
    }

    fun getDocumentByFileName(fileName: String): IndexedDocument? {
        val index = loadVectorIndex()
        return index.documents.find { it.fileName == fileName }
    }

    fun hasDocument(fileName: String, fileHash: String): Boolean {
        val document = getDocumentByFileName(fileName)
        return document != null && document.fileHash == fileHash
    }
}


