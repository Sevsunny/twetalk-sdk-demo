package com.tencent.twetalk_audio.sink

import android.media.MediaCodecList
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build

/**
 * 音频设备能力检测器
 */
object AudioCapabilityDetector {
    
    /**
     * 检测设备是否支持 Opus 硬件编码
     */
    fun isOpusSupported(): Boolean {
        // Android 5.0 (API 21) 开始支持 Opus
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }
        
        // 检查 MediaCodec 是否支持 Opus
        return try {
            val codecList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                MediaCodecList(MediaCodecList.REGULAR_CODECS)
            } else {
                return false
            }
            
            val codecInfos = codecList.codecInfos

            codecInfos.any { codecInfo ->
                codecInfo.isEncoder && codecInfo.supportedTypes.any { 
                    it.equals("audio/opus", ignoreCase = true) 
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 检测设备是否支持 AEC（回声消除）
     */
    fun isAECSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            AcousticEchoCanceler.isAvailable()
        } else {
            false
        }
    }
    
    /**
     * 检测设备是否支持 AGC（自动增益控制）
     */
    fun isAGCSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            AutomaticGainControl.isAvailable()
        } else {
            false
        }
    }
    
    /**
     * 检测设备是否支持 NS（噪声抑制）
     */
    fun isNSSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            NoiseSuppressor.isAvailable()
        } else {
            false
        }
    }
    
    /**
     * 获取设备音频能力报告
     */
    fun getCapabilityReport(): AudioCapabilityReport {
        return AudioCapabilityReport(
            opusSupported = isOpusSupported(),
            aecSupported = isAECSupported(),
            agcSupported = isAGCSupported(),
            nsSupported = isNSSupported()
        )
    }
}

/**
 * 音频能力报告
 */
data class AudioCapabilityReport(
    val opusSupported: Boolean,
    val aecSupported: Boolean,
    val agcSupported: Boolean,
    val nsSupported: Boolean
) {
    override fun toString(): String {
        return """
            音频设备能力报告:
            - Opus 硬件编码: ${if (opusSupported) "支持" else "不支持"}
            - AEC 回声消除: ${if (aecSupported) "支持" else "不支持"}
            - AGC 自动增益: ${if (agcSupported) "支持" else "不支持"}
            - NS 噪声抑制: ${if (nsSupported) "支持" else "不支持"}
        """.trimIndent()
    }
}
