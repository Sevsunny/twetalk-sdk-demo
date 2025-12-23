package com.tencent.twetalk_sdk_demo.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.tencent.twetalk.audio.OpusBridge
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 简易播放器
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

    // Opus 解码器
    private var opusDecoderHandle: Long = 0
    private val opusBridge = OpusBridge.getInstance()


    fun play(audio: ByteArray, sampleRate: Int, channels: Int, isPCM: Boolean = true) {
        executor.execute {
            ensureTrack(sampleRate, channels, isPCM)

            if (!isPCM) {
                // Opus 解码为 PCM
                if (opusDecoderHandle == 0L) {
                    Log.e(TAG, "OpusDecoder handle not initialized")
                    return@execute
                }

                val frameSamples = opusBridge.getFrameSamples(opusDecoderHandle, false) * channels
                val pcmOut = ShortArray(frameSamples)
                val samplesPerCh = opusBridge.decode(opusDecoderHandle, audio, pcmOut, false)

                if (samplesPerCh > 0 && track?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    val samples = samplesPerCh * channels
                    track?.write(pcmOut, 0, samples, AudioTrack.WRITE_BLOCKING)
                }
            } else {
                // PCM 直接写入
                if (track?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track?.write(audio, 0, audio.size, AudioTrack.WRITE_BLOCKING)
                }
            }
        }
    }

    private fun ensureTrack(sr: Int, ch: Int, isPCM: Boolean) {
        // 检查是否需要重建 decoder
        if (!isPCM && opusDecoderHandle == 0L) {
            initOpusDecoder(sr, ch)
        } else if (!isPCM && (currentSr != sr || currentCh != ch)) {
            // 参数变化，重建 decoder
            releaseOpusDecoder()
            initOpusDecoder(sr, ch)
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

        // 重建 track
        releaseInternal()

        currentSr = sr
        currentCh = ch

        val channelOut = when (ch) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> AudioFormat.CHANNEL_OUT_MONO
        }

        val minBuf = AudioTrack.getMinBufferSize(sr, channelOut, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = maxOf(minBuf, sr * ch * 2 / 10)

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

    private fun initOpusDecoder(sr: Int, ch: Int) {
        try {
            opusDecoderHandle = opusBridge.createDecoder(sr, ch)

            if (opusDecoderHandle == 0L) {
                Log.e(TAG, "OpusDecoder 创建失败")
                return
            }

            Log.i(TAG, "OpusDecoder 初始化成功: sr=$sr, ch=$ch")
        } catch (e: Exception) {
            Log.e(TAG, "OpusDecoder 初始化失败", e)
        }
    }

    private fun releaseOpusDecoder() {
        if (opusDecoderHandle != 0L) {
            opusBridge.releaseDecoder(opusDecoderHandle)
            opusDecoderHandle = 0
        }
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

        releaseOpusDecoder()
    }

    fun release() = executor.execute { releaseInternal() }
}