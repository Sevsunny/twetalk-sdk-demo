package com.tencent.twetalk_sdk_demo.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.SharedPreferences
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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONException
import com.tencent.twetalk.protocol.ImageMessage
import com.tencent.twetalk.protocol.TWeTalkMessage
import com.tencent.twetalk_sdk_demo.BaseActivity
import com.tencent.twetalk_sdk_demo.R
import com.tencent.twetalk_sdk_demo.SettingsActivity
import com.tencent.twetalk_sdk_demo.adapter.ChatMessageAdapter
import com.tencent.twetalk_sdk_demo.audio.AudioConfig
import com.tencent.twetalk_sdk_demo.audio.AudioFormatType
import com.tencent.twetalk_sdk_demo.audio.MicRecorder
import com.tencent.twetalk_sdk_demo.audio.RemotePlayer
import com.tencent.twetalk_sdk_demo.data.ChatMessage
import com.tencent.twetalk_sdk_demo.data.Constants
import com.tencent.twetalk_sdk_demo.data.MessageStatus
import com.tencent.twetalk_sdk_demo.databinding.ActivityChatBinding
import com.tencent.twetalk_sdk_demo.utils.PermissionHelper
import com.tencent.twetalk_sdk_demo.video.VideoChatCameraManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    protected var isVideoMode = false
    protected var cameraManager: VideoChatCameraManager? = null

    protected val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            this,
            Constants.KEY_SECRET_INFO_PREF,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private var micRecorder: MicRecorder? = null
    @Volatile private var isMicRecorderInitialized = false

    protected val reqPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false

        when {
            isVideoMode -> {
                if ((audioGranted && cameraGranted) || PermissionHelper.hasPermissions(this,
                        PermissionHelper.VIDEO_MODE_PERMISSIONS)) {
                    startChat()
                    initMicRecorder()
                } else {
                    val deniedPermissions = mutableListOf<String>()
                    if (!audioGranted) deniedPermissions.add("麦克风")
                    if (!cameraGranted) deniedPermissions.add("摄像头")

                    toast("${deniedPermissions.joinToString("、")}权限被拒绝")
                    finish()
                }
            }

            else -> {
                if (audioGranted) {
                    startChat()
                    initMicRecorder()
                } else {
                    toast("麦克风权限被拒绝")
                    finish()
                }
            }
        }
    }

    override fun getViewBinding() = ActivityChatBinding.inflate(layoutInflater)

    override fun initView() {
        isVideoMode = intent.getBundleExtra(Constants.KEY_CHAT_BUNDLE)
            ?.getBoolean(Constants.KEY_VIDEO_MODE) ?: false

        loadConnectionInfo()

        if (isVideoMode) {
            showVideoUI()
            setupVideoUI()
        } else {
            showAudioUI()
            setupAudioUI()
        }
    }

    private fun setupAudioUI() {
        setupToolbar()
        setupAudioConnectionInfo()
        setupAudioRecyclerView()
        setupAudioControls()
    }

    private fun setupVideoUI() {
        setupVideoRecyclerView()
        updateConnectState()

        cameraManager = VideoChatCameraManager(
            this,
            binding.videoChat.previewView
        ) { imgMsg ->
            onImageCaptured(imgMsg)
        }

        binding.videoChat.fabEndCall.setOnClickListener {
            stopRecording()
            stopChat()
        }

        binding.videoChat.fabSwitchCamera.setOnClickListener {
            cameraManager?.switchCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initClient()
        ensurePermissionsAndStart()
        bindCollector()
    }

    abstract fun initClient()
    abstract fun startChat()
    abstract fun stopChat()
    abstract fun onAudioData(audioData: ByteArray, sampleRate: Int, channels: Int)
    abstract fun onImageCaptured(imgMsg: ImageMessage)

    protected fun updateConnectState() {
        // 更新状态显示
        lifecycleScope.launch {
            if (isVideoMode) {
                with(binding.videoChat) {
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
            } else {
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
        // TODO 修改适配一下,如果是使用 TRTC 采集音频的情况
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val audioConfig = if (audioFormat.equals("OPUS", true)) {
                    AudioConfig(chunkMs = 60, formatType = AudioFormatType.OPUS)
                } else {
//                    val pcmFile = File(this@BaseChatActivity.getExternalFilesDir(null), "test_${System.currentTimeMillis()}.pcm")
//                    AudioConfig(saveToFile = true, filePath = pcmFile.absolutePath)
                    AudioConfig()
                }

                micRecorder = MicRecorder(audioConfig) { audioData ->
                    onAudioData(audioData, audioConfig.sampleRate, audioConfig.channelCount)
                }.also { 
                    it.init()
                    isMicRecorderInitialized = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "MicRecorder init failed", e)

                lifecycleScope.launch(Dispatchers.Main) {
                    toast("麦克风初始化失败: ${e.message}")
                }
            }
        }
    }

    private fun loadConnectionInfo() {
        val bundle = intent.getBundleExtra(Constants.KEY_CHAT_BUNDLE)

        bundle?.run {
            connectionType = getString(Constants.KEY_CONNECTION_TYPE, "WEBSOCKET")
            audioFormat = getString(Constants.KEY_AUDIO_TYPE, "PCM") ?: "PCM"
        } ?: run {
            toast("没有读取到配置")
        }
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

    private fun setupVideoRecyclerView() {
        messageAdapter = ChatMessageAdapter(true)

        binding.videoChat.recyclerViewMessages.apply {
            adapter = messageAdapter
            layoutManager = LinearLayoutManager(this@BaseChatActivity)
        }
    }

    private fun setupAudioRecyclerView() {
        messageAdapter = ChatMessageAdapter()

        binding.recyclerViewMessages.apply {
            adapter = messageAdapter
            layoutManager = LinearLayoutManager(this@BaseChatActivity)
        }
    }

    private fun setupAudioConnectionInfo() {
        updateConnectState()

        if (connectionType == "TRTC") {
            binding.chipAudioFormat.isVisible = false
        }

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

    private fun bindCollector() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    ConversationManager.messages.collect(this@BaseChatActivity::onMessageUpdate)
                }

                launch {
                    ConversationManager.assistantTyping.collect(this@BaseChatActivity::onLLMTyping)
                }
            }
        }
    }

    private fun showAudioUI() {
        with(binding) {
            videoChatLayout.visibility = View.GONE
            toolbar.visibility = View.VISIBLE
            statusBar.visibility = View.VISIBLE
            recyclerViewMessages.visibility = View.VISIBLE
            audioControlPanel.visibility = View.VISIBLE
        }
    }

    private fun showVideoUI() {
        with(binding) {
            videoChatLayout.visibility = View.VISIBLE
            toolbar.visibility = View.GONE
            statusBar.visibility = View.GONE
            recyclerViewMessages.visibility = View.GONE
            audioControlPanel.visibility = View.GONE
        }
    }

    private fun onMessageUpdate(messageList: List<ChatMessage>) {
        val lastPosition = messageList.size - 1

        messageAdapter.submitList(messageList) {
            if (lastPosition >= 0) {
                val lastMessage = messageList[lastPosition]

                // bot 消息流式输出时在开始输出和结束输出时滑动
                if ((lastMessage.status != MessageStatus.STREAMING) || (lastMessage.content == "")) {
                    val rv = if (isVideoMode) binding.videoChat.recyclerViewMessages else binding.recyclerViewMessages
                    rv.smoothScrollToPosition(lastPosition)
                }
            }
        }
    }

    private fun onLLMTyping(isTyping: Boolean) {
        Log.d(TAG, "collect typing: $isTyping")

        if (!isVideoMode) {
            if (isTyping) {
                binding.tvAudioStatus.text = getString(R.string.processing)
                binding.audioVisualizerContainer.visibility = View.VISIBLE
            } else {
                binding.audioVisualizerContainer.visibility = View.GONE
            }
        }
    }

    private fun ensurePermissionsAndStart() {
        val requiredPermissions = if (isVideoMode) {
            PermissionHelper.VIDEO_MODE_PERMISSIONS
        } else {
            PermissionHelper.AUDIO_MODE_PERMISSIONS
        }
        
        if (PermissionHelper.hasPermissions(this, requiredPermissions)) {
            startChat()
            initMicRecorder()
        } else {
            // 请求缺失的权限
            val missingPermissions = PermissionHelper.getMissingPermissions(this, requiredPermissions)
            reqPermissions.launch(missingPermissions.toTypedArray())
        }
    }

    protected fun startRecording() {
        if (isPaused) return

        if (isMicRecorderInitialized) {
            performRecording()
            return
        }

        lifecycleScope.launch {
            // 如果没有完成初始化则等待
            withContext(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                val timeout = 5000L // 5秒超时

                while (!isMicRecorderInitialized && (System.currentTimeMillis() - startTime) < timeout) {
                    Thread.sleep(50)
                }

                if (!isMicRecorderInitialized) {
                    Log.e(TAG, "MicRecorder initialization timeout")

                    withContext(Dispatchers.Main) {
                        toast("麦克风初始化超时，请重试")
                    }

                    return@withContext
                }
            }

            withContext(Dispatchers.Main) {
                performRecording()
            }
        }
    }

    private fun performRecording() {
        if (micRecorder == null) {
            Log.e(TAG, "MicRecorder is null")
            toast("麦克风未就绪，请重试")
            return
        }

        isRecording = true
        micRecorder?.start()

        if (!isVideoMode) {
            updateRecordingUI(true)
            binding.tvRecordHint.text = getString(R.string.release_to_send)
            binding.audioVisualizerContainer.visibility = View.VISIBLE
            binding.tvAudioStatus.text = getString(R.string.listening)
            animateRecording()
        }
    }

    protected fun stopRecording() {
        isRecording = false
        micRecorder?.stop()

        if (!isVideoMode) {
            updateRecordingUI(false)
            binding.tvRecordHint.text = getString(R.string.hold_to_speak)
            binding.tvAudioStatus.text = getString(R.string.processing)
        }
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

            TWeTalkMessage.TWeTalkMessageType.SERVER_MESSAGE -> {
                if (message.data is String) {
                    try {
                        val jsonData = JSON.parseObject(message.data as String)
                        val type = jsonData.getString("type")

                        // 如果是请求图片则捕获相机并发送一张图片
                        if (type == "request_image") {
                            // send image
                            cameraManager?.captureImage()
                        }
                    } catch (e: JSONException) {
                        Log.e(
                            TAG,
                            "handleMessage: unknown json data: ${message.data as String}, error msg: ${e.message}"
                        )
                    }
                }
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
        isMicRecorderInitialized = false
        player.release()
        micRecorder?.release()
        micRecorder = null
        cameraManager?.release()
        cameraManager = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        ConversationManager.clearMessage()
        releaseInternal()
    }
}