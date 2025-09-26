# TWeTalk Android SDK

## SDK 接入

### WebSocket

#### 接入指引

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
    implementation 'com.tencent.twetalk:twetalk-android:1.0.4-SNAPSHOT'
    ```

    Kotlin DSL:

    ``` kotlin
    implementation("com.tencent.twetalk:twetalk-android:1.0.4-SNAPSHOT")
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

待实现

## Demo 使用指引

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
