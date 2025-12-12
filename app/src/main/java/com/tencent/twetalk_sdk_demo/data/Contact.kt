package com.tencent.twetalk_sdk_demo.data

/**
 * 通讯录联系人
 */
data class Contact(
    val nickname: String,
    val openId: String
) {
    /**
     * 转换为通讯录消息格式的 Map
     */
    fun toOpenIdMap(): Map<String, String> = mapOf(nickname to openId)
}
