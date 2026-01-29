package com.example.aiagentchat.feature.chat.data.repository

import android.util.Log
import com.example.aiagentchat.feature.chat.data.api.GitHubMcpApi
import com.example.aiagentchat.feature.chat.data.api.JsonRpcRequest
import com.example.aiagentchat.feature.chat.data.api.JsonRpcResponse
import com.example.aiagentchat.feature.chat.data.service.GitFileDetector
import com.example.aiagentchat.feature.chat.data.service.TextIndexingService
import com.example.aiagentchat.feature.chat.data.service.VectorDatabaseService
import com.example.aiagentchat.feature.chat.data.api.OllamaApi
import retrofit2.Response
import java.io.File

class ReviewRepository(
    private val gitFileDetector: GitFileDetector,
    private val textIndexingService: TextIndexingService,
    private val vectorDatabaseService: VectorDatabaseService,
    private val ollamaApi: OllamaApi,
    private val githubMcpApi: GitHubMcpApi? = null
) {
    companion object {
        private const val TAG = "ReviewRepository"
    }
    
    suspend fun getChangedFiles(): Result<List<String>> {
        return gitFileDetector.getChangedFiles()
    }
    
    suspend fun getPullRequestDiff(
        owner: String,
        repo: String,
        pullNumber: Int,
        githubMcpApi: GitHubMcpApi? = this.githubMcpApi
    ): Result<String> {
        if (githubMcpApi == null) {
            return Result.failure(Exception("GitHub MCP API not initialized"))
        }
        
        return try {
            val request = JsonRpcRequest(
                id = System.currentTimeMillis().toInt(),
                method = "tools/call",
                params = mapOf(
                    "name" to "pull_request_read",
                    "arguments" to mapOf(
                        "owner" to owner,
                        "repo" to repo,
                        "pullNumber" to pullNumber,
                        "method" to "get_diff"
                    )
                )
            )
            
            val response: Response<JsonRpcResponse> = githubMcpApi.sendRequest(request)
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    Result.failure(Exception("GitHub MCP error: ${body.error.message}"))
                } else {
                    // GitHub MCP returns diff as string directly or in content field
                    val diff = when {
                        body.result?.get("content") is String -> body.result?.get("content") as String
                        body.result?.get("text") is String -> body.result?.get("text") as String
                        body.result?.get("diff") is String -> body.result?.get("diff") as String
                        body.result is Map<*, *> -> {
                            // Try to find any string value in the result
                            (body.result as Map<*, *>).values.firstOrNull { it is String } as? String ?: ""
                        }
                        else -> body.result?.toString() ?: ""
                    }
                    Result.success(diff)
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("HTTP ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting PR diff", e)
            Result.failure(e)
        }
    }
    
    suspend fun getFileContents(
        owner: String,
        repo: String,
        path: String,
        ref: String? = null,
        githubMcpApi: GitHubMcpApi? = this.githubMcpApi
    ): Result<String> {
        if (githubMcpApi == null) {
            return Result.failure(Exception("GitHub MCP API not initialized"))
        }
        
        return try {
            val arguments = mutableMapOf<String, Any>(
                "owner" to owner,
                "repo" to repo,
                "path" to path
            )
            if (ref != null) {
                arguments["ref"] = ref
            }
            
            val request = JsonRpcRequest(
                id = System.currentTimeMillis().toInt(),
                method = "tools/call",
                params = mapOf(
                    "name" to "get_file_contents",
                    "arguments" to arguments
                )
            )
            
            val response: Response<JsonRpcResponse> = githubMcpApi.sendRequest(request)
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.error != null) {
                    Result.failure(Exception("GitHub MCP error: ${body.error.message}"))
                } else {
                    // GitHub MCP returns file content, may be base64 encoded
                    val contentData = body.result?.get("content") as? String
                    val encoding = body.result?.get("encoding") as? String
                    
                    val content = when {
                        contentData != null && encoding == "base64" -> {
                            // Decode base64 content
                            try {
                                val decodedBytes = android.util.Base64.decode(contentData, android.util.Base64.DEFAULT)
                                String(decodedBytes, Charsets.UTF_8)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to decode base64 content", e)
                                contentData
                            }
                        }
                        contentData != null -> contentData
                        body.result?.get("text") is String -> body.result?.get("text") as String
                        else -> body.result?.toString() ?: ""
                    }
                    Result.success(content)
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("HTTP ${response.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file contents", e)
            Result.failure(e)
        }
    }
    
    suspend fun embedFilesForReview(filePaths: List<String>): Result<Unit> {
        return try {
            filePaths.forEach { filePath ->
                val contentResult = gitFileDetector.readFileContent(filePath)
                contentResult.onSuccess { content ->
                    val fileName = File(filePath).name
                    val needsIndexing = textIndexingService.checkIfIndexingNeeded(filePath, fileName)
                    if (needsIndexing) {
                        textIndexingService.indexFile(filePath, fileName)
                            .onFailure { error ->
                                Log.e(TAG, "Failed to index file: $filePath", error)
                            }
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Failed to read file: $filePath", error)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error embedding files for review", e)
            Result.failure(e)
        }
    }
    
    suspend fun generateReviewPrompt(
        changedFiles: List<String>,
        prDiff: String? = null
    ): String {
        val fileContents = changedFiles.mapNotNull { filePath ->
            gitFileDetector.readFileContent(filePath).getOrNull()?.let { content ->
                "File: $filePath\n```\n$content\n```"
            }
        }.joinToString("\n\n")
        
        return buildString {
            appendLine("# Code Review Request")
            appendLine()
            if (prDiff != null) {
                appendLine("## Pull Request Diff")
                appendLine("```diff")
                appendLine(prDiff)
                appendLine("```")
                appendLine()
            }
            appendLine("## Changed Files")
            appendLine(fileContents)
            appendLine()
            appendLine("## Review Guidelines")
            appendLine("Please review the code above according to Android 2025 best practices:")
            appendLine("- Code Style: Kotlin conventions, naming, formatting")
            appendLine("- Architecture: Clean Architecture, MVVM/MVI patterns")
            appendLine("- Performance: Memory leaks, coroutines usage, UI performance")
            appendLine("- Security: Data handling, permissions, API keys")
            appendLine()
            appendLine("Format your review as Markdown with:")
            appendLine("- File:line references")
            appendLine("- Severity: 🔴 Critical / 🟡 Warning / 🔵 Suggestion")
            appendLine("- Categories: Code Style, Architecture, Performance, Security")
        }
    }
}
