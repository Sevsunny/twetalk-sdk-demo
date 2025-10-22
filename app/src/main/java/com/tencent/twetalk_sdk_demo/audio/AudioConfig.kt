package com.tencent.twetalk_sdk_demo.audio

/**
 * 音频格式枚举
 */
enum class AudioFormatType {
    PCM,    // 原始 PCM 格式
    OPUS    // Opus 编码格式
}

/**
 * 音频配置
 */
data class AudioConfig(
    val sampleRate: Int = 16000,           // 采样率
    val channelCount: Int = 1,              // 声道数（1=单声道，2=立体声）
    val bitDepth: Int = 16,                 // 位深度
    val chunkMs: Int = 20,                  // 每帧时长（毫秒）
    val formatType: AudioFormatType = AudioFormatType.PCM,  // 音频格式
    val enableAEC: Boolean = true,         // 回声消除
    val enableAGC: Boolean = true,         // 自动增益控制
    val enableNS: Boolean = true,          // 噪声抑制
    val saveToFile: Boolean = false,        // 是否保存到文件
    val filePath: String? = null            // 文件保存路径
) {
    /**
     * Opus 特定配置
     */
    object OpusConfig {
        const val FRAME_DURATION_MS = 60    // 60ms 一帧
        const val FRAME_SIZE_BYTES = 180    // 180 字节一帧
        const val BITRATE = 24000           // 比特率 24kbps
    }
    
    /**
     * 验证配置有效性
     */
    fun validate(): Result<Unit> {
        if (sampleRate <= 0) {
            return Result.failure(IllegalArgumentException("采样率必须大于0"))
        }
        if (channelCount !in 1..2) {
            return Result.failure(IllegalArgumentException("声道数必须为1或2"))
        }
        if (bitDepth !in listOf(8, 16)) {
            return Result.failure(IllegalArgumentException("位深度必须为8或16"))
        }
        if (chunkMs <= 0) {
            return Result.failure(IllegalArgumentException("帧时长必须大于0"))
        }
        if (saveToFile && filePath.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("保存文件时必须指定文件路径"))
        }
        return Result.success(Unit)
    }
}

//data class PlayConfig(
//    val sampleRate: Int = 16000,           // 采样率
//    val channelCount: Int = 1,              // 声道数（1=单声道，2=立体声）
//    val formatType: AudioFormatType = AudioFormatType.PCM,  // 音频格式
//    val saveToFile: Boolean = false,        // 是否保存到文件
//    val filePath: String? = null            // 文件保存路径
//) {
//
//    /**
//     * 验证配置有效性
//     */
//    fun validate(): Result<Unit> {
//        if (sampleRate <= 0) {
//            return Result.failure(IllegalArgumentException("采样率必须大于0"))
//        }
//
//        if (channelCount !in 1..2) {
//            return Result.failure(IllegalArgumentException("声道数必须为1或2"))
//        }
//
//        if (saveToFile && filePath.isNullOrBlank()) {
//            return Result.failure(IllegalArgumentException("保存文件时必须指定文件路径"))
//        }
//
//        return Result.success(Unit)
//    }
//}
