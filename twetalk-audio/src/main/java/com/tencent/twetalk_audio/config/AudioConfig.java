package com.tencent.twetalk_audio.config;

/**
 * 音频配置
 */
public class AudioConfig {
    public int sampleRate = 16000;                    // 采样率
    public int channelCount = 1;                       // 声道数（1=单声道，2=立体声）
    public int bitDepth = 16;                          // 位深度
    public FrameDurationType frameDuration = FrameDurationType.MS_60;  // 帧时长
    public AudioFormatType formatType = AudioFormatType.PCM;         // 音频格式
    public boolean enableCustomAEC = false;                   // 回声消除
    public boolean enableCustomAGC = false;                   // 自动增益控制
    public boolean enableCustomNS = false;                    // 噪声抑制

    public AudioConfig() {
    }

    public AudioConfig(int sampleRate, int channelCount, int bitDepth, FrameDurationType frameDuration,
                       AudioFormatType formatType, boolean enableCustomAEC, boolean enableCustomAGC, boolean enableCustomNS) {
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        this.bitDepth = bitDepth;
        this.frameDuration = frameDuration != null ? frameDuration : FrameDurationType.MS_60;
        this.formatType = formatType != null ? formatType : AudioFormatType.PCM;
        this.enableCustomAEC = enableCustomAEC;
        this.enableCustomAGC = enableCustomAGC;
        this.enableCustomNS = enableCustomNS;
    }
}
