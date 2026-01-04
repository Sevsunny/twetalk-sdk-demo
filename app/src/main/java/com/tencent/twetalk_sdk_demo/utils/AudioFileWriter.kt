package com.tencent.twetalk_sdk_demo.utils

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 音频文件写入器
 */
class AudioFileWriter(private val filePath: String) {

    private var fileOutputStream: FileOutputStream? = null
    private var totalBytesWritten: Long = 0

    /**
     * 打开文件准备写入
     */
    @Throws(IOException::class)
    fun open() {
        val file = File(filePath)
        file.parentFile?.mkdirs()
        fileOutputStream = FileOutputStream(file, false)
        totalBytesWritten = 0
    }

    /**
     * 写入音频数据
     */
    @Throws(IOException::class)
    fun write(data: ByteArray) {
        fileOutputStream?.write(data)
        totalBytesWritten += data.size
    }

    /**
     * 关闭文件
     */
    fun close() {
        try {
            fileOutputStream?.flush()
            fileOutputStream?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            fileOutputStream = null
        }
    }

    /**
     * 获取已写入的字节数
     */
    fun getBytesWritten(): Long = totalBytesWritten

    /**
     * 获取文件路径
     */
    fun getFilePath(): String = filePath
}