package com.tencent.twetalk_audio.config;

/**
 * 帧时长类型
 */
public enum FrameDurationType {
    MS_20(20),   // 20ms
    MS_40(40),   // 40ms
    MS_60(60);   // 60ms

    private final int duration;

    FrameDurationType(int duration) {
        this.duration = duration;
    }

    public int getDuration() {
        return duration;
    }
}
