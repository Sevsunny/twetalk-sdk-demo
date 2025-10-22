package com.tencent.twetalk_sdk_demo

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.tencent.twetalk_sdk_demo.databinding.ActivitySettingsBinding
import androidx.core.content.edit

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupToolbar()
        loadSettings()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadSettings() {
        // 加载连接参数设置
        val defaultConnection = sharedPreferences.getString(KEY_DEFAULT_CONNECTION, "WEBSOCKET")
        when (defaultConnection) {
            "WEBSOCKET" -> binding.rbDefaultWebSocket.isChecked = true
            "TRTC" -> binding.rbDefaultTRTC.isChecked = true
        }

        binding.switchAutoSave.isChecked = sharedPreferences.getBoolean(KEY_AUTO_SAVE, true)
        binding.switchAutoConnect.isChecked = sharedPreferences.getBoolean(KEY_AUTO_CONNECT, false)

        // 加载音频设置
        val defaultAudioFormat = sharedPreferences.getString(KEY_DEFAULT_AUDIO_FORMAT, "OPUS")
        when (defaultAudioFormat) {
            "OPUS" -> binding.rbDefaultOpus.isChecked = true
            "PCM" -> binding.rbDefaultPCM.isChecked = true
        }

        binding.sliderAudioQuality.value = sharedPreferences.getFloat(KEY_AUDIO_QUALITY, 2f)
        binding.switchAutoSend.isChecked = sharedPreferences.getBoolean(KEY_AUTO_SEND, true)
        binding.switchPlaySound.isChecked = sharedPreferences.getBoolean(KEY_PLAY_SOUND, true)
        binding.switchUseTRTCRecord.isChecked = sharedPreferences.getBoolean(KEY_USE_TRTC_RECORD, false)
    }

    private fun setupClickListeners() {
        // 保存设置按钮
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        // 清除历史记录按钮
        binding.btnClearHistory.setOnClickListener {
            showClearHistoryDialog()
        }

        // 导出聊天记录按钮
        binding.btnExportHistory.setOnClickListener {
            exportChatHistory()
        }

        // 音频质量滑块监听
        binding.sliderAudioQuality.addOnChangeListener { _, value, _ ->
            val qualityText = when (value.toInt()) {
                1 -> "低质量"
                2 -> "中等质量"
                3 -> "高质量"
                else -> "中等质量"
            }
            Toast.makeText(this, "音频质量: $qualityText", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings() {
        sharedPreferences.edit {
            // 保存连接参数设置
            val defaultConnection = when {
                binding.rbDefaultWebSocket.isChecked -> "WEBSOCKET"
                binding.rbDefaultTRTC.isChecked -> "TRTC"
                else -> "WEBSOCKET"
            }
            putString(KEY_DEFAULT_CONNECTION, defaultConnection)
            putBoolean(KEY_AUTO_SAVE, binding.switchAutoSave.isChecked)
            putBoolean(KEY_AUTO_CONNECT, binding.switchAutoConnect.isChecked)

            // 保存音频设置
            val defaultAudioFormat = when {
                binding.rbDefaultOpus.isChecked -> "OPUS"
                binding.rbDefaultPCM.isChecked -> "PCM"
                else -> "OPUS"
            }

            putString(KEY_DEFAULT_AUDIO_FORMAT, defaultAudioFormat)
            putFloat(KEY_AUDIO_QUALITY, binding.sliderAudioQuality.value)
            putBoolean(KEY_AUTO_SEND, binding.switchAutoSend.isChecked)
            putBoolean(KEY_PLAY_SOUND, binding.switchPlaySound.isChecked)
            putBoolean(KEY_USE_TRTC_RECORD, binding.switchUseTRTCRecord.isChecked)
        }

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
    }

    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle("清除历史记录")
            .setMessage("确定要清除所有聊天记录吗？此操作不可撤销。")
            .setPositiveButton("确定") { _, _ ->
                clearChatHistory()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearChatHistory() {
        // 这里应该实现清除聊天记录的逻辑
        // 目前只是模拟操作
        Toast.makeText(this, "聊天记录已清除", Toast.LENGTH_SHORT).show()
    }

    private fun exportChatHistory() {
        // 这里应该实现导出聊天记录的逻辑
        // 目前只是模拟操作
        Toast.makeText(this, "聊天记录导出功能开发中...", Toast.LENGTH_SHORT).show()
    }

    // 提供静态方法供其他Activity获取设置
    companion object {
        private const val PREFS_NAME = "ai_chat_settings"
        private const val KEY_DEFAULT_CONNECTION = "default_connection"
        private const val KEY_AUTO_SAVE = "auto_save"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_DEFAULT_AUDIO_FORMAT = "default_audio_format"
        private const val KEY_AUDIO_QUALITY = "audio_quality"
        private const val KEY_AUTO_SEND = "auto_send"
        private const val KEY_PLAY_SOUND = "play_sound"
        private const val KEY_USE_TRTC_RECORD = "use_trtc_record"

        fun getDefaultConnectionType(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_DEFAULT_CONNECTION, "WEBSOCKET") ?: "WEBSOCKET"
        }

        fun getDefaultAudioFormat(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_DEFAULT_AUDIO_FORMAT, "OPUS") ?: "OPUS"
        }

        fun isAutoSaveEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_AUTO_SAVE, true)
        }

        fun isAutoConnectEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_AUTO_CONNECT, false)
        }

        fun getAudioQuality(context: Context): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(KEY_AUDIO_QUALITY, 2f)
        }

        fun isAutoSendEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_AUTO_SEND, true)
        }

        fun isPlaySoundEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_PLAY_SOUND, true)
        }

        fun isTRTCRecord(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_USE_TRTC_RECORD, false)
        }
    }
}