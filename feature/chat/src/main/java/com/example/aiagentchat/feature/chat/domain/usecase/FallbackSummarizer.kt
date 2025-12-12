package com.example.aiagentchat.feature.chat.domain.usecase

import com.example.aiagentchat.feature.chat.domain.model.Message

class FallbackSummarizer {
    fun summarize(messages: List<Message>): Pair<String, List<String>> {
        val concatenated = messages.mapIndexed { index, message ->
            "${index + 1}. ${message.content}"
        }.joinToString(separator = "\n")
        
        val summary = if (concatenated.length > 2000) {
            concatenated.take(2000) + "..."
        } else {
            concatenated
        }
        
        val keyFacts = messages
            .take(5)
            .map { it.content.take(100) }
            .filter { it.isNotBlank() }
        
        return summary to keyFacts
    }
}

