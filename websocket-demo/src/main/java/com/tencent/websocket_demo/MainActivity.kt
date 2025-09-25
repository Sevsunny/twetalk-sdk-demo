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
import com.tencent.websocket_demo.audio.MicRecorder
import com.tencent.websocket_demo.audio.RemotePcmPlayer
import com.tencent.twetalk.core.ConnectionState
import com.tencent.twetalk.core.DefaultTWeTalkClient
import com.tencent.twetalk.core.TWeTalkClient
import com.tencent.twetalk.core.TWeTalkClientListener
import com.tencent.twetalk.core.TWeTalkConfig
import com.tencent.twetalk.metrics.MetricEvent
import com.tencent.twetalk.protocol.FrameProcessor
import com.tencent.twetalk.protocol.FramesProtos
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

            override fun onRecvStream(buffer: ByteArray?) {
                if (buffer == null) return
                val frame = FrameProcessor.decode(buffer)
                handleFrame(frame)
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

    private fun handleFrame(frame: FramesProtos.Frame) {
        when (frame.frameCase) {
            FramesProtos.Frame.FrameCase.TRANSCRIPTION -> {
                val t = frame.transcription
                Log.i(TAG, "Transcription: ${t.text} [user=${t.userId}, ts=${t.timestamp}]")
            }

            FramesProtos.Frame.FrameCase.MESSAGE -> {
                val jsonData = frame.message.data
                Log.i(TAG, "ServerMessage: $jsonData")
                appendLog("ServerMessage: $jsonData")
            }

            FramesProtos.Frame.FrameCase.TEXT -> {
                Log.i(TAG, "ServerText: ${frame.text.text}")
            }

            FramesProtos.Frame.FrameCase.AUDIO -> {
                val a = frame.audio
                val sr = if (a.sampleRate > 0) a.sampleRate else sampleRate
                val ch = if (a.numChannels > 0) a.numChannels else 1
                val data = a.audio.toByteArray()
                remotePlayer.play(data, sr, ch)
            }

            FramesProtos.Frame.FrameCase.FRAME_NOT_SET -> {
                Log.e(TAG, "Frame not set")
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