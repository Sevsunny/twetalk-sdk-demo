package com.tencent.twetalk_sdk_demo.call

import android.content.Context
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.TypeReference
import com.tencent.twetalk_sdk_demo.data.Constants
import com.tencent.twetalk_sdk_demo.data.Contact
import org.json.JSONArray

/**
 * 通话配置管理器
 * 负责管理小程序信息和通讯录的持久化存储
 */
object CallConfigManager {

    /**
     * 获取小程序 App ID
     */
    fun getWxaAppId(context: Context): String {
        val prefs = context.getSharedPreferences(Constants.KEY_CALL_CONFIG_PREF, Context.MODE_PRIVATE)
        return prefs.getString(Constants.KEY_WXA_APP_ID, Constants.DEFAULT_WXA_APP_ID) 
            ?: Constants.DEFAULT_WXA_APP_ID
    }

    /**
     * 保存小程序 App ID
     */
    fun setWxaAppId(context: Context, appId: String) {
        val prefs = context.getSharedPreferences(Constants.KEY_CALL_CONFIG_PREF, Context.MODE_PRIVATE)
        prefs.edit().putString(Constants.KEY_WXA_APP_ID, appId).apply()
    }

    /**
     * 获取小程序 Model ID
     */
    fun getWxaModelId(context: Context): String {
        val prefs = context.getSharedPreferences(Constants.KEY_CALL_CONFIG_PREF, Context.MODE_PRIVATE)
        return prefs.getString(Constants.KEY_WXA_MODEL_ID, Constants.DEFAULT_WXA_MODEL_ID) 
            ?: Constants.DEFAULT_WXA_MODEL_ID
    }

    /**
     * 保存小程序 Model ID
     */
    fun setWxaModelId(context: Context, modelId: String) {
        val prefs = context.getSharedPreferences(Constants.KEY_CALL_CONFIG_PREF, Context.MODE_PRIVATE)
        prefs.edit().putString(Constants.KEY_WXA_MODEL_ID, modelId).apply()
    }

    /**
     * 获取通讯录
     */
    fun getContacts(context: Context): MutableList<Contact> {
        val prefs = context.getSharedPreferences(Constants.KEY_CALL_CONFIG_PREF, Context.MODE_PRIVATE)
        val json = prefs.getString(Constants.KEY_CONTACTS, null) ?: return mutableListOf()
        
        return try {
            // 先尝试使用 fastjson2 反序列化（兼容旧数据）
            JSON.parseObject(json, object : TypeReference<MutableList<Contact>>() {})
        } catch (e: Exception) {
            try {
                // 如果失败，尝试手动解析（新格式）
                val contactsList = mutableListOf<Contact>()
                val jsonArray = JSONArray(json)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val nickname = jsonObject.getString("nickname")
                    val openId = jsonObject.getString("openId")
                    contactsList.add(Contact(nickname, openId))
                }
                contactsList
            } catch (e2: Exception) {
                // 如果还是失败，返回空列表
                mutableListOf()
            }
        }
    }

    /**
     * 保存通讯录
     */
    fun saveContacts(context: Context, contacts: List<Contact>) {
        val prefs = context.getSharedPreferences(Constants.KEY_CALL_CONFIG_PREF, Context.MODE_PRIVATE)
        // 手动序列化为 JSON 数组，避免 fastjson2 对 Kotlin data class 的序列化问题
        val json = buildString {
            append("[")
            contacts.forEachIndexed { index, contact ->
                if (index > 0) append(",")
                append("{\"nickname\":\"${contact.nickname}\",\"openId\":\"${contact.openId}\"}")
            }
            append("]")
        }
        prefs.edit().putString(Constants.KEY_CONTACTS, json).apply()
    }

    /**
     * 添加联系人
     */
    fun addContact(context: Context, contact: Contact) {
        val contacts = getContacts(context)
        contacts.add(contact)
        saveContacts(context, contacts)
    }

    /**
     * 更新联系人
     */
    fun updateContact(context: Context, index: Int, contact: Contact) {
        val contacts = getContacts(context)
        if (index in contacts.indices) {
            contacts[index] = contact
            saveContacts(context, contacts)
        }
    }

    /**
     * 删除联系人
     */
    fun removeContact(context: Context, index: Int) {
        val contacts = getContacts(context)
        if (index in contacts.indices) {
            contacts.removeAt(index)
            saveContacts(context, contacts)
        }
    }

    /**
     * 根据 openId 查找联系人昵称
     */
    fun findNicknameByOpenId(context: Context, openId: String): String? {
        val contacts = getContacts(context)
        return contacts.find { it.openId == openId }?.nickname
    }

    /**
     * 获取设备 ID (productId_deviceName)
     */
    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(Constants.KEY_DEVICE_INFO_PREF, Context.MODE_PRIVATE)
        val productId = prefs.getString(Constants.KEY_PRODUCT_ID, "") ?: ""
        val deviceName = prefs.getString(Constants.KEY_DEVICE_NAME, "") ?: ""
        return "${productId}_${deviceName}"
    }

    /**
     * 获取 productId
     */
    fun getProductId(context: Context): String {
        val prefs = context.getSharedPreferences(Constants.KEY_DEVICE_INFO_PREF, Context.MODE_PRIVATE)
        return prefs.getString(Constants.KEY_PRODUCT_ID, "") ?: ""
    }

    /**
     * 获取 deviceName
     */
    fun getDeviceName(context: Context): String {
        val prefs = context.getSharedPreferences(Constants.KEY_DEVICE_INFO_PREF, Context.MODE_PRIVATE)
        return prefs.getString(Constants.KEY_DEVICE_NAME, "") ?: ""
    }

    /**
     * 构建通讯录消息的 openids 列表
     */
    fun buildOpenIdsList(context: Context): List<Map<String, String>> {
        return getContacts(context).map { it.toOpenIdMap() }
    }
}
