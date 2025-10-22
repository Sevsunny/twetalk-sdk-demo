package com.tencent.twetalk_sdk_demo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tencent.twetalk_sdk_demo.R
import com.tencent.twetalk_sdk_demo.data.ChatSession
import com.tencent.twetalk_sdk_demo.databinding.ItemChatSessionBinding

class ChatSessionAdapter(
    private val onItemClick: (ChatSession) -> Unit,
    private val onDeleteClick: (ChatSession) -> Unit
) : ListAdapter<ChatSession, ChatSessionAdapter.SessionViewHolder>(SessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemChatSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SessionViewHolder(binding, onItemClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SessionViewHolder(
        private val binding: ItemChatSessionBinding,
        private val onItemClick: (ChatSession) -> Unit,
        private val onDeleteClick: (ChatSession) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: ChatSession) {
            // 设置连接类型图标和文本
            when (session.connectionType) {
                "WEBSOCKET" -> {
                    binding.ivConnectionType.setImageResource(R.drawable.ic_websocket)
                    binding.tvConnectionType.text = "WebSocket"
                }
                "TRTC" -> {
                    binding.ivConnectionType.setImageResource(R.drawable.ic_trtc)
                    binding.tvConnectionType.text = "TRTC"
                }
            }

            // 设置会话信息
            binding.tvTitle.text = session.title
            binding.tvTime.text = getDisplayTime(session)
            binding.tvLastMessage.text = session.lastMessage.ifEmpty { "暂无消息" }
            binding.tvMessageCount.text = "${session.messageCount}条消息"
            binding.tvDuration.text = session.getDuration()
            binding.chipAudioFormat.text = session.audioFormat

            // 设置点击事件
            binding.root.setOnClickListener { onItemClick(session) }
            binding.btnDelete.setOnClickListener { onDeleteClick(session) }
        }

        private fun getDisplayTime(session: ChatSession): String {
            return when {
                session.isToday() -> {
                    val time = session.getFormattedStartTime()
                    "今天 ${time.substring(11)}"
                }
                session.isYesterday() -> {
                    val time = session.getFormattedStartTime()
                    "昨天 ${time.substring(11)}"
                }
                else -> session.getFormattedStartTime()
            }
        }
    }

    private class SessionDiffCallback : DiffUtil.ItemCallback<ChatSession>() {
        override fun areItemsTheSame(oldItem: ChatSession, newItem: ChatSession): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatSession, newItem: ChatSession): Boolean {
            return oldItem == newItem
        }
    }
}