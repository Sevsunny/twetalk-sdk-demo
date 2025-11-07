package com.tencent.twetalk_sdk_demo.chat

import android.util.Log
import androidx.core.content.edit
import com.tencent.twetalk.core.ConnectionState
import com.tencent.twetalk.core.DefaultTWeTalkClient
import com.tencent.twetalk.core.TWeTalkClient
import com.tencent.twetalk.core.TWeTalkClientListener
import com.tencent.twetalk.core.TWeTalkConfig
import com.tencent.twetalk.metrics.MetricEvent
import com.tencent.twetalk.protocol.TWeTalkMessage
import com.tencent.twetalk_sdk_demo.R
import com.tencent.twetalk_sdk_demo.data.Constants

class WebSocketChatActivity : BaseChatActivity(), TWeTalkClientListener {
    companion object {
        private val TAG = WebSocketChatActivity::class.simpleName
    }

    private lateinit var client: TWeTalkClient
    private lateinit var config: TWeTalkConfig

    override fun initClient() {
        val bundle = intent.getBundleExtra(Constants.KEY_CHAT_BUNDLE)

        if (bundle == null) {
            toast("没有读取到连接配置")
            finish()
        }

        val secretId = bundle?.getString(Constants.KEY_SECRET_ID, "")
        val secretKey = bundle?.getString(Constants.KEY_SECRET_KEY, "")
        val productId = bundle?.getString(Constants.KEY_PRODUCT_ID, "")
        val deviceName = bundle?.getString(Constants.KEY_DEVICE_NAME, "")
        val audioType = bundle?.getString(Constants.KEY_AUDIO_TYPE, "PCM")
        val language = bundle?.getString(Constants.KEY_LANGUAGE, "zh")

        val authConfig = TWeTalkConfig.AuthConfig(
            secretId,
            secretKey,
            productId,
            deviceName,
            audioType,
            language
        ).apply {
            baseUrl = "ws://iot-twetalk-webrtc-test.tencentiotcloud.com/ws"
        }

        config = TWeTalkConfig.builder()
            .authConfig(authConfig)
            .isMetricOpen(true)
            .build()

        client = DefaultTWeTalkClient(config)
        client.addListener(this)
    }

    override fun startChat() {
        client.connect()
    }

    override fun stopChat() {
        client.disconnect()
    }

    override fun onAudioData(audioData: ByteArray, sampleRate: Int, channels: Int) {
        client.sendCustomAudioData(audioData, sampleRate, channels)
    }

    override fun onStateChanged(state: ConnectionState) {
//        Log.d(TAG, "onStateChanged: $state")

        when (state) {
            ConnectionState.IDLE -> {}
            ConnectionState.CONNECTING -> showLoading(true)

            ConnectionState.CONNECTED -> {
                showLoading(false)
                isConnected = true
                updateConnectState()

                // 添加欢迎消息
                ConversationManager.onSystemMessage("连接已建立，开始对话")

                // 保存参数
                saveConfig()
            }

            ConnectionState.RECONNECTING -> showLoading(true, getString(R.string.reconnecting))
            ConnectionState.CLOSING -> showLoading(true, getString(R.string.closing))

            ConnectionState.CLOSED -> {
                isConnected = false
                updateConnectState()
                finish()
            }

            null -> Log.e(TAG, "onStateChanged, unexpected state")
        }
    }

    override fun onRecvMessage(message: TWeTalkMessage) {
        handleMessage(message!!)
    }

    override fun onMetrics(metrics: MetricEvent?) {
        if (metrics?.type == MetricEvent.Type.RTT) {
            Log.d(TAG, "onMetrics: $metrics")
        }
    }

    override fun onError(error: Throwable?) {
        Log.e(TAG, "onError", error)
        ConversationManager.onSystemMessage("连接出现错误，对话已结束")
    }

    override fun onDestroy() {
        super.onDestroy()
        client.close()
    }

    private fun saveConfig() {
        // 绑定的设备信息
        getSharedPreferences(Constants.KEY_DEVICE_INFO_PREF, MODE_PRIVATE).edit {
            putString(Constants.KEY_PRODUCT_ID, config.authConfig.productId)
            putString(Constants.KEY_DEVICE_NAME, config.authConfig.deviceName)
        }

        // 其它连接参数信息
        getSharedPreferences(Constants.KEY_CONNECT_PARAMS_PREF, MODE_PRIVATE).edit {
            putString(Constants.KEY_AUDIO_TYPE, config.authConfig.audioType)
            putString(Constants.KEY_LANGUAGE, config.authConfig.language)
        }

        // 密钥
        securePrefs.edit {
            putString(Constants.KEY_SECRET_ID, config.authConfig.secretId)
            putString(Constants.KEY_SECRET_KEY, config.authConfig.secretKey)
        }
    }
}