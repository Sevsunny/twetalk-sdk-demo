package com.tencent.twetalk_sdk_demo.call

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 微信通话管理器
 * 负责 WebSocketChatActivity 和 WxCallActivity 之间的通信
 */
object WxCallManager {

    // 通话状态流
    private val _callStateFlow = MutableSharedFlow<CallStateEvent>(replay = 0, extraBufferCapacity = 1)
    val callStateFlow: SharedFlow<CallStateEvent> = _callStateFlow.asSharedFlow()

    // 通话操作流 (从 WxCallActivity 发送到 WebSocketChatActivity)
    private val _callActionFlow = MutableSharedFlow<CallActionEvent>(replay = 0, extraBufferCapacity = 1)
    val callActionFlow: SharedFlow<CallActionEvent> = _callActionFlow.asSharedFlow()

    /**
     * 更新通话状态 (从 WebSocketChatActivity 调用)
     */
    fun updateCallState(state: CallState, roomId: String? = null) {
        _callStateFlow.tryEmit(CallStateEvent(state, roomId))
    }

    /**
     * 发送通话操作 (从 WxCallActivity 调用)
     */
    fun sendCallAction(action: CallAction, roomId: String? = null) {
        _callActionFlow.tryEmit(CallActionEvent(action, roomId))
    }
}

/**
 * 通话状态事件
 */
data class CallStateEvent(
    val state: CallState,
    val roomId: String? = null
)

/**
 * 通话操作事件
 */
data class CallActionEvent(
    val action: CallAction,
    val roomId: String? = null
)

/**
 * 通话操作类型
 */
enum class CallAction {
    ANSWER,     // 接听
    REJECT,     // 拒接
    HANGUP,     // 挂断
    MUTE,       // 静音
    UNMUTE      // 解除静音
}
