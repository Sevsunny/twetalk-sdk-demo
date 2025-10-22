package com.tencent.twetalk_sdk_demo.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import com.tencent.twetalk.audio.OpusBridge
import com.tencent.twetalk.audio.model.OpusEncoderParams

/**
 * 音频采集
 */
class MicRecorder(
    private val config: AudioConfig,
    private val onAudioData: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "MicRecorder"
    }

    // AudioRecord 相关
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    
    // 音频效果处理器
    private var aec: AcousticEchoCanceler? = null
    private var agc: AutomaticGainControl? = null
    private var ns: NoiseSuppressor? = null
    
    // Opus 编码器
    private var opusEncoder: MediaCodec? = null
    private var opusBridge: OpusBridge? = null
    
    // 文件写入器
    private var fileWriter: AudioFileWriter? = null
    
    // 状态标志
    @Volatile private var isInitialized = false
    @Volatile private var isRecording = false
    
    // 音频参数
    private var frameBytes: Int = 0
    private var bufferSize: Int = 0

    /**
     * 初始化音频采集器
     * 
     * @throws IllegalStateException 如果已经初始化
     * @throws IllegalArgumentException 如果配置无效
     * @throws UnsupportedOperationException 如果设备不支持所需功能
     */
    @SuppressLint("MissingPermission")
    @Throws(IllegalStateException::class, IllegalArgumentException::class, UnsupportedOperationException::class)
    fun init() {
        if (isInitialized) {
            throw IllegalStateException("MicRecorder 已经初始化，请先调用 release()")
        }
        
        // 验证配置
        config.validate().getOrThrow()
        
        // 检查设备能力
        checkDeviceCapabilities()
        
        try {
            // 初始化 AudioRecord
            initAudioRecord()
            
            // 初始化音频效果处理器
            initAudioEffects()
            
            // 初始化编码器（如果需要）
            if (config.formatType == AudioFormatType.OPUS) {
                initOpusBridge()
//                if (AudioCapabilityDetector.isOpusSupported()) {
//                    initOpusEncoder()
//                } else {
//                    initOpusBridge()
//                }
            }
            
            // 初始化文件写入器（如果需要）
            if (config.saveToFile && config.filePath != null) {
                fileWriter = AudioFileWriter(config.filePath)
                fileWriter?.open()
            }
            
            isInitialized = true
            Log.i(TAG, "MicRecorder 初始化成功")
            
        } catch (e: Exception) {
            // 初始化失败，清理资源
            releaseInternal()
            throw e
        }
    }

    /**
     * 开始录音
     * 
     * @throws IllegalStateException 如果未初始化或已经在录音
     */
    fun start() {
        if (!isInitialized) {
            throw IllegalStateException("MicRecorder 未初始化，请先调用 init()")
        }

        if (isRecording) {
            throw IllegalStateException("MicRecorder 已经在录音中")
        }
        
        isRecording = true
        audioRecord?.startRecording()
        
        // 启动录音线程
        recordThread = Thread({
            recordLoop()
        }, "MicRecorder-Thread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        
        Log.i(TAG, "开始录音")
    }

    /**
     * 停止录音
     */
    fun stop() {
        if (!isRecording) {
            return
        }
        
        isRecording = false
        
        // 等待录音线程结束
        recordThread?.join(1000)
        recordThread = null
        
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "停止录音失败", e)
        }
        
        Log.i(TAG, "停止录音")
    }

    /**
     * 释放资源
     */
    fun release() {
        if (!isInitialized) {
            return
        }
        
        // 确保已停止录音
        if (isRecording) {
            stop()
        }
        
        releaseInternal()
        isInitialized = false
        
        Log.i(TAG, "MicRecorder 已释放")
    }

    /**
     * 检查设备能力
     */
    private fun checkDeviceCapabilities() {
        val report = AudioCapabilityDetector.getCapabilityReport()
        
        // 检查 Opus 支持
        if (config.formatType == AudioFormatType.OPUS && !report.opusSupported) {
            Log.w(TAG, "设备不支持 Opus 硬件编码。将启用 OpusBridge")
        }
        
        // 检查 AEC 支持
        if (config.enableAEC && !report.aecSupported) {
            Log.w(TAG, "设备不支持 AEC（回声消除），该功能将被禁用")
        }
        
        // 检查 AGC 支持
        if (config.enableAGC && !report.agcSupported) {
            Log.w(TAG, "设备不支持 AGC（自动增益控制），该功能将被禁用")
        }
        
        // 检查 NS 支持
        if (config.enableNS && !report.nsSupported) {
            Log.w(TAG, "设备不支持 NS（噪声抑制），该功能将被禁用")
        }
    }

    /**
     * 初始化 AudioRecord
     */
    @SuppressLint("MissingPermission")
    private fun initAudioRecord() {
        val channelConfig = if (config.channelCount == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }
        
        val audioFormat = if (config.bitDepth == 16) {
            AudioFormat.ENCODING_PCM_16BIT
        } else {
            AudioFormat.ENCODING_PCM_8BIT
        }
        
        // 计算帧大小
        val bytesPerSample = config.bitDepth / 8
        frameBytes = (config.sampleRate * bytesPerSample * config.channelCount * config.chunkMs) / 1000
        
        // 计算缓冲区大小
        val minBuf = AudioRecord.getMinBufferSize(config.sampleRate, channelConfig, audioFormat)
        bufferSize = maxOf(minBuf, frameBytes * 4)
        
        // 创建 AudioRecord
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            config.sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord 初始化失败")
        }
        
        Log.i(TAG, "AudioRecord 初始化成功: sampleRate=${config.sampleRate}, " +
                "channels=${config.channelCount}, bitDepth=${config.bitDepth}, " +
                "frameBytes=$frameBytes, bufferSize=$bufferSize")
    }

    /**
     * 初始化音频效果处理器
     */
    private fun initAudioEffects() {
        val audioSessionId = audioRecord?.audioSessionId ?: return
        
        // 初始化 AEC（回声消除）
        if (config.enableAEC && AudioCapabilityDetector.isAECSupported()) {
            try {
                aec = AcousticEchoCanceler.create(audioSessionId)
                aec?.enabled = true
                Log.i(TAG, "AEC（回声消除）已启用")
            } catch (e: Exception) {
                Log.e(TAG, "AEC 初始化失败", e)
            }
        }
        
        // 初始化 AGC（自动增益控制）
        if (config.enableAGC && AudioCapabilityDetector.isAGCSupported()) {
            try {
                agc = AutomaticGainControl.create(audioSessionId)
                agc?.enabled = true
                Log.i(TAG, "AGC（自动增益控制）已启用")
            } catch (e: Exception) {
                Log.e(TAG, "AGC 初始化失败", e)
            }
        }
        
        // 初始化 NS（噪声抑制）
        if (config.enableNS && AudioCapabilityDetector.isNSSupported()) {
            try {
                ns = NoiseSuppressor.create(audioSessionId)
                ns?.enabled = true
                Log.i(TAG, "NS（噪声抑制）已启用")
            } catch (e: Exception) {
                Log.e(TAG, "NS 初始化失败", e)
            }
        }
    }

    /**
     * 初始化 Opus 编码器
     */
    private fun initOpusEncoder() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            throw UnsupportedOperationException("Opus 编码需要 Android 5.0 (API 21) 及以上版本")
        }

        try {
            // 创建 Opus 编码器
            opusEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            
            // 配置编码器
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                config.sampleRate,
                config.channelCount
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, AudioConfig.OpusConfig.BITRATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, frameBytes)
                
                // Opus 特定配置：60ms 一帧
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setInteger(
                        MediaFormat.KEY_PCM_ENCODING,
                        if (config.bitDepth == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT
                    )
                }
            }
            
            opusEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            opusEncoder?.start()
            
            Log.i(TAG, "Opus 编码器初始化成功: bitrate=${AudioConfig.OpusConfig.BITRATE}, " +
                    "frameDuration=${AudioConfig.OpusConfig.FRAME_DURATION_MS}ms")
            
        } catch (e: Exception) {
            throw UnsupportedOperationException("Opus 编码器初始化失败", e)
        }
    }

    private fun initOpusBridge() {
        opusBridge = OpusBridge.getInstance()

        opusBridge?.initEncoder(OpusEncoderParams.Builder()
            .sampleRate(config.sampleRate)
            .channels(config.channelCount)
            .bitrate(AudioConfig.OpusConfig.BITRATE)
            .build()
        )
    }

    /**
     * 录音循环
     */
    private fun recordLoop() {
        val pcmBuffer = ByteArray(frameBytes)
        
        Log.i(TAG, "录音线程开始")
        
        while (isRecording) {
            try {
                // 读取 PCM 数据
                val readBytes = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: break
                
                if (readBytes <= 0) {
                    Log.w(TAG, "读取音频数据失败: $readBytes")
                    continue
                }
                
                // 获取实际读取的数据
                val audioData = if (readBytes == pcmBuffer.size) {
                    pcmBuffer.copyOf()
                } else {
                    pcmBuffer.copyOf(readBytes)
                }
                
                // 处理音频数据
                processAudioData(audioData)
                
            } catch (e: Exception) {
                Log.e(TAG, "录音循环异常", e)
                break
            }
        }
        
        Log.i(TAG, "录音线程结束")
    }

    /**
     * 处理音频数据
     */
    private fun processAudioData(pcmData: ByteArray) {
        try {
            val outputData = when (config.formatType) {
                AudioFormatType.PCM -> {
                    // PCM 格式直接输出
                    pcmData
                }

                AudioFormatType.OPUS -> {
                    // Opus 格式需要编码
                    encodeToOpusByMediaCodec(pcmData) ?: encodeToOpusByOpusBridge(pcmData)
                }
            }
            
            if (outputData != null) {
                // 回调音频数据
                onAudioData(outputData)
                
                // 保存到文件（如果需要）
                fileWriter?.write(outputData)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理音频数据失败", e)
        }
    }

    /**
     * 使用 MediaCodec 将 PCM 编码为 Opus 格式
     */
    private fun encodeToOpusByMediaCodec(pcmData: ByteArray): ByteArray? {
        val encoder = opusEncoder ?: return null
        
        try {
            // 获取输入缓冲区
            val inputBufferIndex = encoder.dequeueInputBuffer(10000)

            if (inputBufferIndex >= 0) {
                val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(pcmData)
                
                encoder.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    pcmData.size,
                    System.nanoTime() / 1000,
                    0
                )
            }
            
            // 获取输出缓冲区
            val bufferInfo = MediaCodec.BufferInfo()
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            
            if (outputBufferIndex >= 0) {
                val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                val opusData = ByteArray(bufferInfo.size)
                outputBuffer?.get(opusData)
                
                encoder.releaseOutputBuffer(outputBufferIndex, false)
                
                return opusData
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Opus 编码失败", e)
        }
        
        return null
    }

    private fun encodeToOpusByOpusBridge(pcmData: ByteArray): ByteArray? {
        return opusBridge?.encode(pcmData)
    }

    /**
     * 内部释放资源
     */
    private fun releaseInternal() {
        // 释放音频效果处理器
        try {
            aec?.release()
            aec = null
        } catch (e: Exception) {
            Log.e(TAG, "释放 AEC 失败", e)
        }
        
        try {
            agc?.release()
            agc = null
        } catch (e: Exception) {
            Log.e(TAG, "释放 AGC 失败", e)
        }
        
        try {
            ns?.release()
            ns = null
        } catch (e: Exception) {
            Log.e(TAG, "释放 NS 失败", e)
        }
        
        // 释放 Opus 编码器
        try {
            opusEncoder?.stop()
            opusEncoder?.release()
            opusEncoder = null
        } catch (e: Exception) {
            Log.e(TAG, "释放 Opus 编码器失败", e)
        }

        opusBridge?.releaseEncoder()
        opusBridge = null
        
        // 释放 AudioRecord
        try {
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "释放 AudioRecord 失败", e)
        }
        
        // 关闭文件写入器
        try {
            fileWriter?.close()
            val bytesWritten = fileWriter?.getBytesWritten() ?: 0
            if (bytesWritten > 0) {
                Log.i(TAG, "音频文件已保存: ${fileWriter?.getFilePath()}, 大小: $bytesWritten 字节")
            }
            fileWriter = null
        } catch (e: Exception) {
            Log.e(TAG, "关闭文件写入器失败", e)
        }
    }

    /**
     * 获取当前状态
     */
    fun isInitialized(): Boolean = isInitialized
    
    fun isRecording(): Boolean = isRecording
    
    /**
     * 获取音频配置
     */
    fun getConfig(): AudioConfig = config
}
