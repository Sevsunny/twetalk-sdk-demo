package com.tencent.twetalk_audio.listener;

/**
 * 音频播放状态监听器
 */
public interface OnPlayStateListener {
    /**
     * 播放错误回调
     * @param errorCode 错误码
     * @param message 错误信息
     */
    void onPlayError(int errorCode, String message);
}
