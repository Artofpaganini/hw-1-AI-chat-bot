package com.example.aiagentchat.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.aiagentchat.R
import com.example.aiagentchat.data.Message

class MessageAdapter(private val messages: MutableList<Message>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    
    class MessageViewHolder(val view: android.view.View) : RecyclerView.ViewHolder(view) {
        val textViewSender: TextView = view.findViewById(R.id.textViewSender)
        val textViewMessage: TextView = view.findViewById(R.id.textViewMessage)
        val messageContainer: LinearLayout = view.findViewById(R.id.messageContainer)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val context = holder.itemView.context
        
        holder.textViewSender.text = if (message.isUser) "Вы" else "AI Агент"
        holder.textViewMessage.text = message.text
        
        // Настраиваем внешний вид в зависимости от отправителя
        val layoutParams = holder.messageContainer.layoutParams as FrameLayout.LayoutParams
        if (message.isUser) {
            layoutParams.gravity = Gravity.END
            holder.textViewMessage.setBackgroundColor(
                ContextCompat.getColor(context, R.color.message_user)
            )
        } else {
            layoutParams.gravity = Gravity.START
            holder.textViewMessage.setBackgroundColor(
                ContextCompat.getColor(context, R.color.message_ai)
            )
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
        notifyDataSetChanged()
    }
}

