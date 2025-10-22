package com.tencent.twetalk_sdk_demo.chat.utils

import com.tencent.twetalk_sdk_demo.data.MessageStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StreamingAggregator(
    private val scope: CoroutineScope,
    private val onUpdate: (String) -> Unit,
    private val onFinish: (String, MessageStatus) -> Unit,
    private val uiDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val throttleMs: Long = 50 // 可选 30–80ms 节流时间
) {
    private val builder = StringBuilder()
    private val deltas = MutableSharedFlow<String>(extraBufferCapacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            // 每次有新 delta 进来，按时间窗合并后再推一次 UI
            deltas.sample(throttleMs).collect {
                val text = builder.toString()
                withContext(uiDispatcher) { onUpdate(text) }
            }
        }
    }

    fun append(delta: String) {
        builder.append(delta)
        deltas.tryEmit(delta)
    }

    fun finish(interrupted: Boolean) {
        job?.cancel()
        val finalText = builder.toString()
        scope.launch(uiDispatcher) {
            onFinish(finalText, if (interrupted) MessageStatus.INTERRUPTED else MessageStatus.COMPLETED)
        }
    }
}
