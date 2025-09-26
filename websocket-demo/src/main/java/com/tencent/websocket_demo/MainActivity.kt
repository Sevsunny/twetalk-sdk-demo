package com.tencent.websocket_demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONException
import com.tencent.websocket_demo.audio.MicRecorder
import com.tencent.websocket_demo.audio.RemotePcmPlayer
import com.tencent.twetalk.core.ConnectionState
import com.tencent.twetalk.core.DefaultTWeTalkClient
import com.tencent.twetalk.core.TWeTalkClient
import com.tencent.twetalk.core.TWeTalkClientListener
import com.tencent.twetalk.core.TWeTalkConfig
import com.tencent.twetalk.metrics.MetricEvent
import com.tencent.twetalk.protocol.FrameProcessor
import com.tencent.twetalk.protocol.TWeTalkMessage
import com.tencent.websocket_demo.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val sessionId = "demo-" + System.currentTimeMillis()
    private var mic: MicRecorder? = null
    private var connected = false
    private var sampleRate = 16000
    private val remotePlayer = RemotePcmPlayer()
    private var client: TWeTalkClient? = null

    private val config: TWeTalkConfig by lazy {
        val authConfig = TWeTalkConfig.AuthConfig(
            "<Your SecretId>",
            "<Your SecretKey>",
            "<Your ProductId>", "<Your DeviceName>", "pcm")

        TWeTalkConfig.builder()
            .authConfig(authConfig)
            .build()
    }

    companion object {
        private val TAG = MainActivity::class.simpleName
    }

    private val reqPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startConnection()
        else toast("麦克风权限被拒绝")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnConnect.setOnClickListener {
            if (!connected) ensureMicPermissionAndConnect()
            else stopConnection()
        }

        client = DefaultTWeTalkClient(config)

        client?.addListener(object : TWeTalkClientListener {
            override fun onStateChanged(state: ConnectionState?) {
                appendLog("onStateChanged: $state")

                lifecycleScope.launch {
                    if (state == ConnectionState.CONNECTED) {
                        connected = true
                        binding.btnConnect.text = "Disconnect"
                        appendLog("Connected. Starting mic…")
                        startMic()
                    } else if (state == ConnectionState.CLOSED) {
                        connected = false
                        binding.btnConnect.text = "Connect"
                        stopMic()
                        appendLog("WebSocket closed")
                    }
                }
            }

            override fun onRecvMessage(message: TWeTalkMessage) {
                handleMessage(message)
            }

            override fun onMetrics(metrics: MetricEvent?) {
                appendLog("onMetrics：${metrics?.type}")
            }

            override fun onError(error: Throwable?) {
                appendLog("onError: ${error?.message}")

                lifecycleScope.launch {
                    connected = false
                    binding.btnConnect.text = "Connect"
                    stopMic()
                }
            }
        })
    }

    private fun ensureMicPermissionAndConnect() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startConnection()
        else reqPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startConnection() {
        client?.connect()
    }

    private fun stopConnection() {
        client?.disconnect()
        stopMic()
        connected = false
        binding.btnConnect.text = "Connect"
        appendLog("Disconnected by user")
    }

    private fun startMic() {
        if (mic != null) return
        mic = MicRecorder(sampleRate = 16000, chunkMs = 20) { bytes, startTime ->
            val audioFrame = FrameProcessor.buildAudioRawFrame(bytes, startTime)
            client?.sendDirectly(audioFrame)
        }.also { it.start() }
    }

    private fun stopMic() {
        mic?.stop()
        mic = null
    }

    private fun appendLog(msg: String) {
        lifecycleScope.launch {
            val text = binding.tvLog.text.toString()
            binding.tvLog.text = if (text.isEmpty()) msg else "$text\n\n$msg"
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun handleMessage(message: TWeTalkMessage) {
        when (message.type) {
            TWeTalkMessage.TWeTalkMessageType.AUDIO_DATA -> {
                if (message.data is TWeTalkMessage.AudioMessage) {
                    val audioMsg = message.data as TWeTalkMessage.AudioMessage
                    val sr = if (audioMsg.sampleRate > 0) audioMsg.sampleRate else sampleRate
                    val ch = if (audioMsg.numChannels > 0) audioMsg.numChannels else 1
                    remotePlayer.play(audioMsg.audio, sr, ch)
                }
            }

            TWeTalkMessage.TWeTalkMessageType.BOT_READY -> {}
            TWeTalkMessage.TWeTalkMessageType.ERROR -> {}

            TWeTalkMessage.TWeTalkMessageType.BOT_TRANSCRIPTION,
            TWeTalkMessage.TWeTalkMessageType.USER_TRANSCRIPTION -> {
                if (message.data is String) {
                    try {
                        val jsonData = JSON.parseObject(message.data as String)
                        val text = jsonData.getString("text")

                        if (message.type.equals(TWeTalkMessage.TWeTalkMessageType.BOT_TRANSCRIPTION)) {
                            appendLog("${message.type.value}, text: \n$text")
                        } else {
                            val userId = jsonData.getString("user_id")
                            val timestamp = jsonData.getString("timestamp")
                            val final = jsonData.getBoolean("final")

                            appendLog("${message.type.value}, user_id: $userId, timestamp: $timestamp, " +
                                    "final: $final, text: \n$text")
                        }

                    } catch (e: JSONException) {
                        Log.e(TAG, "handleMessage: unknown json data: ${message.data as String}, error msg: ${e.message}")
                    }
                }
            }

            // 其余消息根据情况处理
            TWeTalkMessage.TWeTalkMessageType.USER_STARTED_SPEAKING,
            TWeTalkMessage.TWeTalkMessageType.USER_STOPPED_SPEAKING,
            TWeTalkMessage.TWeTalkMessageType.BOT_STARTED_SPEAKING,
            TWeTalkMessage.TWeTalkMessageType.BOT_STOPPED_SPEAKING,
            TWeTalkMessage.TWeTalkMessageType.USER_LLM_TEXT,
            TWeTalkMessage.TWeTalkMessageType.BOT_LLM_TEXT,
            TWeTalkMessage.TWeTalkMessageType.BOT_LLM_STARTED,
            TWeTalkMessage.TWeTalkMessageType.BOT_LLM_STOPPED,
            TWeTalkMessage.TWeTalkMessageType.BOT_TTS_TEXT,
            TWeTalkMessage.TWeTalkMessageType.BOT_TTS_STARTED,
            TWeTalkMessage.TWeTalkMessageType.BOT_TTS_STOPPED -> {
                Log.d(TAG, "handleMessage, data: $message")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopConnection()
        client?.close()
        client = null
    }
}