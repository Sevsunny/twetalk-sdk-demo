package com.tencent.twetalk_sdk_demo.call

/**
 * 通话状态
 */
enum class CallState {
    /** 空闲 */
    IDLE,
    /** 呼叫中 (设备主叫) */
    CALLING,
    /** 来电中 (设备被叫) */
    INCOMING,
    /** 通话中 */
    IN_PROGRESS,
    /** 对方拒接 */
    REJECTED,
    /** 呼叫超时 */
    TIMEOUT,
    /** 对方占线 */
    BUSY,
    /** 呼叫出错 */
    ERROR,
    /** 通话结束 */
    ENDED
}

/**
 * 通话类型
 */
enum class CallType {
    /** 设备呼叫小程序 */
    OUTGOING,
    /** 小程序呼叫设备 */
    INCOMING
}
