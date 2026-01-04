package com.tencent.twetalk_audio.config;

/**
 * 音频格式
 */
public enum AudioFormatType {
    PCM("pcm"),    // 原始 PCM 格式
    OPUS("opus");  // Opus 编码格式

    private final String value;

    AudioFormatType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
