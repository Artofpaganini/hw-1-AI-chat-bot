package com.example.aiagentchat.feature.chat.presentation.event

sealed class ChatEvent {
    data class SendMessage(val text: String) : ChatEvent()
    data class UpdateInputText(val text: String) : ChatEvent()
    data class SetApiKey(val apiKey: String) : ChatEvent()
    object ClearHistory : ChatEvent()
    object DismissError : ChatEvent()
}

