package com.example.aiagentchat.feature.chat.domain.usecase

import com.example.aiagentchat.feature.chat.data.api.ChatMessageDto
import com.example.aiagentchat.feature.chat.domain.model.AiModel
import com.example.aiagentchat.feature.chat.domain.model.UserProfile
import com.example.aiagentchat.feature.chat.domain.repository.AiModelRepository
import com.example.aiagentchat.feature.chat.domain.repository.PersonalizationRepository

class PersonalizeUserUseCase(
    private val aiModelRepository: AiModelRepository,
    private val personalizationRepository: PersonalizationRepository
) {
    suspend operator fun invoke(
        userMessage: String,
        model: AiModel = AiModel.DeepSeek
    ): Result<UserProfile> {
        return try {
            val context = extractContext(userMessage, model).getOrElse {
                return Result.failure(it)
            }
            val userContext = extractUserContext(userMessage, model).getOrNull() ?: ""
            
            var userProfile = personalizationRepository.findOrCreateUserProfile(context, userContext)
            personalizationRepository.saveContext(userProfile.userId, context)
            
            val extractedName = extractUserName(userMessage, model).getOrNull()
            if (extractedName != null && userProfile.userName == null) {
                personalizationRepository.updateUserName(userProfile.userId, extractedName)
                userProfile = personalizationRepository.getUserProfile(userProfile.userId) ?: userProfile
            }
            
            Result.success(userProfile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun extractContext(message: String, model: AiModel): Result<String> {
        val prompt = """
            Извлеки основную тему из следующего сообщения пользователя.
            Верни только краткое описание темы максимум в 5 словах на русском языке.
            Примеры: "Про Эверест и восхождение", "Вопросы о программировании", "Интерес к кулинарии".
            
            Сообщение пользователя: $message
            
            Краткое описание темы (максимум 5 слов):
        """.trimIndent()

        val messages = listOf(
            ChatMessageDto(role = "user", content = prompt)
        )

        return aiModelRepository.sendMessage(model, messages).map { response ->
            response.content.trim()
                .take(100)
                .split(Regex("\\s+"))
                .take(5)
                .joinToString(" ")
        }
    }

    private suspend fun extractUserName(message: String, model: AiModel): Result<String> {
        val prompt = """
            Проанализируй следующее сообщение пользователя и определи, упоминает ли он свое имя.
            Если пользователь упоминает свое имя (например: "меня зовут Виктор", "я Виктор", "это Виктор", "Виктор здесь" и т.д.),
            верни ТОЛЬКО имя без дополнительных слов.
            Если имя не упоминается, верни пустую строку.
            
            Сообщение пользователя: $message
            
            Имя пользователя (только имя или пустая строка):
        """.trimIndent()

        val messages = listOf(
            ChatMessageDto(role = "user", content = prompt)
        )

        return aiModelRepository.sendMessage(model, messages).map { response ->
            val extracted = response.content.trim()
            if (extracted.isBlank() || extracted.length > 50 || extracted.contains("не упоминается") || extracted.contains("не указано")) {
                ""
            } else {
                extracted.split(Regex("\\s+")).firstOrNull()?.take(30) ?: ""
            }
        }
    }

    private suspend fun extractUserContext(message: String, model: AiModel): Result<String> {
        val prompt = """
            Проанализируй следующее сообщение пользователя и определи его интересы, цели, контекст деятельности.
            На основе сообщения определи, чем интересуется пользователь, что он делает, к чему готовится.
            Например: если он говорит про Эверест, вероятно он интересуется альпинизмом и готовится к восхождению.
            Если говорит про программирование, вероятно он разработчик или изучает программирование.
            
            Верни краткое описание контекста пользователя (интересы, деятельность, цели) в 1-2 предложениях на русском языке.
            Если контекст неясен, верни пустую строку.
            
            Сообщение пользователя: $message
            
            Контекст пользователя (интересы, деятельность, цели):
        """.trimIndent()

        val messages = listOf(
            ChatMessageDto(role = "user", content = prompt)
        )

        return aiModelRepository.sendMessage(model, messages).map { response ->
            val extracted = response.content.trim()
            if (extracted.isBlank() || extracted.length > 300 || 
                extracted.contains("неясен") || extracted.contains("не определен") ||
                extracted.contains("не указан")) {
                ""
            } else {
                extracted.take(300)
            }
        }
    }
}
