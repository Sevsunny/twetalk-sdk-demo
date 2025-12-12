package com.tencent.twetalk_sdk_demo.chat

import android.util.Log
import androidx.core.content.edit
import com.tencent.twetalk.metrics.MetricEvent
import com.tencent.twetalk.mqtt.MqttManager
import com.tencent.twetalk.protocol.AudioFormat
import com.tencent.twetalk.protocol.CallStream
import com.tencent.twetalk.protocol.CallSubType
import com.tencent.twetalk.protocol.ImageMessage
import com.tencent.twetalk.protocol.TWeTalkMessage
import com.tencent.twetalk.protocol.TweCallMessage
import com.tencent.twetalk_sdk_demo.R
import com.tencent.twetalk_sdk_demo.data.Constants
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
    private lateinit var mqttCallback: MqttManager.MqttConnectionCallback

    override fun initClient() {
        val bundle = intent.getBundleExtra(Constants.KEY_CHAT_BUNDLE)

        if (bundle == null) {
            showToast("没有读取到连接配置")
            finish()
        }

        val language = bundle?.getString(Constants.KEY_LANGUAGE, "zh")

        mqttCallback = object : MqttManager.MqttConnectionCallback {
            override fun onConnected() {
                // nothing to do
            }

            override fun onDisconnected(cause: Throwable?) {
                this@TRTCChatActivity.showToast("设备已断开连接，请尝试重新连接！")
                finish()
            }

            override fun onConnectFailed(cause: Throwable?) {
                // nothing to do
            }

            override fun onMessageReceived(
                topic: String?,
                method: String?,
                params: Map<String?, Any?>?
            ) {
                if (params == null) return
                if (method == MqttManager.REPLY_QUERY_TRTC_ROOM || method == MqttManager.REPLY_QUERY_TRTC_AI_ROOM) {
                    config = TRTCConfig(applicationContext).apply {
                        sdkAppId = params["sdk_app_id"] as String
                        userId = params["user_id"] as String
                        userSig = params["user_sig"] as String
                        privateKey = params["private_key"] as String
                        roomId = params["room_id"] as String
                    }

                    client = DefaultTRTCClient(config)
                    client.addListener(this@TRTCChatActivity)
                    client.startConversation()
                }
            }
        }

        mqttManager?.callback = mqttCallback
    }

    override fun startChat() {
        mqttManager?.queryTRTCRoom(null)
    }

    override fun stopChat() {
        mqttManager?.callback = null
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

    override fun onImageCaptured(imgMsg: ImageMessage) {
        throw UnsupportedOperationException("TRTC does not support sending image.")
    }

    // TRTC 模式暂不支持通话功能
    override fun sendDeviceAnswerMessage(roomId: String) {}
    override fun sendDeviceRejectMessage(roomId: String) {}
    override fun sendDeviceHangupForIncomingMessage(roomId: String) {}
    override fun sendDeviceHangupForOutgoingMessage() {}

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
                saveConfig()
            }

            null -> Log.e(TAG, "onStateChanged, unexpected state")
        }
    }

    override fun onRecvAudio(audio: ByteArray, sampleRate: Int, channels: Int, format: AudioFormat) {
        handleRecvAudio(audio, sampleRate, channels, format)
    }

    override fun onRecvTalkMessage(type: TWeTalkMessage.TWeTalkMessageType, text: String?) {
        handleRecvTalkMessage(type, text)
    }

    override fun onRecvCallMessage(
        stream: CallStream,
        subType: CallSubType,
        data: TweCallMessage.TweCallData
    ) {
        handleRecvCallMessage(stream, subType, data)
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

    private fun saveConfig() {
        // 绑定的设备信息
//        getSharedPreferences(Constants.KEY_DEVICE_INFO_PREF, MODE_PRIVATE).edit {
//            putString(Constants.KEY_PRODUCT_ID, config.productId)
//            putString(Constants.KEY_DEVICE_NAME, config.deviceName)
//        }
//
        // 其它连接参数信息
        getSharedPreferences(Constants.KEY_CONNECT_PARAMS_PREF, MODE_PRIVATE).edit {
            putString(Constants.KEY_LANGUAGE, config.language)
        }
    }
}