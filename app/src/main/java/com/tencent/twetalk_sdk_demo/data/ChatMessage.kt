package com.tencent.twetalk_sdk_demo.data

import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(
    val id: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: MessageType = MessageType.USER,
    val status: MessageStatus = MessageStatus.COMPLETED
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

enum class MessageType {
    USER,
    BOT,
    SYSTEM
}

enum class MessageStatus {
    IDLE,
    STREAMING,
    COMPLETED,
    INTERRUPTED,
    ERROR
}