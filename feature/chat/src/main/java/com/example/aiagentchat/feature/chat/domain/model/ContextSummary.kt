package com.example.aiagentchat.feature.chat.domain.model

data class ContextSummary(
    val type: SummaryType,
    val summary: String,
    val keyFacts: List<String>,
    val timestamp: Long
) {
    enum class SummaryType {
        USER_PACK,
        AI_PACK
    }
}

data class SessionContext(
    val userSummaries: List<ContextSummary> = emptyList(),
    val aiSummaries: List<ContextSummary> = emptyList()
) {
    val latestUser: ContextSummary?
        get() = userSummaries.maxByOrNull { it.timestamp }
    val latestAi: ContextSummary?
        get() = aiSummaries.maxByOrNull { it.timestamp }
}




