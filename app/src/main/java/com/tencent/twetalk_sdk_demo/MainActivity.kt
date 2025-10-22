package com.tencent.twetalk_sdk_demo

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.card.MaterialCardView
import com.tencent.twetalk.core.TWeTalkConfig
import com.tencent.twetalk_sdk_demo.chat.TRTCChatActivity
import com.tencent.twetalk_sdk_demo.chat.WebSocketChatActivity
import com.tencent.twetalk_sdk_demo.databinding.ActivityMainBinding

class MainActivity : BaseActivity<ActivityMainBinding>() {
    private var selectedLanguage = "zh"
    private var connectionType = TWeTalkConfig.TransportType.WEBSOCKET
    private var sharedPreferences: SharedPreferences? = null

    override fun getViewBinding() = ActivityMainBinding.inflate(layoutInflater)

    override fun initView() {
        if (SettingsActivity.isAutoSaveEnabled(this)) {
            sharedPreferences = getSharedPreferences("connection_info", MODE_PRIVATE)
        }

        loadDefaultConfig()
        setupToolbar()
        setupLanguageSelection()
        setupConnectionTypeSelection()
        setupConnectButton()
        setupNavigationButtons()

        // 默认选择中文和WebSocket
        selectConnectionType(TWeTalkConfig.TransportType.WEBSOCKET)
    }

    private fun loadDefaultConfig() {
        with(binding) {
            etProductId.setText(sharedPreferences?.getString("productId", null)
                ?: BuildConfig.productId)
            etDeviceName.setText(sharedPreferences?.getString("deviceName", null)
                ?: BuildConfig.deviceName)
            etSecretId.setText(sharedPreferences?.getString("secretId", null)
                ?: BuildConfig.secretId)
            etSecretKey.setText(sharedPreferences?.getString("secretKey", null)
                ?: BuildConfig.secretKey)
            etSdkAppId.setText(sharedPreferences?.getString("sdkAppId", null)
                ?: BuildConfig.sdkAppId)
            etSdkSecretKey.setText(sharedPreferences?.getString("sdkSecretKey", null)
                ?: BuildConfig.sdkSecretKey)
            etUserId.setText(sharedPreferences?.getString("userId", null)
                ?: BuildConfig.userId)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    private fun setupLanguageSelection() {
        val languageValues = resources.getStringArray(R.array.language_values)
        
        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedLanguage = languageValues[position]
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // 默认选择第一个选项（中文）
                selectedLanguage = languageValues[0]
            }
        }
        
        // 设置默认选择
        binding.spinnerLanguage.setSelection(0)
    }

    private fun setupConnectionTypeSelection() {
        binding.cardWebSocket.setOnClickListener {
            selectConnectionType(TWeTalkConfig.TransportType.WEBSOCKET)
        }

        binding.cardTRTC.setOnClickListener {
            selectConnectionType(TWeTalkConfig.TransportType.TRTC)
        }
    }

    private fun selectConnectionType(type: TWeTalkConfig.TransportType) {
        connectionType = type
        
        // 重置所有卡片样式
        resetCardSelection(binding.cardWebSocket)
        resetCardSelection(binding.cardTRTC)
        
        // 设置选中的卡片样式
        when (type) {
            TWeTalkConfig.TransportType.WEBSOCKET -> {
                setCardSelected(binding.cardWebSocket)
                showWebSocketParams()
            }

            TWeTalkConfig.TransportType.TRTC -> {
                setCardSelected(binding.cardTRTC)
                showTRTCParams()
            }
        }
    }

    private fun resetCardSelection(card: MaterialCardView) {
        card.strokeWidth = 0
        card.cardElevation = 2f
    }

    private fun setCardSelected(card: MaterialCardView) {
        card.strokeWidth = 4
        card.strokeColor = ContextCompat.getColor(this, R.color.primary_blue)
        card.cardElevation = 8f
    }

    private fun showWebSocketParams() {
        binding.layoutWebSocketParams.visibility = View.VISIBLE
        binding.layoutTRTCParams.visibility = View.GONE
        binding.cardAudioFormat.visibility = View.VISIBLE
    }

    private fun showTRTCParams() {
        binding.layoutWebSocketParams.visibility = View.GONE
        binding.layoutTRTCParams.visibility = View.VISIBLE

        if (binding.rbOpus.isChecked) {
            binding.rbOpus.isChecked = false
            binding.rbPCM.isChecked = true
        }

        binding.cardAudioFormat.visibility = View.GONE
    }

    private fun setupConnectButton() {
        binding.fabConnect.setOnClickListener {
            connect()
        }
    }

    private fun connect() {
        if (!validateInputs()) {
            return
        }

        // 跳转到聊天界面
        val bundle = Bundle().apply {
            putString(KEY_CONNECTION_TYPE, connectionType.name)
            putString(KEY_AUDIO_TYPE, getSelectedAudioType())
            putString(KEY_LANGUAGE, selectedLanguage)
            putString(KEY_PRODUCT_ID, binding.etProductId.text.toString())
            putString(KEY_DEVICE_NAME, binding.etDeviceName.text.toString())
        }

        val intent = when (connectionType) {
            TWeTalkConfig.TransportType.WEBSOCKET -> {
                bundle.putString(KEY_SECRET_ID, binding.etSecretId.text.toString())
                bundle.putString(KEY_SECRET_KEY, binding.etSecretKey.text.toString())

                Intent(this@MainActivity, WebSocketChatActivity::class.java)
                    .putExtra(KEY_BUNDLE_NAME, bundle)
            }

            TWeTalkConfig.TransportType.TRTC -> {
                bundle.putString(KEY_SDK_APP_ID, binding.etSdkAppId.text.toString())
                bundle.putInt(KEY_SDK_APP_ID, binding.etSdkAppId.text.toString().trim().toIntOrNull() ?: 0)
                bundle.putString(KEY_SDK_SECRET_KEY, binding.etSdkSecretKey.text.toString())

                // userId 选填，如果没有则不传，默认会取 productId_deviceName
                if (!binding.etUserId.text.isNullOrBlank()) {
                    bundle.putString(KEY_USER_ID, binding.etUserId.text.toString())
                }

                Intent(this@MainActivity, TRTCChatActivity::class.java)
                    .putExtra(KEY_BUNDLE_NAME, bundle)
            }
        }

        startActivity(intent)

        sharedPreferences?.edit {

        }
    }

    private fun validateInputs(): Boolean {
        // 验证通用参数
        if (binding.etProductId.text.isNullOrBlank()) {
            binding.etProductId.error = getString(R.string.required_field)
            return false
        }

        if (binding.etDeviceName.text.isNullOrBlank()) {
            binding.etDeviceName.error = getString(R.string.required_field)
            return false
        }

        // 验证特定连接方式的参数
        when (connectionType) {
            TWeTalkConfig.TransportType.WEBSOCKET -> {
                if (binding.etSecretId.text.isNullOrBlank()) {
                    binding.etSecretId.error = getString(R.string.required_field)
                    return false
                }

                if (binding.etSecretKey.text.isNullOrBlank()) {
                    binding.etSecretKey.error = getString(R.string.required_field)
                    return false
                }
            }

            TWeTalkConfig.TransportType.TRTC -> {
                if (binding.etSdkAppId.text.isNullOrBlank()) {
                    binding.etSdkAppId.error = getString(R.string.required_field)
                    return false
                }

                if (binding.etSdkSecretKey.text.isNullOrBlank()) {
                    binding.etSdkSecretKey.error = getString(R.string.required_field)
                    return false
                }
            }
        }

        return true
    }

    private fun getSelectedAudioType(): String {
        return when (binding.rgAudioFormat.checkedRadioButtonId) {
            R.id.rbOpus -> "OPUS"
            R.id.rbPCM -> "PCM"
            else -> "OPUS"
        }
    }

    private fun setupNavigationButtons() {
        binding.btnAiConfig.setOnClickListener {
            val intent = Intent(this, AiConfigActivity::class.java)
            startActivity(intent)
        }

        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        binding.btnHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
    }

    companion object {
        private val TAG = MainActivity::class.simpleName

        const val KEY_BUNDLE_NAME = "connection_config"
        const val KEY_CONNECTION_TYPE = "connection_type"
        const val KEY_AUDIO_TYPE = "audio_type"
        const val KEY_LANGUAGE = "language"
        const val KEY_PRODUCT_ID = "product_id"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_SECRET_ID = "secret_id"
        const val KEY_SECRET_KEY = "secret_key"
        const val KEY_SDK_APP_ID = "sdk_app_id"
        const val KEY_SDK_SECRET_KEY = "sdk_secret_key"
        const val KEY_USER_ID = "user_id"
    }
}