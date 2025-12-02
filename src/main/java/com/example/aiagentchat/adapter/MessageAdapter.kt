package com.example.aiagentchat.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.aiagentchat.R
import com.example.aiagentchat.data.Message

class MessageAdapter(private val messages: MutableList<Message>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    
    // Хранит состояние чекбокса для каждого сообщения (позиция -> состояние)
    private val formatStates = mutableMapOf<Int, Boolean>()
    
    class MessageViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val textViewSender: TextView = view.findViewById(R.id.textViewSender)
        val textViewMessage: TextView = view.findViewById(R.id.textViewMessage)
        val messageContainer: LinearLayout = view.findViewById(R.id.messageContainer)
        val checkBoxFormat: CheckBox = view.findViewById(R.id.checkBoxFormat)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val context = holder.itemView.context
        
        holder.textViewSender.text = if (message.isUser) "Вы" else "DeepSeek AI"
        
        val layoutParams = holder.messageContainer.layoutParams as FrameLayout.LayoutParams
        
        if (message.isUser) {
            // Сообщение пользователя
            layoutParams.gravity = Gravity.END
            holder.textViewMessage.setBackgroundColor(
                ContextCompat.getColor(context, R.color.message_user)
            )
            holder.textViewMessage.text = message.text
            holder.checkBoxFormat.visibility = View.GONE
        } else {
            // Сообщение AI
            layoutParams.gravity = Gravity.START
            holder.textViewMessage.setBackgroundColor(
                ContextCompat.getColor(context, R.color.message_ai)
            )
            
            // Показываем чекбокс только если есть распарсенные данные
            if (message.parsedData != null) {
                holder.checkBoxFormat.visibility = View.VISIBLE
                
                // Получаем состояние чекбокса (по умолчанию false = JSON)
                val showFormatted = formatStates[position] ?: false
                
                // Убираем слушатель перед установкой состояния
                holder.checkBoxFormat.setOnCheckedChangeListener(null)
                holder.checkBoxFormat.isChecked = showFormatted
                
                // Отображаем текст в зависимости от состояния
                holder.textViewMessage.text = if (showFormatted) {
                    message.parsedData.toFormattedText()
                } else {
                    message.parsedData.toJsonText()
                }
                
                // Устанавливаем слушатель
                holder.checkBoxFormat.setOnCheckedChangeListener { _, isChecked ->
                    formatStates[position] = isChecked
                    holder.textViewMessage.text = if (isChecked) {
                        message.parsedData.toFormattedText()
                    } else {
                        message.parsedData.toJsonText()
                    }
                }
            } else {
                // Нет распарсенных данных - показываем сырой ответ
                holder.checkBoxFormat.visibility = View.GONE
                holder.textViewMessage.text = message.rawResponse ?: message.text
            }
        }
        
        holder.messageContainer.layoutParams = layoutParams
    }
    
    override fun getItemCount(): Int = messages.size
    
    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    
    fun clearMessages() {
        messages.clear()
        formatStates.clear()
        notifyDataSetChanged()
    }
}
