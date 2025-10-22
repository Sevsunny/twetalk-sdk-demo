package com.tencent.twetalk_sdk_demo.data

import java.text.SimpleDateFormat
import java.util.*

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val connectionType: String, // WEBSOCKET or TRTC
    val audioFormat: String, // OPUS or PCM
    val startTime: Long,
    val endTime: Long? = null,
    val messageCount: Int = 0,
    val lastMessage: String = "",
    val summary: String = ""
) {
    fun getFormattedStartTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(startTime))
    }

    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(startTime))
    }

    fun getDuration(): String {
        if (endTime == null) return "进行中"
        val duration = (endTime - startTime) / 1000 // 秒
        val minutes = duration / 60
        val seconds = duration % 60
        return "${minutes}分${seconds}秒"
    }

    fun isToday(): Boolean {
        val today = Calendar.getInstance()
        val sessionDate = Calendar.getInstance().apply { timeInMillis = startTime }
        return today.get(Calendar.YEAR) == sessionDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == sessionDate.get(Calendar.DAY_OF_YEAR)
    }

    fun isYesterday(): Boolean {
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val sessionDate = Calendar.getInstance().apply { timeInMillis = startTime }
        return yesterday.get(Calendar.YEAR) == sessionDate.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == sessionDate.get(Calendar.DAY_OF_YEAR)
    }
}