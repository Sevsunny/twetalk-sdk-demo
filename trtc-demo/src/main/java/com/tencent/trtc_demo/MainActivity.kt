package com.tencent.trtc_demo

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
import com.tencent.trtc_demo.audio.MicRecorder
import com.tencent.trtc_demo.audio.RemotePcmPlayer
import com.tencent.trtc_demo.databinding.ActivityMainBinding
import com.tencent.twetalk.protocol.TWeTalkMessage
import com.tencent.twetalk_sdk_trtc.config.TRTCConfig
import com.tencent.twetalk_sdk_trtc.core.DefaultTRTCClient
import com.tencent.twetalk_sdk_trtc.core.TRTCClientListener
import com.tencent.twetalk_sdk_trtc.core.TRTCClientState
import com.tencent.twetalk_sdk_trtc.core.TWeTalkTRTCClient
import com.tencent.twetalk_sdk_trtc.protocal.AudioFrame
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity(), TRTCClientListener {
    companion object {
        private val TAG = MainActivity::class.simpleName
    }

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var mic: MicRecorder? = null
    private var started = false
    private var sampleRate = 16000
    private val remotePlayer = RemotePcmPlayer()
    private var client: TWeTalkTRTCClient? = null
    private val config = TRTCConfig.Builder()
        .sdkAppId(0)
        .sdkSecretKey("<Your SDKSecretKey>")
        .userId("<Your userId>")
        .productId("<Your ProductId>")
        .deviceName("<Your DeviceName>")
        .context(this)
        .build()

    private val reqPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startConversation()
        else toast("麦克风权限被拒绝")
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnStart.setOnClickListener {
            if (!started) ensureMicPermissionAndStartConversation()
            else stopConversation()
        }

        client = DefaultTRTCClient(config)
        client?.addListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopConversation()
        client?.destroy()
        client = null
    }

    private fun ensureMicPermissionAndStartConversation() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startConversation()
        else reqPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startConversation() {
        client?.startConversation()
    }

    private fun stopConversation() {
        client?.stopConversation()
        stopMic()
        started = false
        binding.btnStart.text = "Connect"
        appendLog("Disconnected by user")
    }

    private fun startMic() {
        if (mic != null) return
        mic = MicRecorder(sampleRate = 16000, chunkMs = 20) { bytes ->
            client?.sendCustomAudioData(bytes)
        }.also { it.start() }
    }

    private fun stopMic() {
        mic?.stop()
        mic = null
    }

    private fun handleMessage(msg: TWeTalkMessage) {
        when (msg.type) {
            TWeTalkMessage.TWeTalkMessageType.AUDIO_DATA -> {
                if (msg.data is AudioFrame) {
                    val audioFrame = msg.data as AudioFrame
                    val sr = if (audioFrame.sampleRate > 0) audioFrame.sampleRate else sampleRate
                    val ch = if (audioFrame.channels > 0) audioFrame.channels else 1
                    remotePlayer.play(audioFrame.data, sr, ch)
                }
            }

            TWeTalkMessage.TWeTalkMessageType.BOT_READY -> {}
            TWeTalkMessage.TWeTalkMessageType.ERROR -> {}

            TWeTalkMessage.TWeTalkMessageType.USER_LLM_TEXT -> {
                appendLog("${now()} Me: \n\n")
                handleTextMessage(msg)
            }

            TWeTalkMessage.TWeTalkMessageType.BOT_LLM_STARTED -> {
                appendLog("${now()} Bot: \n\n")
            }

            TWeTalkMessage.TWeTalkMessageType.BOT_LLM_TEXT -> {
                handleTextMessage(msg)
            }

            TWeTalkMessage.TWeTalkMessageType.BOT_LLM_STOPPED -> {
                appendLog("\n")
            }

            TWeTalkMessage.TWeTalkMessageType.USER_TRANSCRIPTION,
            TWeTalkMessage.TWeTalkMessageType.BOT_TRANSCRIPTION,
            TWeTalkMessage.TWeTalkMessageType.USER_STARTED_SPEAKING,
            TWeTalkMessage.TWeTalkMessageType.USER_STOPPED_SPEAKING,
            TWeTalkMessage.TWeTalkMessageType.BOT_STARTED_SPEAKING,
            TWeTalkMessage.TWeTalkMessageType.BOT_STOPPED_SPEAKING,
            TWeTalkMessage.TWeTalkMessageType.BOT_TTS_TEXT,
            TWeTalkMessage.TWeTalkMessageType.BOT_TTS_STARTED,
            TWeTalkMessage.TWeTalkMessageType.BOT_TTS_STOPPED -> {
                Log.d(TAG, "handleMessage, data: $msg")
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun appendLog(msg: String, needLineBreak: Boolean = true) {
        lifecycleScope.launch {
            val lineBreak = if (needLineBreak) "\n\n" else ""

            // 更新文本
            if (binding.tvLog.text.isEmpty()) {
                binding.tvLog.text = msg
            } else {
                binding.tvLog.append("$lineBreak$msg")
            }

            binding.scrollView.post {
                binding.scrollView.smoothScrollTo(0, binding.tvLog.bottom)
            }
        }
    }

    private fun handleTextMessage(message: TWeTalkMessage) {
        if (message.data is String) {
            try {
                val jsonData = JSON.parseObject(message.data as String)
                val text = jsonData.getString("text")
                appendLog(text, false)
            } catch (e: JSONException) {
                Log.e(
                    TAG,
                    "handleMessage: unknown json data: ${message.data as String}, error msg: ${e.message}"
                )
            }
        }
    }

    private fun now(): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date())
    }

    // TRTCClientListener Listener
    override fun onStateChanged(state: TRTCClientState?) {
        appendLog("onStateChanged: $state")

        lifecycleScope.launch {
            if (state == TRTCClientState.ON_CALLING) {
                started = true
                binding.btnStart.text = "Stop"
                appendLog("Started. Starting mic…")
                startMic()
            } else if (state == TRTCClientState.LEAVED) {
                started = false
                binding.btnStart.text = "Start"
                stopMic()
                appendLog("Stopped.")
            }
        }
    }

    override fun onRecvMessage(message: TWeTalkMessage) {
        handleMessage(message)
    }

    override fun onError(errCode: Int, errMsg: String?) {
        appendLog("onError, errCode: $errCode, errMsg: $errMsg")

        lifecycleScope.launch {
            started = false
            binding.btnStart.text = "Start"
            stopMic()
        }
    }
}