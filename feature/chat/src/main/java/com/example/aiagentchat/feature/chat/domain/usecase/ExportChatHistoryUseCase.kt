package com.example.aiagentchat.feature.chat.domain.usecase

import com.example.aiagentchat.core.common.toon.ToonEncoder
import com.example.aiagentchat.feature.chat.domain.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportChatHistoryUseCase {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    operator fun invoke(
        messages: List<Message>,
        comparison: MetricsComparison? = null
    ): String {
        val data = mutableMapOf<String, Any?>(
            "exportedAt" to dateFormat.format(Date()),
            "totalMessages" to messages.size
        )
        
        if (messages.isNotEmpty()) {
            val messagesData = messages.map { msg ->
                mutableMapOf<String, Any?>(
                    "id" to msg.id,
                    "content" to msg.content,
                    "isUser" to msg.isUser,
                    "model" to (msg.model?.displayName ?: ""),
                    "timestamp" to msg.timestamp
                ).apply {
                    msg.metrics?.let { metrics ->
                        put("responseTimeMs", metrics.responseTimeMs)
                        put("inputTokens", metrics.inputTokens)
                        put("outputTokens", metrics.outputTokens)
                        put("costUsd", metrics.costUsd)
                    }
                }
            }
            data["messages"] = messagesData
        }
        
        comparison?.let { comp ->
            if (comp.isComplete) {
                val comparisonData = mutableMapOf<String, Any?>(
                    "prompt" to comp.prompt
                )
                comp.deepSeekMessage?.let { message ->
                    comparisonData["deepSeekMessage"] = mapOf(
                        "model" to (message.model?.displayName ?: ""),
                        "content" to message.content,
                        "responseTimeMs" to (message.metrics?.responseTimeMs ?: 0),
                        "inputTokens" to (message.metrics?.inputTokens ?: 0),
                        "outputTokens" to (message.metrics?.outputTokens ?: 0),
                        "costUsd" to (message.metrics?.costUsd ?: 0.0)
                    )
                }
                comp.timeDifferenceMs?.let { comparisonData["timeDifferenceMs"] = it }
                comp.costDifferenceUsd?.let { comparisonData["costDifferenceUsd"] = it }
                comp.tokensDifference?.let { comparisonData["tokensDifference"] = it }
                data["comparison"] = comparisonData
            }
        }
        
        return ToonEncoder.encode(data)
    }
}

