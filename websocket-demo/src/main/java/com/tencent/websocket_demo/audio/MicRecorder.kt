package com.tencent.websocket_demo.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock

class MicRecorder(
    private val sampleRate: Int = 16000,
    private val chunkMs: Int = 20, // 20ms -> 640 bytes @16k/16bit/mono
    private val onChunk: (bytes: ByteArray, startTime: Long) -> Unit
) {
    private var recorder: AudioRecord? = null
    @Volatile private var running = false
    private var startElapsedMs: Long = 0

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val frameBytes = (sampleRate * 2 /*16bit*/ * chunkMs) / 1000
        val bufferSize = maxOf(minBuf, frameBytes * 4)

        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        require(recorder?.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }

        running = true
        recorder?.startRecording()
        startElapsedMs = SystemClock.elapsedRealtime()

        Thread({
            val chunk = ByteArray(frameBytes)
            while (running) {
                val read = recorder?.read(chunk, 0, chunk.size) ?: break
                if (read > 0) {
                    onChunk(if (read == chunk.size) chunk.copyOf() else chunk.copyOf(read), startElapsedMs)
                }
            }
        }, "MicRecorder").start()
    }

    fun stop() {
        running = false
        try { recorder?.stop() } catch (_: Throwable) {}
        try { recorder?.release() } catch (_: Throwable) {}
        recorder = null
    }
}
