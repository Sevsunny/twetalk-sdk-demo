# TWeTalk Android SDK

## SDK 接入

### WebSocket

#### WebSocket SDK 接入指引

1. 引入依赖

    SnapShot 版本：

    在 settings.gradle 下添加 maven snapshot 仓库：

    Groovy:

    ``` groovy
        maven {
            url "https://oss.sonatype.org/content/repositories/snapshots"
        }
        maven {
            name = 'Central Portal Snapshots'
            url = 'https://central.sonatype.com/repository/maven-snapshots/'
        }
    ```

    Kotlin DSL:

    ``` kotlin
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots")
        }

        maven {
            name = "Central Portal Snapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    ```

    项目中引用：

    Groovy:

    ``` groovy
    implementation 'com.tencent.twetalk:twetalk-android:1.1.1-SNAPSHOT'
    ```

    Kotlin DSL:

    ``` kotlin
    implementation("com.tencent.twetalk:twetalk-android:1.1.1-SNAPSHOT")
    ```

2. 创建 TWeTalkClient

    1. 创建配置

        ``` Kotlin
        // 创建认证类型配置
        // secretId: 控制台上获取的密钥 ID
        // secretKey: 控制台上获取的密钥 key
        // productId: 控制台上获取的产品 ID
        // deviceName: 控制台上获取的设备名称
        // audioType: 支持传输 pcm 和 opus 两种音频格式，取值 "pcm" 或 "opus"
        val authConfig = TWeTalkConfig.AuthConfig(secretId, secretKey, productId, deviceName, audioType)

        // SDK 配置
        val config = TWeTalkConfig.builder()
                        .authConfig(authConfig)
                        .build()

        ```

    2. 创建 TWeTalkClient

        ``` Kotlin
        client = DefaultTWeTalkClient(config)
        ```

    3. 添加监听器

        ``` Kotlin
        client?.addListener(object : TWeTalkClientListener {
            override fun onStateChanged(state: ConnectionState?) {
                // WebSocket 连接状态变化监听
                ......
            }

            override fun onRecvMessage(message: TWeTalkMessage) {
                // 接收到服务端传过来的数据
                ......
            }

            override fun onMetrics(metrics: MetricEvent?) {
                // 监听 SDK 各项指标，默认不监听，如有需要可在创建 config 时打开
            }

            override fun onError(error: Throwable?) {
                // 监听错误
            }
        })
        ```

3. 调用接口传输数据

    1. 发送音频数据

       ``` Kotlin
        // 默认发送
        client?.sendAudioData(audioData)

        // 指定采样率和通道数
        client?.sendCustomAudioData(audioData, sampleRate, channels)
       ```

    2. 发送自定义消息数据

       ``` Kotlin
       // msg 为 json 格式的字符串
        client?.sendCustomMsg(msg)
       ```

4. 在 onRecvMessage 回调中接收服务端传过来的数据并处理

   ``` Kotlin
    override fun onRecvMessage(message: TWeTalkMessage) {
        ......
    }
   ```

#### TWeTalkMessage 格式

消息格式：

``` Java
public class TWeTalkMessage {
    private final TWeTalkMessageType type;    // 消息类型
    private Object data;    // 消息携带的数据
}
```

类型：

``` Java
public enum TWeTalkMessageType {
    /** 音频消息 **/
    AUDIO_DATA("audio-data"),

    /** 转录消息 */
    USER_TRANSCRIPTION("user-transcription"),             // 本地用户语音转文本
    BOT_TRANSCRIPTION("bot-transcription"),               // 机器人完整文本转录
    USER_STARTED_SPEAKING("user-started-speaking"),       // 用户开始说话
    USER_STOPPED_SPEAKING("user-stopped-speaking"),       // 用户停止说话
    BOT_STARTED_SPEAKING("bot-started-speaking"),         // 机器人开始说话
    BOT_STOPPED_SPEAKING("bot-stopped-speaking"),         // 机器人停止说话

    /** LLM 消息 */
    USER_LLM_TEXT("user-llm-text"),               // 聚合后的用户输入文本
    BOT_LLM_TEXT("bot-llm-text"),                 // LLM 返回的流式 token
    BOT_LLM_STARTED("bot-llm-started"),           // 机器人 LLM 推理开始
    BOT_LLM_STOPPED("bot-llm-stopped"),           // 机器人 LLM 推理结束

    /** TTS 消息 */
    BOT_TTS_TEXT("bot-tts-text"),                 // 机器人 TTS 文本输出
    BOT_TTS_STARTED("bot-tts-started"),           // 机器人 TTS 响应开始
    BOT_TTS_STOPPED("bot-tts-stopped")           // 机器人 TTS 响应结束
    ;
}
```

音频消息：

``` Java
public class AudioMessage {
    private final byte[] audio;  // 音频数据
    private final int sampleRate;  // 采样率
    private final int numChannels;  // 通道数
}
```

### TRTC

#### TRTC SDK 接入指引

1. 引入依赖

    SnapShot 版本：

    在 settings.gradle 下添加 maven snapshot 仓库：

    Groovy:

    ``` groovy
        maven {
            url "https://oss.sonatype.org/content/repositories/snapshots"
        }
        maven {
            name = 'Central Portal Snapshots'
            url = 'https://central.sonatype.com/repository/maven-snapshots/'
        }
    ```

    Kotlin DSL:

    ``` kotlin
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots")
        }

        maven {
            name = "Central Portal Snapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    ```

    项目中引用：

    Groovy:

    ``` groovy
    implementation 'com.tencent.twetalk:twetalk-android-trtc:1.0.6-SNAPSHOT'
    ```

    Kotlin DSL:

    ``` kotlin
    implementation("com.tencent.twetalk:twetalk-android-trtc:1.0.6-SNAPSHOT")
    ```

2. 创建 TWeTalkTRTCClient
   1. 创建配置

      ``` Kotlin
        private val config = TRTCConfig.Builder()
            .sdkAppId(sdkAppId)  // TRTC 控制台上获取的 sdkAppId
            .sdkSecretKey(sdkSecretKey)  // TRTC 控制台上获取的 sdkSecretKey
            .userId(userId)  // userId，如果初始不配置默认使用 productId_deviceName 的组合
            .productId(productId) // Iot 控制台上获取的 productId
            .deviceName(deviceName)  // Iot 控制台上获取的 deviceName
            .context(context)  // Android Context
            .build()

      ```

   2. 创建 TWeTalkTRTCClient

       ``` Kotlin
        client = DefaultTRTCClient(config)
       ```

   3. 添加监听器 TRTCClientListener

      ``` Kotlin
        client?.addListener(object : TRTCClientListener {
            override fun onStateChanged(state: TRTCClientState?) {
                // TRTC 状态变化监听
                ......
            }

            override fun onRecvMessage(message: TWeTalkMessage) {
                // 接收到服务端传过来的数据
                ......
            }

            override fun onMetrics(metrics: MetricEvent?) {
                // 监听 SDK 各项指标，默认不监听，如有需要可在创建 config 时打开
            }

            override fun onError(errCode: Int, errMsg: String?) {
                // 监听错误
                // errCode 参考：https://cloud.tencent.com/document/product/647/38308#e9c6eb6577e24853dd9716de29044384
            }
        })
      ```

3. 调用接口传输数据
   1. 发送音频数据

        ``` Kotlin
        // 自采集默认发送，一般默认使用 TRTC 采集音频，如需自采集请在 config 关闭 TRTC 采集音频 useTRTCRecord 参数
        client?.sendAudioData(audioData)

        // 指定采样率和通道数
        client?.sendCustomAudioData(audioData, sampleRate, channels)
        ```

   2. 发送自定义消息数据

        ``` Kotlin
        // msg 为 json 格式的字符串
        client?.sendCustomMsg(msg)
        ```

4. 在 onRecvMessage 回调中接收服务端传过来的数据并处理

   ``` Kotlin
    override fun onRecvMessage(message: TWeTalkMessage) {
        ......
    }
   ```

#### 消息格式

TWeTalkMessage 同上述 WebSocket 的一致。

音频消息格式略有区别：

``` Java
public class AudioFrame {
    public String userId;  // TRTC Room 中音频帧来源的 user
    public byte[] data;  // 音频数据
    public int channels;  // 通道数
    public int sampleRate;  // 采样率
    public long timestamp;  // 时间戳
}
```

## Demo 使用指引

注意，SecretKey 最好保存在自建服务中以免泄露，此处仅为演示。

### TWeTalkDemo

1. 用户编译前可选择根据实际需要情况调整 config.json 中的内容，config.json 位于 app 模块的根目录，config.json 内容如下：

    ``` json
    {
      "productId": "",
      "deviceName": "",

      "websocket": {
          "secretId": "请输入从物联网开发平台申请的 App key",
          "secretKey": "请输入从物联网开发平台申请的 App Secret，App Secret 请保存在服务端，此处仅为演示，如有泄露概不负责"
      },

      "trtc": {
          "sdkAppId": "",
          "sdkSecretKey": "",
          "userId": ""
      }
    }
    ```

   websocket 和 trtc 字段只填需要使用的一种即可。

2. 用户也可直接打开 Demo 根据输入框提示需要的信息填写连接

### websocket-demo

将 MainActivity 下的 config 参数替换为对应参数：

``` Kotlin
private val config: TWeTalkConfig by lazy {
    val authConfig = TWeTalkConfig.AuthConfig(
        "<Your SecretId>",
        "<Your SecretKey>",
        "<Your ProductId>", "<Your DeviceName>", "pcm")

    TWeTalkConfig.builder()
        .authConfig(authConfig)
        .build()
}
```

然后编译后安装运行即可。

### trtc-demo

将 MainActivity 下的 config 参数替换为对应参数：

``` Kotlin
private val config = TRTCConfig.Builder()
    .sdkAppId(0)
    .sdkSecretKey("<Your SDKSecretKey>")
    .userId("<Your userId>")
    .productId("<Your ProductId>")
    .deviceName("<Your DeviceName>")
    .context(this)
    .build()
```

然后编译后安装运行即可。
