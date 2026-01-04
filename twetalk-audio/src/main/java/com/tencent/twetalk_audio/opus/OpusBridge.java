package com.tencent.twetalk_audio.opus;

import android.util.Log;

/**
 * Opus 原生接口
 */
public final class OpusBridge {
    static {
        System.loadLibrary("opus_jni");
    }

    private static final String TAG = OpusBridge.class.getSimpleName();
    private static OpusBridge INSTANCE = null;

    public static synchronized OpusBridge getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new OpusBridge();
        }
        return INSTANCE;
    }

    private OpusBridge() {}

    // Native methods
    private native long nativeCreateEncoder(int sampleRate, int channels,
                                           int targetBytes, int bitrate,
                                           boolean cbr, boolean dtx,
                                           int complexity, boolean signalVoice);
    private native long nativeCreateDecoder(int sampleRate, int channels);
    private native byte[] nativeEncode(long handle, short[] pcmFrame);
    private native int nativeDecode(long handle, byte[] packet, short[] pcmOut, boolean fec);
    private native void nativeReleaseEncoder(long handle);
    private native void nativeReleaseDecoder(long handle);
    private native int nativeGetFrameSamples(long handle, boolean isEncoder);

    // Public API

    /**
     * 创建 Encoder
     * @param encoderParams 编码配置
     * @return encoder 句柄，失败返回 0
     */
    public long createEncoder(OpusEncoderParams encoderParams) {
        return nativeCreateEncoder(encoderParams.getSampleRate(), encoderParams.getChannels(),
                encoderParams.getTargetBytes(), encoderParams.getBitrate(), encoderParams.isCbr(),
                encoderParams.isDtx(), encoderParams.getComplexity(), encoderParams.isSignalVoice());
    }

    /**
     * 创建 Decoder
     * @param sampleRate 采样率
     * @param channels 通道数
     * @return decoder 句柄，失败返回 0
     */
    public long createDecoder(int sampleRate, int channels) {
        return nativeCreateDecoder(sampleRate, channels);
    }

    /**
     * 编码 PCM 数据
     * @param handle encoder 句柄
     * @param pcmFrame PCM 数据（short[]）
     * @return Opus 数据
     */
    public byte[] encode(long handle, short[] pcmFrame) {
        if (handle == 0 || pcmFrame == null || pcmFrame.length == 0) {
            Log.e(TAG, "encode: invalid handle or pcm data");
            return null;
        }
        return nativeEncode(handle, pcmFrame);
    }

    /**
     * 解码 Opus 数据
     * @param handle decoder 句柄
     * @param packet Opus 数据
     * @param pcmOut 解码后的 PCM 数据（short[]）
     * @param fec 是否启用 FEC
     * @return 解码后的样本数或错误码
     */
    public int decode(long handle, byte[] packet, short[] pcmOut, boolean fec) {
        if (handle == 0) {
            Log.e(TAG, "decode: invalid handle");
            return -1;
        }
        return nativeDecode(handle, packet, pcmOut, fec);
    }

    /**
     * 释放 Encoder 句柄
     * @param handle encoder 句柄
     */
    public void releaseEncoder(long handle) {
        if (handle != 0) {
            nativeReleaseEncoder(handle);
        }
    }

    /**
     * 释放 Decoder 句柄
     * @param handle decoder 句柄
     */
    public void releaseDecoder(long handle) {
        if (handle != 0) {
            nativeReleaseDecoder(handle);
        }
    }

    /**
     * 获取帧样本数
     * @param handle encoder 或 decoder 句柄
     * @param isEncoder true: encoder handle, false: decoder handle
     * @return 帧样本数
     */
    public int getFrameSamples(long handle, boolean isEncoder) {
        if (handle == 0) {
            return -1;
        }
        return nativeGetFrameSamples(handle, isEncoder);
    }

    // 辅助常量与方法
    public static final int FRAME_MS = 60;
    private static final int DEFAULT_SAMPLE_RATE = 48000;
    private static final int DEFAULT_CHANNELS = 1;

    public static int frameSamples(int sampleRate) {
        return sampleRate * FRAME_MS / 1000;
    }

    public static long ptsUsFromSamples(long samples, int sampleRate) {
        return samples * 1_000_000L / sampleRate;
    }
}
