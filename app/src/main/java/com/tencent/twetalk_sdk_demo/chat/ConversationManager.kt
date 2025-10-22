package com.tencent.twetalk_sdk_demo.chat

import com.tencent.twetalk_sdk_demo.chat.utils.StreamingAggregator
import com.tencent.twetalk_sdk_demo.data.ChatMessage
import com.tencent.twetalk_sdk_demo.data.MessageStatus
import com.tencent.twetalk_sdk_demo.data.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

object ConversationManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 对 UI 暴露的消息列表
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    // AI 输入处理时
    private val _assistantTyping = MutableStateFlow(false)
    val assistantTyping: StateFlow<Boolean> = _assistantTyping

    // 当前机器人流式消息的拼接器
    private var currentStream: StreamingAggregator? = null

    // 生成唯一 ID
    private fun newId() = UUID.randomUUID().toString()

    fun onUserLLMText(text: String) {
        appendMessage(ChatMessage(id = newId(), messageType = MessageType.USER, content = text))
    }

    fun onBotLLMStarted() {
        _assistantTyping.value = true
    }

    fun onBotLLMText(delta: String) {
        // 处理结束后开启新消息
        if (currentStream == null) {
            val msgId = newId()
            val msg = ChatMessage(id = msgId, messageType = MessageType.BOT, content = "", status = MessageStatus.STREAMING)
            appendMessage(msg)

            currentStream = StreamingAggregator(
                scope = scope,
                onUpdate = { content ->
                    updateMessageContent(msgId, content, MessageStatus.STREAMING)
                },
                onFinish = { content, status ->
                    updateMessageContent(msgId, content, status)
                    currentStream = null
                    _assistantTyping.value = false
                }
            ).also { it.start() }
        }

        currentStream?.append(delta)
    }

    fun onBotLLMStopped() {
        if (currentStream == null) {
            _assistantTyping.value = false
        } else {
            currentStream?.finish(interrupted = false)
        }
    }

    fun onSystemMessage(text: String) {
        appendMessage(ChatMessage(id = newId(), content = text, messageType = MessageType.SYSTEM))
    }

    fun interruptAssistant() {
        // 用户打断
        currentStream?.finish(interrupted = true)
        currentStream = null
        // 通知服务端取消
        // websocket.send({"type":"cancel"}) 等
    }

    fun clearMessage() {
        _messages.value = emptyList()
    }

    private fun appendMessage(msg: ChatMessage) {
        _messages.update { it + msg }
    }

    private fun updateMessageContent(id: String, content: String, status: MessageStatus) {
        _messages.update { list ->
            list.map { if (it.id == id) it.copy(content = content, status = status) else it }
        }
    }
}
