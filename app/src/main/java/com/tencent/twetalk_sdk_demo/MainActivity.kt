package com.tencent.twetalk_sdk_demo

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.card.MaterialCardView
import com.tencent.twetalk.core.TWeTalkConfig
import com.tencent.twetalk_sdk_demo.chat.TRTCChatActivity
import com.tencent.twetalk_sdk_demo.chat.WebSocketChatActivity
import com.tencent.twetalk_sdk_demo.data.Constants
import com.tencent.twetalk_sdk_demo.databinding.ActivityMainBinding

class MainActivity : BaseActivity<ActivityMainBinding>() {
    private var selectedLanguage = "zh"
    private var connectionType = TWeTalkConfig.TransportType.WEBSOCKET

    override fun getViewBinding() = ActivityMainBinding.inflate(layoutInflater)

    override fun initView() {
        setupToolbar()
        setupLanguageSelection()
        setupConnectionTypeSelection()
        setupConnectButton()
        setupNavigationButtons()
        loadDefaultConfig()
    }

    private fun loadDefaultConfig() {
        with(binding) {
            getSharedPreferences(Constants.KEY_CONNECT_PARAMS_PREF, MODE_PRIVATE).run {
                val connectionTypeStr = this.getString(Constants.KEY_CONNECTION_TYPE, "WEBSOCKET")

                if (connectionTypeStr == "TRTC") {
                    selectConnectionType(TWeTalkConfig.TransportType.TRTC)
                } else {
                    selectConnectionType(TWeTalkConfig.TransportType.WEBSOCKET)
                }

                // WebSocket 其它参数反向渲染
                val audioType = this.getString(Constants.KEY_AUDIO_TYPE, "PCM")

                if (audioType == "OPUS") {
                    rbOpus.isChecked = true
                    rbPCM.isChecked = false
                } else {
                    rbOpus.isChecked = false
                    rbPCM.isChecked = true
                }

                val isVideoMode = this.getBoolean(Constants.KEY_VIDEO_MODE, false)
                switchVideoChat.isChecked = isVideoMode

                // TRTC 其它参数反向渲染
                etUserId.setText(this.getString(Constants.KEY_USER_ID, null) ?: BuildConfig.userId)

                val language = this.getString(Constants.KEY_LANGUAGE, "zh")

                if (language == "en") {
                    spinnerLanguage.setSelection(1)
                } else {
                    spinnerLanguage.setSelection(0)
                }
            }

            // 设备信息反向渲染
            getSharedPreferences(Constants.KEY_DEVICE_INFO_PREF, MODE_PRIVATE).run {
                etProductId.setText(this.getString(Constants.KEY_PRODUCT_ID, null) ?: BuildConfig.productId)
                etDeviceName.setText(this.getString(Constants.KEY_DEVICE_NAME, null) ?: BuildConfig.deviceName)
            }

            // 密钥信息反向渲染
            val masterKey = MasterKey.Builder(this@MainActivity)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                this@MainActivity,
                Constants.KEY_SECRET_INFO_PREF,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).run {
                etSecretId.setText(this.getString(Constants.KEY_SECRET_ID, null) ?: BuildConfig.secretId)
                etSecretKey.setText(this.getString(Constants.KEY_SECRET_KEY, null) ?: BuildConfig.secretKey)
                etSdkAppId.setText(this.getString(Constants.KEY_SDK_APP_ID, null) ?: BuildConfig.sdkAppId)
                etSdkSecretKey.setText(this.getString(Constants.KEY_SDK_SECRET_KEY, null) ?: BuildConfig.sdkSecretKey)
            }
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
        binding.cardVideoChat.visibility = View.VISIBLE
    }

    private fun showTRTCParams() {
        binding.layoutWebSocketParams.visibility = View.GONE
        binding.layoutTRTCParams.visibility = View.VISIBLE

        if (binding.rbOpus.isChecked) {
            binding.rbOpus.isChecked = false
            binding.rbPCM.isChecked = true
        }

        binding.cardAudioFormat.visibility = View.GONE

        // TRTC 连接时暂不支持视频聊天
        binding.switchVideoChat.isChecked = false
        binding.cardVideoChat.visibility = View.GONE
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
            putString(Constants.KEY_CONNECTION_TYPE, connectionType.name)
            putString(Constants.KEY_AUDIO_TYPE, getSelectedAudioType())
            putString(Constants.KEY_PRODUCT_ID, binding.etProductId.text.toString())
            putString(Constants.KEY_DEVICE_NAME, binding.etDeviceName.text.toString())
            putString(Constants.KEY_LANGUAGE, selectedLanguage)
            putBoolean(Constants.KEY_VIDEO_MODE, binding.switchVideoChat.isChecked)
        }

        val intent = when (connectionType) {
            TWeTalkConfig.TransportType.WEBSOCKET -> {
                bundle.putString(Constants.KEY_SECRET_ID, binding.etSecretId.text.toString())
                bundle.putString(Constants.KEY_SECRET_KEY, binding.etSecretKey.text.toString())

                Intent(this@MainActivity, WebSocketChatActivity::class.java)
                    .putExtra(Constants.KEY_CHAT_BUNDLE, bundle)
            }

            TWeTalkConfig.TransportType.TRTC -> {
                bundle.putString(Constants.KEY_SDK_APP_ID, binding.etSdkAppId.text.toString())
                bundle.putInt(Constants.KEY_SDK_APP_ID, binding.etSdkAppId.text.toString().trim().toIntOrNull() ?: 0)
                bundle.putString(Constants.KEY_SDK_SECRET_KEY, binding.etSdkSecretKey.text.toString())

                // userId 选填，如果没有则不传，默认会取 productId_deviceName
                if (!binding.etUserId.text.isNullOrBlank()) {
                    bundle.putString(Constants.KEY_USER_ID, binding.etUserId.text.toString())
                }

                Intent(this@MainActivity, TRTCChatActivity::class.java)
                    .putExtra(Constants.KEY_CHAT_BUNDLE, bundle)
            }
        }

        // 保存上次连接的方式
        getSharedPreferences(Constants.KEY_CONNECT_PARAMS_PREF, MODE_PRIVATE).edit {
            putString(Constants.KEY_CONNECTION_TYPE, connectionType.name)
        }

        startActivity(intent)
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
    }
}