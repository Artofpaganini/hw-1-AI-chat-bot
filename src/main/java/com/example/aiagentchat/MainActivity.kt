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
import com.example.aiagentchat.data.Message
import com.example.aiagentchat.databinding.ActivityMainBinding
import com.example.aiagentchat.repository.ChatRepository
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var messageAdapter: MessageAdapter
    private var chatRepository: ChatRepository? = null

    private var apiKey: String? = "sk-d06b698223034c60a9cdb3d7bc8fab15"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
        initializeRepository()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                showApiKeyDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initializeRepository() {
        if (apiKey.isNullOrEmpty()) {
            showApiKeyDialog()
            return
        }
        chatRepository = ChatRepository(apiKey)
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
            Toast.makeText(this, "API ключ не настроен", Toast.LENGTH_SHORT).show()
            showApiKeyDialog()
            return
        }

        val userMessage = Message(text = messageText, isUser = true)
        messageAdapter.addMessage(userMessage)
        binding.recyclerViewMessages.smoothScrollToPosition(messageAdapter.itemCount - 1)

        binding.editTextMessage.text.clear()
        showLoading(true)

        lifecycleScope.launch {
            val result = repository.sendMessage(messageText)

            showLoading(false)

            result
                .onSuccess { response ->
                    val displayText = response.parsedData?.toJsonText() ?: response.rawResponse
                    val aiMessage = Message(
                        text = displayText,
                        isUser = false,
                        rawResponse = response.rawResponse,
                        parsedData = response.parsedData
                    )
                    messageAdapter.addMessage(aiMessage)
                    binding.recyclerViewMessages.smoothScrollToPosition(messageAdapter.itemCount - 1)
                }
                .onFailure { error ->
                    Toast.makeText(
                        this@MainActivity,
                        "${getString(R.string.error_message)}: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.buttonSend.isEnabled = !show
        binding.editTextMessage.isEnabled = !show
    }

    private fun showApiKeyDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Введите ваш DeepSeek API ключ"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        if (!apiKey.isNullOrEmpty()) {
            input.setText(apiKey)
        }

        AlertDialog.Builder(this)
            .setTitle("Настройка API ключа")
            .setMessage(
                "Для работы необходим DeepSeek API ключ.\n\n" +
                "Получите ключ на:\nhttps://platform.deepseek.com/"
            )
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotEmpty()) {
                    apiKey = key
                    initializeRepository()
                    Toast.makeText(this, "API ключ сохранен", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "API ключ не может быть пустым", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
