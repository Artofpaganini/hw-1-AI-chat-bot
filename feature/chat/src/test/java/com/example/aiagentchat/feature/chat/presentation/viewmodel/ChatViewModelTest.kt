package com.example.aiagentchat.feature.chat.presentation.viewmodel

import com.example.aiagentchat.core.common.Result
import com.example.aiagentchat.core.domain.model.Message
import com.example.aiagentchat.core.domain.usecase.ClearHistoryUseCase
import com.example.aiagentchat.core.domain.usecase.GetApiKeyUseCase
import com.example.aiagentchat.core.domain.usecase.GetMessagesUseCase
import com.example.aiagentchat.core.domain.usecase.SendMessageUseCase
import com.example.aiagentchat.core.domain.usecase.SetApiKeyUseCase
import com.example.aiagentchat.feature.chat.presentation.event.ChatEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChatViewModelTest {
    private lateinit var sendMessageUseCase: SendMessageUseCase
    private lateinit var getMessagesUseCase: GetMessagesUseCase
    private lateinit var clearHistoryUseCase: ClearHistoryUseCase
    private lateinit var setApiKeyUseCase: SetApiKeyUseCase
    private lateinit var getApiKeyUseCase: GetApiKeyUseCase
    private lateinit var viewModel: ChatViewModel

    @BeforeEach
    fun setup() {
        sendMessageUseCase = mockk()
        getMessagesUseCase = mockk()
        clearHistoryUseCase = mockk()
        setApiKeyUseCase = mockk()
        getApiKeyUseCase = mockk()
        every { getMessagesUseCase() } returns flowOf(emptyList())
        every { getApiKeyUseCase() } returns flowOf(null)
        viewModel = ChatViewModel(
            sendMessageUseCase = sendMessageUseCase,
            getMessagesUseCase = getMessagesUseCase,
            clearHistoryUseCase = clearHistoryUseCase,
            setApiKeyUseCase = setApiKeyUseCase,
            getApiKeyUseCase = getApiKeyUseCase
        )
    }

    @Test
    fun `when send message event is triggered, should call use case`() = runTest {
        val messageText = "Test message"
        val message = Message(
            id = "1",
            text = messageText,
            isUser = true
        )
        coEvery { sendMessageUseCase(messageText) } returns flowOf(Result.Success(message))
        viewModel.handleEvent(ChatEvent.SendMessage(messageText))
        coEvery { sendMessageUseCase(messageText) }
    }

    @Test
    fun `when update input text event is triggered, should update state`() = runTest {
        val inputText = "New text"
        viewModel.handleEvent(ChatEvent.UpdateInputText(inputText))
        val states = viewModel.state.toList()
        assertTrue(states.any { it.inputText == inputText })
    }

    @Test
    fun `when set api key event is triggered, should call use case`() = runTest {
        val apiKey = "test-api-key"
        coEvery { setApiKeyUseCase(apiKey) } returns Unit
        viewModel.handleEvent(ChatEvent.SetApiKey(apiKey))
        coVerify { setApiKeyUseCase(apiKey) }
    }

    @Test
    fun `when clear history event is triggered, should call use case`() {
        viewModel.handleEvent(ChatEvent.ClearHistory)
        verify { clearHistoryUseCase() }
    }

    @Test
    fun `when dismiss error event is triggered, should clear error`() = runTest {
        viewModel.handleEvent(ChatEvent.DismissError)
        val states = viewModel.state.toList()
        assertTrue(states.any { it.error == null })
    }
}

