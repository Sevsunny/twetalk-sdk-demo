package com.tencent.twetalk_sdk_demo.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.tencent.twetalk.audio.OpusBridge
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 简易播放器
 */
class RemotePlayer {
    private val TAG = "RemotePlayer"
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread({
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            } catch (_: Throwable) {}
            r.run()
        }, "RemotePlayer").apply { isDaemon = true }
    }

    private var track: AudioTrack? = null
    private var currentSr = 0
    private var currentCh = 0
    private val started = AtomicBoolean(false)

    // Opus 解码器
    private var opusDecoderHandle: Long = 0
    private val opusBridge = OpusBridge.getInstance()
    private var lastWriteCostMs = 0L
    private val pcmQueue = ConcurrentLinkedDeque<ByteArray>()
    private val maxQueueBytes = 16000 * 2  // 1 秒容量（16k单声道16bit）


    fun play(audio: ByteArray, sampleRate: Int, channels: Int, isPCM: Boolean = true) {
        executor.execute {
            ensureTrack(sampleRate, channels, isPCM)

            val pcmBytes: ByteArray = if (!isPCM) {
                if (opusDecoderHandle == 0L) {
                    Log.e(TAG, "OpusDecoder handle not initialized")
                    return@execute
                }

                val frameSamples = opusBridge.getFrameSamples(opusDecoderHandle, false) * channels
                val pcmOut = ShortArray(frameSamples)
                val samplesPerCh = opusBridge.decode(opusDecoderHandle, audio, pcmOut, false)

                if (samplesPerCh <= 0) {
                    return@execute
                }

                // short[] -> byte[]
                val samples = samplesPerCh * channels
                val out = ByteArray(samples * 2)
                var idx = 0
                for (i in 0 until samples) {
                    val v = pcmOut[i].toInt()
                    out[idx++] = (v and 0xFF).toByte()
                    out[idx++] = ((v ushr 8) and 0xFF).toByte()
                }
                out
            } else {
                audio
            }

            // 入队，超出容量则丢弃最老的帧，防止延迟累积
            while (queueBytes() + pcmBytes.size > maxQueueBytes) {
                pcmQueue.poll()
            }
            pcmQueue.offer(pcmBytes)

            // 唤起播放循环
            drainQueueNonBlocking()
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
        val targetBuf = sr * ch * 2 / 5 // 约 200ms buffer
        val bufferSize = maxOf(minBuf * 2, targetBuf)

        // 通话场景使用 VOICE_COMMUNICATION 属性，尽量与录音路由一致
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sr)
            .setChannelMask(channelOut)
            .build()

        track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track?.play()
        started.set(true)
        Log.i(TAG, "AudioTrack init: sr=$sr, ch=$ch, bufferSize=$bufferSize, minBuf=$minBuf, lastWriteCostMs=$lastWriteCostMs")
        // 初次启动尝试预充多帧，减少首包卡顿
        drainQueueNonBlocking(preloadOnly = true, maxFrames = 5)
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
            pcmQueue.clear()
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
        pcmQueue.clear()

        releaseOpusDecoder()
    }

    fun release() = executor.execute { releaseInternal() }

    private fun queueBytes(): Int {
        var sum = 0
        pcmQueue.forEach { sum += it.size }
        return sum
    }

    /**
     * 将队列数据以非阻塞方式写出，避免 100ms 阻塞。
     * preloadOnly 为 true 时只写 maxFrames 帧用于预充，避免过多写入阻塞 UI。
     */
    private fun drainQueueNonBlocking(preloadOnly: Boolean = false, maxFrames: Int = 50) {
        val t = track ?: return
        var writtenFrames = 0
        
        while (writtenFrames < maxFrames) {
            val chunk = pcmQueue.poll() ?: break
            var offset = 0
            var remaining = chunk.size
            
            // 循环写入直到全部写入或缓冲区满
            while (remaining > 0) {
                val res = t.write(chunk, offset, remaining, AudioTrack.WRITE_NON_BLOCKING)

                if (res < 0) {
                    Log.w(TAG, "AudioTrack write failed: $res, remaining=$remaining")
                    // 写入错误，放回队列头部
                    if (remaining < chunk.size) {
                        pcmQueue.offerFirst(chunk.copyOfRange(offset, chunk.size))
                    } else {
                        pcmQueue.offerFirst(chunk)
                    }
                    return
                } else if (res == 0) {
                    // 缓冲区满，放回队列头部稍后再试
                    pcmQueue.offerFirst(chunk.copyOfRange(offset, chunk.size))
                    return
                }

                offset += res
                remaining -= res
            }
            
            writtenFrames++
            if (preloadOnly && writtenFrames >= maxFrames) break
        }
    }
}