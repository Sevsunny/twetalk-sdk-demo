package com.tencent.twetalk_audio.listener;

/**
 * 音频采集数据回调监听器
 */
public interface OnRecordDataListener {
    /**
     * PCM 原始数据回调
     * @param data PCM 音频数据
     * @param size 数据大小（字节）
     */
    void onPcmData(byte[] data, int size);

    /**
     * Opus 编码数据回调（仅当配置为 Opus 格式时触发）
     * @param data Opus 编码后的音频数据
     * @param size 数据大小（字节）
     */
    void onOpusData(byte[] data, int size);

    /**
     * 录音错误回调
     * @param errorCode 错误码
     * @param message 错误信息
     */
    void onRecordError(int errorCode, String message);
}
