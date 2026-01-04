package com.tencent.twetalk_audio.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PcmUtil {

    /**
     * short[]转byte[]
     * @param src
     * @return
     */
    public static byte[] shortToByte(short[] src) {
        if (src == null) {
            throw new IllegalArgumentException("short数组不能为空!");
        }
        if (src.length % 2 != 0) {
            throw new IllegalArgumentException("short数组的大小必须是偶数!");
        }
        byte[] bytes = new byte[src.length << 1];
        for (int i = 0; i < src.length; i++) {
            bytes[i * 2] = (byte) (src[i]);
            bytes[i * 2 + 1] = (byte) (src[i] >> 8);
        }
        return bytes;
    }

    /**
     * byte[]转short[]
     * @param src
     * @return
     */
    public static short[] byteToShort(byte[] src) {
        if (src == null) {
            throw new IllegalArgumentException("字节数组不能为空!");
        }
        if (src.length % 2 != 0) {
            throw new IllegalArgumentException("字节数组的大小必须是偶数!");
        }
        short[] shorts = new short[src.length >> 1];
        for (int i = 0, j = 0; i < shorts.length; i++, j += 2) {
            shorts[i] = (short) ((src[i * 2] & 0xFF) | (src[i * 2 + 1] << 8));
        }
        return shorts;
    }

    /**
     * short[] convert to byte[] by ByteBuffer
     * @param src
     * @param order
     * @return
     */
    public static byte[] shortToByteWithBuffer(short[] src, ByteOrder order) {
        if (src == null) throw new IllegalArgumentException("short数组不能为空!");
        byte[] out = new byte[src.length * 2];
        ByteBuffer.wrap(out).order(order).asShortBuffer().put(src);
        return out;
    }

    /**
     * byte[] convert to short[] by ByteBuffer
     * @param src
     * @param order
     * @return
     */
    public static short[] byteToShortWithBuffer(byte[] src, ByteOrder order) {
        if (src == null) throw new IllegalArgumentException("字节数组不能为空!");
        if ((src.length & 1) != 0) throw new IllegalArgumentException("字节数组的大小必须是偶数!");
        short[] out = new short[src.length / 2];
        ByteBuffer.wrap(src).order(order).asShortBuffer().get(out);
        return out;
    }
}

