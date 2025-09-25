package com.tencent.websocket_demo.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 简易的 PCM 16-bit 播放器（小端），以单线程写入 AudioTrack，避免阻塞回调线程。
 */
class RemotePcmPlayer {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "RemotePcmPlayer").apply { isDaemon = true }
    }

    private var track: AudioTrack? = null
    private var currentSr = 0
    private var currentCh = 0
    private val started = AtomicBoolean(false)

    fun play(pcm: ByteArray, sampleRate: Int, channels: Int) {
        executor.execute {
            ensureTrack(sampleRate, channels)
            track?.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
        }
    }

    private fun ensureTrack(sr: Int, ch: Int) {
        if (track != null && sr == currentSr && ch == currentCh) {
            if (started.compareAndSet(false, true)) {
                track?.play()
            } else if (track?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                track?.play()
            }
            return
        }

        // 需要重建或首次创建
        releaseInternal()
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

    fun stop() {
        executor.execute { releaseInternal() }
    }

    private fun releaseInternal() {
        started.set(false)
        try { track?.stop() } catch (_: Throwable) {}
        try { track?.flush() } catch (_: Throwable) {}
        try { track?.release() } catch (_: Throwable) {}
        track = null
        currentSr = 0
        currentCh = 0
    }

    fun release() = stop()
}