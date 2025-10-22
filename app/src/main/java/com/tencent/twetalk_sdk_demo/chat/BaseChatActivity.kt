package com.tencent.twetalk_sdk_demo.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONException
import com.tencent.twetalk.protocol.TWeTalkMessage
import com.tencent.twetalk_sdk_demo.BaseActivity
import com.tencent.twetalk_sdk_demo.MainActivity
import com.tencent.twetalk_sdk_demo.R
import com.tencent.twetalk_sdk_demo.SettingsActivity
import com.tencent.twetalk_sdk_demo.adapter.ChatMessageAdapter
import com.tencent.twetalk_sdk_demo.audio.AudioConfig
import com.tencent.twetalk_sdk_demo.audio.AudioFormatType
import com.tencent.twetalk_sdk_demo.audio.MicRecorder
import com.tencent.twetalk_sdk_demo.audio.RemotePlayer
import com.tencent.twetalk_sdk_demo.data.MessageStatus
import com.tencent.twetalk_sdk_demo.databinding.ActivityChatBinding
import kotlinx.coroutines.launch

abstract class BaseChatActivity : BaseActivity<ActivityChatBinding>() {
    companion object {
        private val TAG = BaseChatActivity::class.simpleName
    }

    private lateinit var messageAdapter: ChatMessageAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var isRecording = false
    private var isPaused = false
    private var connectionType: String = ""
    private var audioFormat: String = ""
    protected var isConnected = false
    protected val player = RemotePlayer()
    private var micRecorder: MicRecorder? = null

    protected val reqPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startChat()
            initMicRecorder()
        } else {
            toast("麦克风权限被拒绝")
            finish()
        }
    }

    override fun getViewBinding() = ActivityChatBinding.inflate(layoutInflater)

    override fun initView() {
        setupToolbar()
        setupRecyclerView()
        setupConnectionInfo()
        setupAudioControls()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initClient()
        ensureMicPermissionAndStart()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    ConversationManager.messages.collect { messageList ->
                        val lastPosition = messageList.size - 1

                        messageAdapter.submitList(messageList) {
                            if (lastPosition >= 0) {
                                val lastMessage = messageList[lastPosition]

                                // bot 消息流式输出时在开始输出和结束输出时滑动
                                if ((lastMessage.status != MessageStatus.STREAMING) || (lastMessage.content == "")) {
                                    binding.recyclerViewMessages.smoothScrollToPosition(lastPosition)
                                }
                            }
                        }
                    }
                }

                launch {
                    ConversationManager.assistantTyping.collect { isTyping ->
                        Log.d(TAG, "collect typing: $isTyping")
                        if (isTyping) {
                            binding.tvAudioStatus.text = getString(R.string.processing)
                            binding.audioVisualizerContainer.visibility = View.VISIBLE
                        } else {
                            binding.audioVisualizerContainer.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    abstract fun initClient()
    abstract fun startChat()
    abstract fun stopChat()
    abstract fun onAudioData(audioData: ByteArray, sampleRate: Int, channels: Int)

    protected fun updateConnectState() {
        // 更新状态显示
        lifecycleScope.launch {
            with(binding) {
                if (isConnected) {
                    chipConnectionStatus.text = getString(R.string.connected)
                    chipConnectionStatus.chipIcon =
                        AppCompatResources.getDrawable(this@BaseChatActivity, R.drawable.ic_connected)
                    chipConnectionStatus.chipBackgroundColor =
                        ContextCompat.getColorStateList(this@BaseChatActivity, R.color.success_green)
                } else {
                    chipConnectionStatus.text = getString(R.string.disconnected)
                    chipConnectionStatus.chipIcon =
                        AppCompatResources.getDrawable(this@BaseChatActivity, R.drawable.ic_disconnected)
                    chipConnectionStatus.chipBackgroundColor =
                        ContextCompat.getColorStateList(this@BaseChatActivity, R.color.error_red)
                }
            }
        }
    }

    private var rotationAnimation: Animation? = null

    protected fun showLoading(isShow: Boolean, tips: String = getString(R.string.connecting)) {
        lifecycleScope.launch {
            if (isShow) {
                binding.loading.tvLoadingText.text = tips
                binding.loadingLayout.isVisible = true
                binding.loadingLayout.bringToFront()

                rotationAnimation = AnimationUtils.loadAnimation(
                    this@BaseChatActivity,
                    R.anim.rotate_loading
                ).also {
                    // 确保动画以ImageView中心为旋转点
                    binding.loading.ivLoading.pivotX = binding.loading.ivLoading.width.div(2f)
                    binding.loading.ivLoading.pivotY = binding.loading.ivLoading.height.div(2f)
                    binding.loading.ivLoading.startAnimation(it)
                }
            } else {
                rotationAnimation?.cancel()
                binding.loadingLayout.isVisible = false
            }
        }
    }

    protected fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun initMicRecorder() {
        // TODO 修改适配一下，如果是使用 TRTC 采集音频的情况
        val audioConfig = if (audioFormat.equals("OPUS", true)) {
            AudioConfig(chunkMs = 60, formatType = AudioFormatType.OPUS)
        } else {
//            val pcmFile = File(this.getExternalFilesDir(null), "test_${System.currentTimeMillis()}.pcm")
//            AudioConfig(saveToFile = true, filePath = pcmFile.absolutePath)
            AudioConfig()
        }

        micRecorder = MicRecorder(audioConfig) { audioData ->
            onAudioData(audioData, audioConfig.sampleRate, audioConfig.channelCount)
        }.also { it.init() }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        binding.toolbar.setNavigationOnClickListener {
            AlertDialog.Builder(this@BaseChatActivity)
                .setTitle("结束对话")
                .setMessage("是否要结束对话？")
                .setPositiveButton("确定") { _,_ -> stopChat() }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = ChatMessageAdapter()

        binding.recyclerViewMessages.apply {
            adapter = messageAdapter
            layoutManager = LinearLayoutManager(this@BaseChatActivity)
        }
    }

    private fun setupConnectionInfo() {
        // 获取连接参数
        val bundle = intent.getBundleExtra(MainActivity.Companion.KEY_BUNDLE_NAME)

        bundle?.run {
            connectionType = bundle.getString(MainActivity.Companion.KEY_CONNECTION_TYPE, "WEBSOCKET")

            // TODO 启动 TRTC 采集功能后再显示这个 chip
            if (connectionType == "TRTC") {
                binding.chipAudioFormat.isVisible = false
            }

            audioFormat = bundle.getString(
                MainActivity.Companion.KEY_AUDIO_TYPE,
                if (SettingsActivity.Companion.isTRTCRecord(this@BaseChatActivity)) {
                    "TRTC 采集"
                } else {
                    "其它"
                })
        } ?: {
            toast("没有读取到配置")
        }

        updateConnectState()
        binding.chipAudioFormat.text = audioFormat

        binding.btnEndChat.setOnClickListener {
            stopChat()
        }
    }

    private fun setupAudioControls() {
        setupRecordButton()
        setupControlButtons()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecordButton() {
        binding.fabRecord.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isPaused) {
                        startRecording()
                    }

                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isRecording) {
                        stopRecording()
                    }

                    true
                }

                else -> false
            }
        }
    }

    private fun setupControlButtons() {
        binding.btnPause.setOnClickListener {
            if (isPaused) {
                resumeRecording()
            } else {
                pauseRecording()
            }
        }

        binding.btnStop.setOnClickListener {
            if (isRecording) {
                stopRecording()
            }
        }
    }

    private fun ensureMicPermissionAndStart() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startChat()
            initMicRecorder()
        } else reqPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startRecording() {
        if (isPaused || micRecorder == null) return

        isRecording = true
        updateRecordingUI(true)
        micRecorder?.start()
        binding.tvRecordHint.text = getString(R.string.release_to_send)
        binding.audioVisualizerContainer.visibility = View.VISIBLE
        binding.tvAudioStatus.text = getString(R.string.listening)
        animateRecording()
    }

    private fun stopRecording() {
        isRecording = false
        updateRecordingUI(false)

        micRecorder?.stop()
        binding.tvRecordHint.text = getString(R.string.hold_to_speak)
        binding.tvAudioStatus.text = getString(R.string.processing)
    }

    private fun pauseRecording() {
        isPaused = true
        binding.btnPause.setIconResource(R.drawable.ic_play)
        binding.tvAudioStatus.text = "已暂停"
        toast("录音已暂停")
    }

    private fun resumeRecording() {
        isPaused = false
        binding.btnPause.setIconResource(R.drawable.ic_pause)
        binding.tvAudioStatus.text = getString(R.string.listening)
        toast("录音已恢复")
    }

    private fun updateRecordingUI(recording: Boolean) {
        if (recording) {
            binding.fabRecord.backgroundTintList = 
                ContextCompat.getColorStateList(this, R.color.error_red)
            binding.fabRecord.setImageResource(R.drawable.ic_mic_recording)
        } else {
            binding.fabRecord.backgroundTintList = 
                ContextCompat.getColorStateList(this, R.color.primary_blue)
            binding.fabRecord.setImageResource(R.drawable.ic_mic)
        }
    }

    private fun animateRecording() {
        // 简单的录音动画效果
        if (isRecording && !isPaused) {
            binding.tvAudioStatus.alpha = if (binding.tvAudioStatus.alpha == 1f) 0.5f else 1f
            handler.postDelayed({ animateRecording() }, 500)
        } else {
            binding.tvAudioStatus.alpha = 1f
        }
    }

    protected fun handleMessage(message: TWeTalkMessage) {
        when (message.type) {
            TWeTalkMessage.TWeTalkMessageType.AUDIO_DATA -> {
                if (message.data is TWeTalkMessage.AudioMessage) {
                    val audioMsg = message.data as TWeTalkMessage.AudioMessage
                    val sr = if (audioMsg.sampleRate > 0) audioMsg.sampleRate else 16000
                    val ch = if (audioMsg.numChannels > 0) audioMsg.numChannels else 1
                    player.play(audioMsg.audio, sr, ch, audioFormat != "OPUS")
                }
            }

            TWeTalkMessage.TWeTalkMessageType.BOT_READY -> {}
            TWeTalkMessage.TWeTalkMessageType.ERROR -> {}

            TWeTalkMessage.TWeTalkMessageType.USER_LLM_TEXT -> {
                // 打断机器人的话
                ConversationManager.interruptAssistant()
                player.stop()

                // 通知用户对话
                val text = obtainTextFromTextMessage(message)
                ConversationManager.onUserLLMText(text)
            }

            TWeTalkMessage.TWeTalkMessageType.BOT_LLM_STARTED -> {
                ConversationManager.onBotLLMStarted()
            }

            TWeTalkMessage.TWeTalkMessageType.BOT_LLM_TEXT -> {
                val text = obtainTextFromTextMessage(message)
                ConversationManager.onBotLLMText(text)
            }

            TWeTalkMessage.TWeTalkMessageType.BOT_LLM_STOPPED -> {
                ConversationManager.onBotLLMStopped()
            }

            TWeTalkMessage.TWeTalkMessageType.USER_STARTED_SPEAKING -> {
                Log.d("Metric", "User start speaking...")
            }

            TWeTalkMessage.TWeTalkMessageType.USER_STOPPED_SPEAKING -> {
                Log.d("Metric", "User stop speaking.")
            }

            TWeTalkMessage.TWeTalkMessageType.BOT_STARTED_SPEAKING -> {
                Log.d("Metric", "Bot start speaking...")
            }

            TWeTalkMessage.TWeTalkMessageType.BOT_STOPPED_SPEAKING -> {
                Log.d("Metric", "Bot stop speaking.")
            }

            TWeTalkMessage.TWeTalkMessageType.BOT_TRANSCRIPTION -> {
//                if (audioFormat == "OPUS") {
//                    val text = obtainTextFromTextMessage(message)
//                    ConversationManager.onBotLLMText(text)
//                }
            }

            // 其余消息根据情况处理
            TWeTalkMessage.TWeTalkMessageType.USER_TRANSCRIPTION,
            TWeTalkMessage.TWeTalkMessageType.BOT_TTS_TEXT,
            TWeTalkMessage.TWeTalkMessageType.BOT_TTS_STARTED,
            TWeTalkMessage.TWeTalkMessageType.BOT_TTS_STOPPED -> {
//                Log.d(TAG, "handleMessage, data: $message")
            }
        }
    }

    private fun obtainTextFromTextMessage(message: TWeTalkMessage): String {
        if (message.data is String) {
            try {
                val jsonData = JSON.parseObject(message.data as String)
                val text = jsonData.getString("text")
                return text
            } catch (e: JSONException) {
                Log.e(
                    TAG,
                    "handleMessage: unknown json data: ${message.data as String}, error msg: ${e.message}"
                )

                return ""
            }
        }

        return ""
    }

    private fun releaseInternal() {
        player.release()
        micRecorder?.release()
        micRecorder = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        ConversationManager.clearMessage()
        releaseInternal()
    }
}