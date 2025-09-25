# TWeTalk Android SDK

## SDK 接入

### WebSocket

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
    implementation 'com.tencent.twetalk:twetalk-android:1.0.0-SNAPSHOT'
    ```

    Kotlin DSL:

    ``` kotlin
    implementation("com.tencent.twetalk:twetalk-android:1.0.0-SNAPSHOT")
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

            override fun onRecvStream(buffer: ByteArray?) {
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

    1. 封装传输消息的帧格式

        ``` Kotlin
        // 音频消息，默认采样率为 16000，单声道
        // audioData: 音频数据  startTime: 开始录制音频的时间
        val audioFrame = FrameProcessor.buildAudioRawTime(audioData, startTime)

        // 自定义音频帧
        val audioRawFrame = FramesProtos.AudioRawFrame
                            .newBuilder()
                            .setId(id)
                            .setName(name)
                            .setAudio(ByteString.copyFrom(audioData))
                            .setSampleRate(sampleRate)
                            .setNumChannels(channel)
                            .setPts(pts)
                            .build()

        val audioFrame = FramesProtos.Frame.newBuilder()
                            .setAudio(audioRawFrame)
                            .build()

        // 其它消息格式
        // 文本消息
        val textFrame = FrameProcessor.buildTextFrame(text)
        
        或

        val textRawFrame = FramesProtos.TextFrame.newBuilder()
                            .setId(id)
                            .setName(name)
                            .setText(text)
                            .build()
        
        val textFrame = FramesProtos.Frame.newBuilder()
                            .setText(textFrame)
                            .build()

        // 自定义消息，一般发送 json 格式 string 消息
        val customMsgFrame = FrameProcessor.buildCustomMsgFrame(message)

        或

        val customMsgRawFrame = FramesProtos.MessageFrame.newBuilder()
                                    .setData(message)
                                    .build()

        val customMsgFrame = FramesProtos.Frame.newBuilder()
                                .setMessage(customMsgRawFrame)
                                .build()

        ```

    2. 调用 sendDirectly 发送消息

       ``` Kotlin
        client?.sendDirectly(Frame)
       ```

4. 在 onRecvStream 回调中接收服务端传过来的数据并处理

   ``` Kotlin
    override fun onRecvStream(buffer: ByteArray?) {
        ......
    }

   ```

### TRTC

待实现
