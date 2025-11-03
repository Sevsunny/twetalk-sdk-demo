package com.tencent.twetalk_sdk_demo

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alibaba.fastjson2.JSON
import com.google.android.material.textfield.TextInputEditText
import com.tencent.twetalk.config.IdleResponseInfo
import com.tencent.twetalk.config.TalkBasicConfigInfo
import com.tencent.twetalk.config.TalkConfig
import com.tencent.twetalk.config.TalkConversationConfigInfo
import com.tencent.twetalk.config.TalkIdleDetectionConfigInfo
import com.tencent.twetalk.config.TalkLLMConfigInfo
import com.tencent.twetalk.config.TalkSTTConfigInfo
import com.tencent.twetalk.config.TalkTTSConfigInfo
import com.tencent.twetalk.util.AuthUtil
import com.tencent.twetalk_sdk_demo.data.Constants
import com.tencent.twetalk_sdk_demo.databinding.ActivityAiConfigBinding

/**
 * AI 配置页面
 * 提供完整的 AI Talk 配置功能
 */
class AiConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiConfigBinding
    private lateinit var sharedPreferences: SharedPreferences

    // 展开状态
    private var isBasicConfigExpanded = false
    private var isSTTConfigExpanded = false
    private var isLLMConfigExpanded = false
    private var isTTSConfigExpanded = false
    private var isConversationConfigExpanded = false

    // 空闲响应视图列表
    private val idleResponseViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        setupToolbar()
        setupExpandableCards()
        setupIdleResponseManagement()
        loadConfig()
    }

    /**
     * 设置工具栏
     */
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            saveConfig()
        }
    }

    /**
     * 设置可展开卡片
     */
    private fun setupExpandableCards() {
        // 基础配置
        binding.layoutBasicConfigHeader.setOnClickListener {
            isBasicConfigExpanded = !isBasicConfigExpanded
            toggleCardExpansion(
                binding.layoutBasicConfigContent,
                binding.ivBasicConfigExpand,
                isBasicConfigExpanded
            )
        }

        // STT 配置
        binding.layoutSTTConfigHeader.setOnClickListener {
            isSTTConfigExpanded = !isSTTConfigExpanded
            toggleCardExpansion(
                binding.layoutSTTConfigContent,
                binding.ivSTTConfigExpand,
                isSTTConfigExpanded
            )
        }

        // LLM 配置
        binding.layoutLLMConfigHeader.setOnClickListener {
            isLLMConfigExpanded = !isLLMConfigExpanded
            toggleCardExpansion(
                binding.layoutLLMConfigContent,
                binding.ivLLMConfigExpand,
                isLLMConfigExpanded
            )
        }

        // TTS 配置
        binding.layoutTTSConfigHeader.setOnClickListener {
            isTTSConfigExpanded = !isTTSConfigExpanded
            toggleCardExpansion(
                binding.layoutTTSConfigContent,
                binding.ivTTSConfigExpand,
                isTTSConfigExpanded
            )
        }

        // 会话配置
        binding.layoutConversationConfigHeader.setOnClickListener {
            isConversationConfigExpanded = !isConversationConfigExpanded
            toggleCardExpansion(
                binding.layoutConversationConfigContent,
                binding.ivConversationConfigExpand,
                isConversationConfigExpanded
            )
        }
    }

    /**
     * 切换卡片展开/收起状态
     */
    private fun toggleCardExpansion(contentLayout: View, expandIcon: View, isExpanded: Boolean) {
        contentLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
        expandIcon.rotation = if (isExpanded) 180f else 0f
    }

    /**
     * 设置空闲响应管理
     */
    private fun setupIdleResponseManagement() {
        // 初始化第一个空闲响应视图
        val firstView = binding.idleResponse1.root
        idleResponseViews.add(firstView)
        setupIdleResponseRemoveButton(firstView, 0)

        // 添加按钮点击事件
        binding.btnAddIdleResponse.setOnClickListener {
            if (idleResponseViews.size < 3) {
                addIdleResponseView()
            } else {
                Toast.makeText(this, "最多只能添加3个空闲响应配置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 添加空闲响应视图
     */
    private fun addIdleResponseView() {
        val inflater = LayoutInflater.from(this)
        val newView = inflater.inflate(R.layout.item_idle_response, binding.layoutIdleResponsesContainer, false)
        
        val index = idleResponseViews.size
        idleResponseViews.add(newView)
        setupIdleResponseRemoveButton(newView, index)
        
        binding.layoutIdleResponsesContainer.addView(newView)
    }

    /**
     * 设置空闲响应删除按钮
     */
    private fun setupIdleResponseRemoveButton(view: View, index: Int) {
        val btnRemove = view.findViewById<View>(R.id.btnRemoveIdleResponse)
        btnRemove.setOnClickListener {
            if (idleResponseViews.size > 1) {
                binding.layoutIdleResponsesContainer.removeView(view)
                idleResponseViews.remove(view)
            } else {
                Toast.makeText(this, "至少保留一个空闲响应配置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 加载配置
     */
    private fun loadConfig() {
        val configJson = sharedPreferences.getString(KEY_CONFIG_JSON, null)

        if (configJson != null) {
            try {
                parseAndFillConfig(configJson)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.config_load_failed, Toast.LENGTH_SHORT).show()
            }
        }

        // 设备信息反向渲染
        getSharedPreferences(Constants.KEY_DEVICE_INFO_PREF, MODE_PRIVATE).run {
            binding.etProductId.setText(this.getString(Constants.KEY_PRODUCT_ID, null) ?: BuildConfig.productId)
            binding.etDeviceName.setText(this.getString(Constants.KEY_DEVICE_NAME, null) ?: BuildConfig.deviceName)
        }

        // 密钥信息反向渲染
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            this,
            Constants.KEY_SECRET_INFO_PREF,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).run {
            binding.etSecretId.setText(this.getString(Constants.KEY_SECRET_ID, null) ?: BuildConfig.secretId)
            binding.etSecretKey.setText(this.getString(Constants.KEY_SECRET_KEY, null) ?: BuildConfig.secretKey)
        }
    }

    /**
     * 解析并填充配置
     */
    private fun parseAndFillConfig(configJson: String) {
        val json = JSON.parseObject(configJson)

        // 密钥信息
        binding.etSecretId.setText(BuildConfig.secretId)
        binding.etSecretKey.setText(BuildConfig.secretKey)

        // 基础信息
        binding.etConfigName.setText(json.getString("ConfigName") ?: "")
        binding.etProductId.setText(json.getString("ProductId") ?: BuildConfig.productId)
        binding.etDeviceName.setText(json.getString("DeviceName") ?: BuildConfig.deviceName)
        binding.etTargetLanguage.setText(json.getString("TargetLanguage") ?: "")

        // 基础配置
        val basicConfig = json.getJSONObject("BasicConfig")
        if (basicConfig != null) {
            binding.etSystemPrompt.setText(basicConfig.getString("SystemPrompt") ?: "")
            binding.etGreetingMessage.setText(basicConfig.getString("GreetingMessage") ?: "")
            val voiceType = basicConfig.getInteger("DefaultVoiceType")
            if (voiceType != null) {
                binding.etDefaultVoiceType.setText(voiceType.toString())
            }
        }

        // STT 配置
        val sttConfig = json.getJSONObject("STTConfig")
        if (sttConfig != null) {
            binding.switchSTTEnabled.isChecked = sttConfig.getBooleanValue("Enabled")
            binding.etSTTType.setText(sttConfig.getString("STTType") ?: "")
            binding.etSTTConfig.setText(sttConfig.getString("Config") ?: "")
        }

        // LLM 配置
        val llmConfig = json.getJSONObject("LLMConfig")
        if (llmConfig != null) {
            binding.switchLLMEnabled.isChecked = llmConfig.getBooleanValue("Enabled")
            binding.etLLMType.setText(llmConfig.getString("LLMType") ?: "")
            binding.etLLMModel.setText(llmConfig.getString("Model") ?: "")
            binding.switchLLMStreaming.isChecked = llmConfig.getBooleanValue("Streaming")
            val temperature = llmConfig.getFloat("Temperature")
            if (temperature != null) {
                binding.etTemperature.setText(temperature.toString())
            }
            val maxTokens = llmConfig.getInteger("MaxTokens")
            if (maxTokens != null) {
                binding.etMaxTokens.setText(maxTokens.toString())
            }
            val topP = llmConfig.getFloat("TopP")
            if (topP != null) {
                binding.etTopP.setText(topP.toString())
            }
            binding.etLLMConfig.setText(llmConfig.getString("Config") ?: "")
        }

        // TTS 配置
        val ttsConfig = json.getJSONObject("TTSConfig")
        if (ttsConfig != null) {
            binding.switchTTSEnabled.isChecked = ttsConfig.getBooleanValue("Enabled")
            binding.etTTSType.setText(ttsConfig.getString("TTSType") ?: "")
            val speed = ttsConfig.getFloat("Speed")
            if (speed != null) {
                binding.etSpeed.setText(speed.toString())
            }
            val volume = ttsConfig.getFloat("Volume")
            if (volume != null) {
                binding.etVolume.setText(volume.toString())
            }
            val pitch = ttsConfig.getFloat("Pitch")
            if (pitch != null) {
                binding.etPitch.setText(pitch.toString())
            }
            binding.etTTSConfig.setText(ttsConfig.getString("Config") ?: "")
        }

        // 会话配置
        val conversationConfig = json.getJSONObject("ConversationConfig")
        if (conversationConfig != null) {
            val sessionTimeout = conversationConfig.getInteger("SessionTimeout")
            if (sessionTimeout != null) {
                binding.etSessionTimeout.setText(sessionTimeout.toString())
            }
            binding.switchInterruptionEnabled.isChecked = conversationConfig.getBooleanValue("InterruptionEnabled")
            val maxContextTokens = conversationConfig.getInteger("MaxContextTokens")
            if (maxContextTokens != null) {
                binding.etMaxContextTokens.setText(maxContextTokens.toString())
            }
            binding.switchEmotionEnabled.isChecked = conversationConfig.getBooleanValue("EmotionEnabled")
            binding.switchSemanticVADEnabled.isChecked = conversationConfig.getBooleanValue("SemanticVADEnabled")
            binding.switchNoiseFilterEnabled.isChecked = conversationConfig.getBooleanValue("NoiseFilterEnabled")

            // 空闲检测配置
            val idleDetection = conversationConfig.getJSONObject("IdleDetection")
            if (idleDetection != null) {
                binding.switchIdleEnabled.isChecked = idleDetection.getBooleanValue("Enabled")
                val timeoutSeconds = idleDetection.getFloat("TimeoutSeconds")
                if (timeoutSeconds != null) {
                    binding.etIdleTimeout.setText(timeoutSeconds.toString())
                }
                val maxRetries = idleDetection.getInteger("MaxRetries")
                if (maxRetries != null) {
                    binding.etIdleMaxRetries.setText(maxRetries.toString())
                }

                // 空闲响应列表
                val idleResponses = idleDetection.getJSONArray("IdleResponses")
                if (idleResponses != null && idleResponses.isNotEmpty()) {
                    // 清空现有视图
                    binding.layoutIdleResponsesContainer.removeAllViews()
                    idleResponseViews.clear()

                    // 添加空闲响应视图
                    for (i in 0 until idleResponses.size.coerceAtMost(3)) {
                        val response = idleResponses.getJSONObject(i)
                        val view = if (i == 0) {
                            binding.idleResponse1.root
                        } else {
                            val inflater = LayoutInflater.from(this)
                            inflater.inflate(R.layout.item_idle_response, binding.layoutIdleResponsesContainer, false)
                        }

                        val etRetryCount = view.findViewById<TextInputEditText>(R.id.etRetryCount)
                        val etResponseMessage = view.findViewById<TextInputEditText>(R.id.etResponseMessage)

                        val retryCount = response.getInteger("RetryCount")
                        if (retryCount != null) {
                            etRetryCount.setText(retryCount.toString())
                        }
                        etResponseMessage.setText(response.getString("Message") ?: "")

                        idleResponseViews.add(view)
                        setupIdleResponseRemoveButton(view, i)
                        binding.layoutIdleResponsesContainer.addView(view)
                    }
                }
            }
        }
    }

    /**
     * 保存配置
     */
    private fun saveConfig() {
        if (!validateInputs()) {
            return
        }

        try {
            val config = buildTalkConfig()
            val configJson = config.toJsonString()
            Log.d(TAG, "saveConfig: $configJson")
            doSaveRequest(configJson)

            // 保存到 SharedPreferences
            sharedPreferences.edit()
                .putString(KEY_CONFIG_JSON, configJson)
                .apply()

            Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "saveConfig, error", e)
        }
    }

    /**
     * 保存配置请求，如果保存失败会抛出异常
     */
    private fun doSaveRequest(configJson: String) {
        val secretId = binding.etSecretId.text.toString()
        val secretKey = binding.etSecretKey.text.toString()
        val resp = AuthUtil.doCreateConfigRequest(secretId, secretKey, configJson)

        Log.d(TAG, "saveRequest, resp: $resp")
    }


    /**
     * 输入参数校验
     */
    private fun validateInputs(): Boolean {
        var result = true
        
        if (binding.etProductId.text.isNullOrBlank()) {
            validateFailed(binding.etProductId)
            result = false
        }

        if (binding.etSecretId.text.isNullOrBlank()) {
            validateFailed(binding.etSecretId)
            result = false
        }

        if (binding.etSecretKey.text.isNullOrBlank()) {
            validateFailed(binding.etSecretKey)
            result = false
        }

        // 如果启用自定 STT 但不填参数
        if (binding.switchSTTEnabled.isChecked) {
            if (binding.etSTTType.text.isNullOrBlank()) {
                validateFailed(binding.etSTTType)
                result = false
            }
            
            if (binding.etSTTConfig.text.isNullOrBlank()) {
                validateFailed(binding.etSTTConfig)
                result = false
            }
        }

        // 如果启用自定 LLM 但不填 LLMType, Model, Config
        if (binding.switchLLMEnabled.isChecked) {
            if (binding.etLLMType.text.isNullOrBlank()) {
                validateFailed(binding.etLLMType)
                result = false
            }

            if (binding.etLLMModel.text.isNullOrBlank()) {
                validateFailed(binding.etLLMModel)
                result = false
            }

            if (binding.etLLMConfig.text.isNullOrBlank()) {
                validateFailed(binding.etLLMConfig)
                result = false
            }
        }

        // 如果启用自定 TTS 但不填 type、config 参数
        if (binding.switchTTSEnabled.isChecked) {
            if (binding.etTTSType.text.isNullOrBlank()) {
                validateFailed(binding.etTTSType)
                result = false
            }

            if (binding.etTTSConfig.text.isNullOrBlank()) {
                validateFailed(binding.etTTSConfig)
                result = false
            }
        }
        
        return result
    }

    private fun validateFailed(
        et: EditText,
        error: String = getString(R.string.required_field)
    ) {
        et.error = error
    }

    /**
     * 构建 TalkConfig 对象
     */
    private fun buildTalkConfig(): TalkConfig {
        val config = TalkConfig()

        // 基础信息
        config.configName = binding.etConfigName.text.toString()
        config.productId = binding.etProductId.text.toString()
        val deviceName = binding.etDeviceName.text.toString()

        if (deviceName.isNotEmpty()) {
            config.deviceName = deviceName
        }

        val targetLanguage = binding.etTargetLanguage.text.toString()

        if (targetLanguage.isNotEmpty()) {
            config.targetLanguage = targetLanguage.lowercase()
        }

        // 基础配置
        if (hasBasicConfig()) {
            val basicConfig = TalkBasicConfigInfo()
            basicConfig.systemPrompt = binding.etSystemPrompt.text.toString()
            basicConfig.greetingMessage = binding.etGreetingMessage.text.toString()
            val voiceType = binding.etDefaultVoiceType.text.toString()
            if (voiceType.isNotEmpty()) {
                basicConfig.defaultVoiceType = voiceType.toIntOrNull()
            }
            config.basicConfig = basicConfig
        }

        // STT 配置
        if (hasSTTConfig()) {
            val sttConfig = TalkSTTConfigInfo()
            sttConfig.enabled = binding.switchSTTEnabled.isChecked
            sttConfig.sttType = binding.etSTTType.text.toString()
            sttConfig.config = binding.etSTTConfig.text.toString()
            config.sttConfig = sttConfig
        }

        // LLM 配置
        if (hasLLMConfig()) {
            val llmConfig = TalkLLMConfigInfo()
            llmConfig.enabled = binding.switchLLMEnabled.isChecked
            llmConfig.llmType = binding.etLLMType.text.toString()
            llmConfig.model = binding.etLLMModel.text.toString()
            llmConfig.streaming = binding.switchLLMStreaming.isChecked
            llmConfig.temperature = binding.etTemperature.text.toString().toFloatOrNull()
            llmConfig.maxTokens = binding.etMaxTokens.text.toString().toIntOrNull()
            llmConfig.topP = binding.etTopP.text.toString().toFloatOrNull()
            llmConfig.config = binding.etLLMConfig.text.toString()
            config.llmConfig = llmConfig
        }

        // TTS 配置
        if (hasTTSConfig()) {
            val ttsConfig = TalkTTSConfigInfo()
            ttsConfig.enabled = binding.switchTTSEnabled.isChecked
            ttsConfig.ttsType = binding.etTTSType.text.toString()
            ttsConfig.speed = binding.etSpeed.text.toString().toFloatOrNull()
            ttsConfig.volume = binding.etVolume.text.toString().toFloatOrNull()
            ttsConfig.pitch = binding.etPitch.text.toString().toFloatOrNull()
            ttsConfig.config = binding.etTTSConfig.text.toString()
            config.ttsConfig = ttsConfig
        }

        // 会话配置
        if (hasConversationConfig()) {
            val conversationConfig = TalkConversationConfigInfo()
            val sessionTimeout = binding.etSessionTimeout.text.toString()

            if (sessionTimeout.isNotEmpty()) {
                conversationConfig.sessionTimeout = sessionTimeout.toIntOrNull()
            }

            conversationConfig.interruptionEnabled = binding.switchInterruptionEnabled.isChecked
            val maxContextTokens = binding.etMaxContextTokens.text.toString()

            if (maxContextTokens.isNotEmpty()) {
                conversationConfig.maxContextTokens = maxContextTokens.toIntOrNull()
            }

            conversationConfig.emotionEnabled = binding.switchEmotionEnabled.isChecked
            conversationConfig.semanticVADEnabled = binding.switchSemanticVADEnabled.isChecked
            conversationConfig.noiseFilterEnabled = binding.switchNoiseFilterEnabled.isChecked

            // 空闲检测配置
            if (hasIdleDetectionConfig()) {
                val idleDetection = TalkIdleDetectionConfigInfo()
                idleDetection.enabled = binding.switchIdleEnabled.isChecked
                val timeoutSeconds = binding.etIdleTimeout.text.toString()
                if (timeoutSeconds.isNotEmpty()) {
                    idleDetection.timeoutSeconds = timeoutSeconds.toFloatOrNull()
                }
                val maxRetries = binding.etIdleMaxRetries.text.toString()
                if (maxRetries.isNotEmpty()) {
                    idleDetection.maxRetries = maxRetries.toIntOrNull()
                }

                // 空闲响应列表
                val idleResponses = mutableListOf<IdleResponseInfo>()
                for (view in idleResponseViews) {
                    val etRetryCount = view.findViewById<TextInputEditText>(R.id.etRetryCount)
                    val etResponseMessage = view.findViewById<TextInputEditText>(R.id.etResponseMessage)

                    val retryCountText = etRetryCount.text.toString()
                    val message = etResponseMessage.text.toString()

                    if (retryCountText.isNotEmpty() || message.isNotEmpty()) {
                        val response = IdleResponseInfo()
                        if (retryCountText.isNotEmpty()) {
                            response.retryCount = retryCountText.toIntOrNull()
                        }
                        response.message = message
                        idleResponses.add(response)
                    }
                }

                if (idleResponses.isNotEmpty()) {
                    idleDetection.idleResponses = idleResponses
                }

                conversationConfig.idleDetection = idleDetection
            }

            config.conversationConfig = conversationConfig
        }

        return config
    }

    /**
     * 检查是否有基础配置
     */
    private fun hasBasicConfig(): Boolean {
        return binding.etSystemPrompt.text.toString().isNotEmpty() ||
                binding.etGreetingMessage.text.toString().isNotEmpty() ||
                binding.etDefaultVoiceType.text.toString().isNotEmpty()
    }

    /**
     * 检查是否有 STT 配置
     */
    private fun hasSTTConfig(): Boolean {
        return binding.switchSTTEnabled.isChecked 
                && binding.etSTTType.text.toString().isNotEmpty() 
                && binding.etSTTConfig.text.toString().isNotEmpty()
    }

    /**
     * 检查是否有 LLM 配置
     */
    private fun hasLLMConfig(): Boolean {
        return binding.switchLLMEnabled.isChecked
                && binding.etLLMType.text.toString().isNotEmpty()
                && binding.etLLMModel.text.toString().isNotEmpty()
                && binding.etLLMConfig.text.toString().isNotEmpty()
    }

    /**
     * 检查是否有 TTS 配置
     */
    private fun hasTTSConfig(): Boolean {
        return binding.switchTTSEnabled.isChecked
                && binding.etTTSType.text.toString().isNotEmpty()
                && binding.etTTSConfig.text.toString().isNotEmpty()
    }

    /**
     * 检查是否有会话配置
     */
    private fun hasConversationConfig(): Boolean {
        return binding.switchConversationConfigEnabled.isChecked
    }

    /**
     * 检查是否有空闲检测配置
     */
    private fun hasIdleDetectionConfig(): Boolean {
        // TODO validate
        return binding.switchIdleEnabled.isChecked ||
                binding.etIdleTimeout.text.toString().isNotEmpty() ||
                binding.etIdleMaxRetries.text.toString().isNotEmpty() ||
                idleResponseViews.any { view ->
                    val etRetryCount = view.findViewById<TextInputEditText>(R.id.etRetryCount)
                    val etResponseMessage = view.findViewById<TextInputEditText>(R.id.etResponseMessage)
                    etRetryCount.text.toString().isNotEmpty() || etResponseMessage.text.toString().isNotEmpty()
                }
    }

    /**
     * 获取保存的配置（静态方法供其他 Activity 调用）
     */
    companion object {
        private const val TAG = "AiConfigActivity"
        private const val PREF_NAME = "ai_config"
        private const val KEY_CONFIG_JSON = "config_json"

        fun getSavedConfig(context: Context): TalkConfig? {
            val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val configJson = sharedPreferences.getString(KEY_CONFIG_JSON, null) ?: return null

            return try {
                TalkConfig.getFromJson(configJson)
            } catch (e: Exception) {
                null
            }
        }
    }
}
