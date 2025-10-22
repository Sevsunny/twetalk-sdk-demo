package com.tencent.twetalk_sdk_demo.chat

import android.util.Log
import com.tencent.twetalk.core.ConnectionState
import com.tencent.twetalk.core.DefaultTWeTalkClient
import com.tencent.twetalk.core.TWeTalkClient
import com.tencent.twetalk.core.TWeTalkClientListener
import com.tencent.twetalk.core.TWeTalkConfig
import com.tencent.twetalk.metrics.MetricEvent
import com.tencent.twetalk.protocol.TWeTalkMessage
import com.tencent.twetalk_sdk_demo.MainActivity
import com.tencent.twetalk_sdk_demo.R

class WebSocketChatActivity : BaseChatActivity(), TWeTalkClientListener {
    companion object {
        private val TAG = WebSocketChatActivity::class.simpleName
    }

    private lateinit var client: TWeTalkClient
    private lateinit var config: TWeTalkConfig

    override fun initClient() {
        val bundle = intent.getBundleExtra(MainActivity.Companion.KEY_BUNDLE_NAME)

        if (bundle == null) {
            toast("没有读取到连接配置")
            finish()
        }

        val secretId = bundle?.getString(MainActivity.Companion.KEY_SECRET_ID, "")
        val secretKey = bundle?.getString(MainActivity.Companion.KEY_SECRET_KEY, "")
        val productId = bundle?.getString(MainActivity.Companion.KEY_PRODUCT_ID, "")
        val deviceName = bundle?.getString(MainActivity.Companion.KEY_DEVICE_NAME, "")
        val audioType = bundle?.getString(MainActivity.Companion.KEY_AUDIO_TYPE, "opus")
        val language = bundle?.getString(MainActivity.Companion.KEY_LANGUAGE, "zh")

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

    override fun onStateChanged(state: ConnectionState?) {
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

    override fun onRecvMessage(message: TWeTalkMessage?) {
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
}