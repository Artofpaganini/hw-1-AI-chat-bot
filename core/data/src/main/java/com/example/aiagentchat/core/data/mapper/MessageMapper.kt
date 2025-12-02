package com.example.aiagentchat.core.data.mapper

import com.example.aiagentchat.core.domain.model.Message
import com.example.aiagentchat.core.domain.model.ParsedAiResponse
import com.example.aiagentchat.core.network.dto.AiResponseDataDto
import java.util.UUID

object MessageMapper {
    fun toDomain(
        text: String,
        isUser: Boolean,
        rawResponse: String? = null,
        parsedData: ParsedAiResponse? = null
    ): Message {
        return Message(
            id = UUID.randomUUID().toString(),
            text = text,
            isUser = isUser,
            timestamp = System.currentTimeMillis(),
            rawResponse = rawResponse,
            parsedData = parsedData
        )
    }

    fun toParsedAiResponse(dto: AiResponseDataDto): ParsedAiResponse {
        return ParsedAiResponse(
            title = dto.data.title,
            description = dto.data.description,
            category = dto.data.category,
            tags = dto.data.tags
        )
    }
}

