package com.tencent.twetalk_sdk_demo.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.tencent.twetalk.audio.OpusBridge
import com.tencent.twetalk.audio.model.OpusEncoderParams
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 简易的 PCM 16-bit 播放器（小端）
 * TODO OPUS 解码
 */
class RemotePlayer {
    private val TAG = "RemotePlayer"
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "RemotePlayer").apply { isDaemon = true }
    }

    private var track: AudioTrack? = null
    private var currentSr = 0
    private var currentCh = 0
    private val started = AtomicBoolean(false)
    private val opusBridge = OpusBridge.getInstance()
    private var isOpusInitialed = false

    fun play(audio: ByteArray, sampleRate: Int, channels: Int, isPCM: Boolean = true) {
        executor.execute {
            ensureTrack(sampleRate, channels, isPCM)

            if (!isPCM && isOpusInitialed) {
                val pcmOut = ShortArray(OpusBridge.frameSamples(sampleRate) * channels)
                val samples = opusBridge.decode(audio, pcmOut)

                if (samples > 0 && track?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track?.write(pcmOut, 0, pcmOut.size, AudioTrack.WRITE_BLOCKING)
                }
            }

            if (isPCM && track?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track?.write(audio, 0, audio.size, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    private fun ensureTrack(sr: Int, ch: Int, isPCM: Boolean) {
        if (!isPCM && !isOpusInitialed) {
            initOpus(sr, ch)

            if (!isOpusInitialed) {
                throw RuntimeException("Opus 解码器初始化失败")
            }
        }

        if (track != null && track?.state == AudioTrack.STATE_INITIALIZED && sr == currentSr && ch == currentCh) {
            try {
                if (started.compareAndSet(false, true)) {
                    track?.play()
                } else if (track?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track?.play()
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "ensureTrack: play error ${e.message}")
            }

            return
        }

        // 需要重建或首次创建
        releaseInternal()

        // 使用 opus 时重新 init
        if (!isPCM && isOpusInitialed) {
            initOpus(sr, ch)

            if (!isOpusInitialed) {
                return
            }
        }

        currentSr = sr
        currentCh = ch

        val channelOut = when (ch) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> AudioFormat.CHANNEL_OUT_MONO // 不支持的声道数时回退为单声道
        }

        val minBuf = AudioTrack.getMinBufferSize(sr, channelOut, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = maxOf(minBuf, sr * ch * 2 / 10) // 预留约 100ms buffer

        track = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sr,
            channelOut,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STREAM
        )
        track?.play()
        started.set(true)
    }

    private fun initOpus(sr: Int, ch: Int) {
        val encoderRes = opusBridge.initEncoder(
            OpusEncoderParams.Builder()
                .sampleRate(sr)
                .channels(ch)
                .build()
        )

        val decoderRes = opusBridge.initDecoder(sr, ch)

        if (!decoderRes || !encoderRes) {
            Log.e(TAG, "OpusBridge 初始化失败")
            return
        }

        isOpusInitialed = true
    }

    fun stop() {
        executor.execute {
            track?.pause()
            track?.flush()
            track?.stop()
        }
    }

    private fun releaseInternal() {
        started.set(false)

        track?.let { t ->
            try {
                t.pause()
                t.flush()
                t.stop()
            } catch (_: Throwable) {}
        }

        track = null
        currentSr = 0
        currentCh = 0

        try {
            opusBridge.releaseEncoder()
            opusBridge.releaseDecoder()
            isOpusInitialed = false
        } catch (_: Throwable) {}
    }

    fun release() = executor.execute { releaseInternal() }
}