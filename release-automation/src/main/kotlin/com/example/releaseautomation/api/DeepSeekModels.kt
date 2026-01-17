package com.example.releaseautomation.api

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String = "deepseek-chat",
    val messages: List<Message>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 4000
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val object_field: String? = null,
    val created: Long? = null,
    val model: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int? = null,
    val message: Message? = null,
    val finish_reason: String? = null
)

@Serializable
data class Usage(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null
)

@Serializable
data class ReleaseAnalysis(
    val versionBump: String, // "major", "minor", "patch"
    val releaseNotesRu: String,
    val releaseNotesEn: String,
    val whatsNewRu: String, // max 500 chars
    val whatsNewEn: String, // max 500 chars
    val breakingChanges: List<String>? = null,
    val changelog: String,
    val userImpact: String, // "low", "medium", "high"
    val summary: String
)

@Serializable
data class CodeReviewResult(
    val issues: List<ReviewIssue>
)

@Serializable
data class ReviewIssue(
    val severity: String, // "info", "warning", "error"
    val filePath: String,
    val lineNumber: Int? = null,
    val message: String,
    val suggestion: String? = null
)
