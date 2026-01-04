package com.tencent.twetalk_audio.listener;

/**
 * 简化的音频采集数据监听器适配器
 * 继承此类可只实现需要的方法
 */
public abstract class SimpleRecordDataListener implements OnRecordDataListener {

    @Override
    public void onPcmData(byte[] data, int size) {
        // 默认空实现
    }

    @Override
    public void onOpusData(byte[] data, int size) {
        // 默认空实现
    }

    @Override
    public void onRecordError(int errorCode, String message) {
        // 默认空实现
    }
}
