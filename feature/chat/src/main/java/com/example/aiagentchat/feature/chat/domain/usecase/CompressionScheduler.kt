package com.example.aiagentchat.feature.chat.domain.usecase

import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.ContextSummary
import com.example.aiagentchat.feature.chat.domain.model.ContextSummary.SummaryType
import com.example.aiagentchat.feature.chat.domain.model.Message
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository
import com.example.aiagentchat.feature.chat.domain.repository.ChatRepository
import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CompressionScheduler(
    private val chatRepository: ChatRepository,
    private val aiModelRepository: AiModelRepository,
    private val fallbackSummarizer: FallbackSummarizer = FallbackSummarizer(),
    private val gson: Gson = Gson()
) {
    private var userCounter: Int = 0
    private var aiCounter: Int = 0

    suspend fun onMessageSaved(message: Message, modelForSummary: AiModel) {
        if (message.isUser) {
            userCounter += 1
            if (userCounter >= MESSAGE_THRESHOLD) {
                compress(isUser = true, modelForSummary)
                userCounter = 0
            }
        } else {
            aiCounter += 1
            if (aiCounter >= MESSAGE_THRESHOLD) {
                compress(isUser = false, modelForSummary)
                aiCounter = 0
            }
        }
    }

    private suspend fun compress(isUser: Boolean, modelForSummary: AiModel) {
        val messages = chatRepository.getLastMessages(isUser = isUser, limit = MESSAGE_THRESHOLD)
        if (messages.size < MESSAGE_THRESHOLD) return

        val sortedMessages = messages.sortedBy { it.timestamp }
        val role = if (isUser) "USER" else "AI"
        val prompt = buildPrompt(role, sortedMessages)
        val chatMessages = listOf(
            ChatMessageDto(role = "user", content = prompt)
        )

        val result = withContext(Dispatchers.IO) {
            aiModelRepository.sendMessage(modelForSummary, chatMessages)
        }

        val (summary, keyFacts) = result.fold(
            onSuccess = { response ->
                parseSummary(response.content).getOrElse {
                    fallbackSummarizer.summarize(sortedMessages)
                }
            },
            onFailure = {
                fallbackSummarizer.summarize(sortedMessages)
            }
        ).let { pair ->
            validate(pair.first) to pair.second.take(MAX_KEY_FACTS)
        }

        val contextSummary = ContextSummary(
            type = if (isUser) SummaryType.USER_PACK else SummaryType.AI_PACK,
            summary = summary,
            keyFacts = keyFacts,
            timestamp = System.currentTimeMillis()
        )

        chatRepository.insertContextSummary(contextSummary)
        chatRepository.trimContextSummaries(contextSummary.type, MAX_SUMMARIES_PER_ROLE)
        chatRepository.deleteMessagesByIds(sortedMessages.map { it.id })
    }

    private fun parseSummary(raw: String): Result<Pair<String, List<String>>> {
        return runCatching {
            val adapter = gson.fromJson(raw, SummaryPayload::class.java)
            adapter.summary to (adapter.keyFacts ?: emptyList())
        }
    }

    private fun validate(summary: String): String {
        if (summary.length <= MAX_SUMMARY_LENGTH) return summary
        return summary.take(MAX_SUMMARY_LENGTH) + "..."
    }

    private fun buildPrompt(role: String, messages: List<Message>): String {
        val content = messages.mapIndexed { index, message ->
            "${index + 1}. ${message.content}"
        }.joinToString(separator = "\n")
        
        return """Создай подробный summary для последних ${messages.size} сообщений [$role]. 
Важно: summary должен быть полным и содержать всю ключевую информацию из всех сообщений.
JSON формат: { "summary": "...", "key_facts": [...] }

Сообщения:
$content

Требования:
- Summary должен быть подробным и содержать всю важную информацию
- Summary может быть длинным (до 2000 символов) - это нормально
- Key_facts должны содержать самые важные факты из всех сообщений""".trimIndent()
    }

    private data class SummaryPayload(
        val summary: String,
        val keyFacts: List<String>?
    )

    companion object {
        private const val MESSAGE_THRESHOLD: Int = 10
        private const val MAX_SUMMARY_LENGTH: Int = 2000
        private const val MAX_SUMMARIES_PER_ROLE: Int = 5
        private const val MAX_KEY_FACTS: Int = 5
    }
}

