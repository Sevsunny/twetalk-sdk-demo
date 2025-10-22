package com.tencent.twetalk_sdk_demo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tencent.twetalk_sdk_demo.data.ChatMessage
import com.tencent.twetalk_sdk_demo.data.MessageStatus
import com.tencent.twetalk_sdk_demo.data.MessageType
import com.tencent.twetalk_sdk_demo.databinding.ItemChatMessageBinding

class ChatMessageAdapter : ListAdapter<ChatMessage, ChatMessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(private val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            with(binding) {
                // 隐藏所有布局
                layoutUserMessage.visibility = View.GONE
                layoutAiMessage.visibility = View.GONE
                layoutSystemMessage.visibility = View.GONE

                when (message.messageType) {
                    MessageType.SYSTEM -> {
                        layoutSystemMessage.visibility = View.VISIBLE
                        tvSystemMessage.text = message.content
                    }

                    MessageType.USER -> {
                        layoutUserMessage.visibility = View.VISIBLE
                        tvUserMessage.text = message.content
                        tvUserTime.text = message.getFormattedTime()
                    }

                    MessageType.BOT -> {
                        layoutAiMessage.visibility = View.VISIBLE
                        val typingIndicator = if (message.status == MessageStatus.STREAMING) " ▋" else ""
                        tvAiMessage.text = message.content + typingIndicator
                        tvAiTime.text = message.getFormattedTime()
                    }
                }
            }
        }
    }

    private class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.content == newItem.content && oldItem.status == newItem.status && oldItem.messageType == newItem.messageType
        }
    }
}