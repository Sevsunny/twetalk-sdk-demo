package com.tencent.twetalk_sdk_demo.chat

import android.util.Log
import com.tencent.twetalk.metrics.MetricEvent
import com.tencent.twetalk.protocol.TWeTalkMessage
import com.tencent.twetalk_sdk_demo.MainActivity
import com.tencent.twetalk_sdk_demo.R
import com.tencent.twetalk_sdk_trtc.config.TRTCConfig
import com.tencent.twetalk_sdk_trtc.core.DefaultTRTCClient
import com.tencent.twetalk_sdk_trtc.core.TRTCClientListener
import com.tencent.twetalk_sdk_trtc.core.TRTCClientState
import com.tencent.twetalk_sdk_trtc.core.TWeTalkTRTCClient

class TRTCChatActivity: BaseChatActivity(), TRTCClientListener {
    companion object {
        private val TAG = TRTCChatActivity::class.simpleName
    }

    private lateinit var client: TWeTalkTRTCClient
    private lateinit var config: TRTCConfig

    override fun initClient() {
        val bundle = intent.getBundleExtra(MainActivity.Companion.KEY_BUNDLE_NAME)

        if (bundle == null) {
            toast("没有读取到连接配置")
            finish()
        }

        val sdkAppId = bundle?.getInt(MainActivity.KEY_SDK_APP_ID, 0)
        val sdkSecretKey = bundle?.getString(MainActivity.KEY_SDK_SECRET_KEY, "")
        val userId = bundle?.getString(MainActivity.KEY_USER_ID, "")
        val productId = bundle?.getString(MainActivity.Companion.KEY_PRODUCT_ID, "")
        val deviceName = bundle?.getString(MainActivity.Companion.KEY_DEVICE_NAME, "")
        val language = bundle?.getString(MainActivity.Companion.KEY_LANGUAGE, "zh")

        config = TRTCConfig.Builder()
            .sdkAppId(sdkAppId!!)
            .sdkSecretKey(sdkSecretKey)
            .userId(userId)
            .productId(productId)
            .deviceName(deviceName)
            .language(language)
            .context(this)
            .useTRTCRecord(false)
            .build()

        client = DefaultTRTCClient(config)
        client.addListener(this)
    }

    override fun startChat() {
        client.startConversation()
    }

    override fun stopChat() {
        client.stopConversation()
    }

    override fun onAudioData(
        audioData: ByteArray,
        sampleRate: Int,
        channels: Int
    ) {
        if (!config.useTRTCRecord) {
            client.sendCustomAudioData(audioData, sampleRate, channels)
        }
    }

    override fun onStateChanged(state: TRTCClientState?) {
        when (state) {
            TRTCClientState.IDLE -> {}
            TRTCClientState.ENTERING -> showLoading(true, getString(R.string.entering))
            TRTCClientState.ENTERED -> showLoading(false)
            TRTCClientState.LEAVING -> showLoading(true, getString(R.string.leaving))

            TRTCClientState.LEAVED -> {
                isConnected = false
                updateConnectState()
                finish()
            }

            TRTCClientState.WAITING ->
                ConversationManager.onSystemMessage("已进入房间，等待对方进入...")

            TRTCClientState.ON_CALLING -> {
                isConnected = true
                updateConnectState()
                ConversationManager.onSystemMessage("对方已进入房间，开始对话")
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

    override fun onError(errCode: Int, errMsg: String?) {
        Log.e(TAG, "onError, errCode: $errCode, errMsg: $errMsg")
        ConversationManager.onSystemMessage("连接出现错误：$errMsg")
    }

    override fun onDestroy() {
        super.onDestroy()
        client.destroy()
    }
}