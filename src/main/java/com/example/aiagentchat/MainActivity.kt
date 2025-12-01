package com.example.aiagentchat

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aiagentchat.adapter.MessageAdapter
import com.example.aiagentchat.api.ApiProvider
import com.example.aiagentchat.data.Message
import com.example.aiagentchat.databinding.ActivityMainBinding
import com.example.aiagentchat.repository.ChatRepository
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var messageAdapter: MessageAdapter
    private var chatRepository: ChatRepository? = null

    private var currentProvider: ApiProvider = ApiProvider.DEEPSEEK
    private var apiKey: String? = "sk-d06b698223034c60a9cdb3d7bc8fab15"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()

        // Инициализируем с бесплатным провайдером по умолчанию
        initializeRepository()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                showProviderSelectionDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initializeRepository() {
        chatRepository =
                when (currentProvider) {
                    ApiProvider.QWEN -> {
                        if (apiKey == null || apiKey!!.isEmpty()) {
                            showApiKeyDialog()
                            return
                        }
                        ChatRepository(currentProvider, apiKey)
                    }
                    ApiProvider.DEEPSEEK -> {
                        if (apiKey == null || apiKey!!.isEmpty()) {
                            showApiKeyDialog()
                            return
                        }
                        ChatRepository(currentProvider, apiKey)
                    }
                    ApiProvider.GROQ -> {
                        if (apiKey == null || apiKey!!.isEmpty()) {
                            showApiKeyDialog()
                            return
                        }
                        ChatRepository(currentProvider, apiKey)
                    }
                    ApiProvider.OPENAI -> {
                        if (apiKey == null) {
                            showApiKeyDialog()
                            return
                        }
                        ChatRepository(currentProvider, apiKey)
                    }
                    ApiProvider.HUGGINGFACE -> {
                        ChatRepository(currentProvider, null)
                    }
                    ApiProvider.OLLAMA -> {
                        ChatRepository(currentProvider, null)
                    }
                }
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(mutableListOf())
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
            adapter = messageAdapter
        }
    }

    private fun setupClickListeners() {
        binding.buttonSend.setOnClickListener { sendMessage() }

        // Отправка по Enter (опционально)
        binding.editTextMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
    }

    private fun sendMessage() {
        val messageText = binding.editTextMessage.text.toString().trim()

        if (messageText.isEmpty()) {
            Toast.makeText(this, getString(R.string.empty_message), Toast.LENGTH_SHORT).show()
            return
        }

        val repository = chatRepository
        if (repository == null) {
            Toast.makeText(this, "Провайдер не настроен", Toast.LENGTH_SHORT).show()
            showProviderSelectionDialog()
            return
        }

        // Добавляем сообщение пользователя в чат
        val userMessage = Message(messageText, isUser = true)
        messageAdapter.addMessage(userMessage)
        binding.recyclerViewMessages.smoothScrollToPosition(messageAdapter.itemCount - 1)

        // Очищаем поле ввода
        binding.editTextMessage.text.clear()

        // Показываем индикатор загрузки
        showLoading(true)

        // Отправляем запрос
        lifecycleScope.launch {
            val result = repository.sendMessage(messageText)

            showLoading(false)

            result
                    .onSuccess { response ->
                        val aiMessage = Message(response, isUser = false)
                        messageAdapter.addMessage(aiMessage)
                        binding.recyclerViewMessages.smoothScrollToPosition(
                                messageAdapter.itemCount - 1
                        )
                    }
                    .onFailure { error ->
                        Toast.makeText(
                                        this@MainActivity,
                                        "${getString(R.string.error_message)}: ${error.message}",
                                        Toast.LENGTH_LONG
                                )
                                .show()
                    }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.buttonSend.isEnabled = !show
        binding.editTextMessage.isEnabled = !show
    }

    private fun showProviderSelectionDialog() {
        val providers = ApiProvider.values()
        val providerNames = providers.map { it.displayName }.toTypedArray()
        val currentIndex = providers.indexOf(currentProvider)

        AlertDialog.Builder(this)
                .setTitle("Выберите провайдера AI")
                .setSingleChoiceItems(providerNames, currentIndex) { dialog, which ->
                    val selectedProvider = providers[which]
                    if (selectedProvider != currentProvider) {
                        currentProvider = selectedProvider
                        messageAdapter.clearMessages()

                        if ((selectedProvider == ApiProvider.QWEN ||
                                        selectedProvider == ApiProvider.DEEPSEEK ||
                                        selectedProvider == ApiProvider.GROQ ||
                                        selectedProvider == ApiProvider.OPENAI) &&
                                        (apiKey == null || apiKey!!.isEmpty())
                        ) {
                            dialog.dismiss()
                            showApiKeyDialog()
                        } else {
                            initializeRepository()
                            dialog.dismiss()
                            Toast.makeText(
                                            this,
                                            "Выбран провайдер: ${selectedProvider.displayName}",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show()
                        }
                    } else {
                        dialog.dismiss()
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
    }

    private fun showApiKeyDialog() {
        val input = android.widget.EditText(this)
        val providerName =
                when (currentProvider) {
                    ApiProvider.QWEN -> "Qwen (Together AI)"
                    ApiProvider.DEEPSEEK -> "DeepSeek"
                    ApiProvider.GROQ -> "Groq"
                    else -> "OpenAI"
                }
        val apiUrl =
                when (currentProvider) {
                    ApiProvider.QWEN -> "https://api.together.xyz/"
                    ApiProvider.DEEPSEEK -> "https://platform.deepseek.com/"
                    ApiProvider.GROQ -> "https://console.groq.com/"
                    else -> "https://platform.openai.com/api-keys"
                }

        input.hint = "Введите ваш $providerName API ключ"
        input.inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        // Если ключ уже установлен, показываем его
        if (apiKey != null && apiKey!!.isNotEmpty()) {
            input.setText(apiKey)
        }

        AlertDialog.Builder(this)
                .setTitle("Настройка API ключа")
                .setMessage(
                        "Для работы с $providerName необходим API ключ.\n\nВы можете получить БЕСПЛАТНЫЙ ключ на:\n$apiUrl\n\nDeepSeek и Groq предоставляют бесплатный доступ!"
                )
                .setView(input)
                .setPositiveButton("Сохранить") { _, _ ->
                    val key = input.text.toString().trim()
                    if (key.isNotEmpty()) {
                        apiKey = key
                        initializeRepository()
                        Toast.makeText(this, "API ключ сохранен", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "API ключ не может быть пустым", Toast.LENGTH_SHORT)
                                .show()
                        showProviderSelectionDialog()
                    }
                }
                .setNegativeButton("Выбрать другой провайдер") { _, _ ->
                    showProviderSelectionDialog()
                }
                .setCancelable(false)
                .show()
    }
}
