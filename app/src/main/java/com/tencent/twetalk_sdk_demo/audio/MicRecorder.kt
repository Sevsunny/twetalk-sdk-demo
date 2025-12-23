package com.tencent.twetalk_sdk_demo.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.tencent.twetalk.audio.OpusBridge
import com.tencent.twetalk.audio.model.OpusEncoderParams
import com.tencent.twetalk.util.PcmUtil

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
    private var opusEncoderHandle: Long = 0
    private val opusBridge = OpusBridge.getInstance()

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
                initOpusEncoder()
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
            MediaRecorder.AudioSource.MIC,
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
        try {
            val params = OpusEncoderParams.Builder()
                .sampleRate(config.sampleRate)
                .channels(config.channelCount)
                .targetBytes(AudioConfig.OpusConfig.TARGET_BYTES)
                .bitrate(AudioConfig.OpusConfig.BITRATE)
                .cbr(true)
                .dtx(false)
                .complexity(5)
                .signalVoice(true)
                .build()

            opusEncoderHandle = opusBridge.createEncoder(params)

            if (opusEncoderHandle == 0L) {
                throw UnsupportedOperationException("OpusEncoder 创建失败")
            }

            Log.i(TAG, "==== OpusEncoder Init Success ====")
            Log.i(TAG, "Handle: ${opusEncoderHandle}\n" +
                    "Sample Rate: ${config.sampleRate}\n" +
                    "Channels: ${config.channelCount}\n" +
                    "Bitrate: ${AudioConfig.OpusConfig.BITRATE}\n" +
                    "Target Bytes: ${AudioConfig.OpusConfig.TARGET_BYTES}\n" +
                    "Frame Ms: ${AudioConfig.OpusConfig.FRAME_DURATION_MS}\n")
        } catch (e: Exception) {
            throw UnsupportedOperationException("OpusEncoder 初始化失败", e)
        }
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
                    encodeToOpus(pcmData)
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
     * 编码 PCM 为 Opus
     */
    private fun encodeToOpus(pcmData: ByteArray): ByteArray? {
        if (opusEncoderHandle == 0L) {
            Log.e(TAG, "OpusEncoder isn't init!")
            return null
        }

        try {
            val shorts = PcmUtil.byteToShort(pcmData)
            return opusBridge.encode(opusEncoderHandle, shorts)
        } catch (e: Exception) {
            Log.e(TAG, "Opus 编码失败", e)
            return null
        }
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
        if (opusEncoderHandle != 0L) {
            opusBridge.releaseEncoder(opusEncoderHandle)
            opusEncoderHandle = 0
        }
        
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
