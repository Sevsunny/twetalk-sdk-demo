package com.tencent.twetalk_sdk_demo

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import com.tencent.twetalk.mqtt.MqttManager
import kotlinx.coroutines.launch

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {
    protected val binding by lazy { getViewBinding() }

    // 全局 mqtt
    protected val mqttManager: MqttManager?
        get() = TalkApplication.getInstance().mqttManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        initView()
    }

    protected abstract fun getViewBinding(): VB
    protected abstract fun initView()

    protected fun showToast(msg: String?) {
        lifecycleScope.launch {
            Toast.makeText(this@BaseActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }
}