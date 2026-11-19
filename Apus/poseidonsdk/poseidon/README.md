# Flynk Multiplatform

商业化广告SDK跨平台实现, 支持 Android / iOS, 采用 Kotlin Multiplatform 实现。

## Android端

### 前置要求

产品需要接入 **firebase-analytics** , **firebase-config** , **adjust-android** 库，当前SDK编译使用这些库的版本如下,
如有兼容问题，联系SDK提供方兼容:

```groovy
compileOnly "com.google.firebase:firebase-analytics:22.1.2"
compileOnly "com.google.firebase:firebase-config:22.0.1"
compileOnly 'com.adjust.sdk:adjust-android:5.3.0'
```

### 接入

如果你在**非**隔离环境使用这个SDK, 请添加如下仓库:

```groovy
repositories {
    google()
    mavenCentral()
    mavenLocal()
    maven { url 'https://maven.apuscn.com/nexus/content/repositories/android-xal-releases/' }
    maven { url 'https://maven.apuscn.com/nexus/content/repositories/android-xal-snapshots/' }
    maven { url "https://jfrog.anythinktech.com/artifactory/overseas_sdk" }
    maven { url "https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea" }
    maven { url "https://artifact.bytedance.com/repository/pangle" }
}
```

如果你在**隔离环境**环境下使用这个SDK, 请先将该SDK上传到内网服务期后在使用!, 注意每个产品用不同的SDK

Groovy DSL:

**Holy Bible Light**

```groovy
dependencies {
    def version = '1.0.8'
    debugImplementation "flynk.ads:flynk-multiplatform-android-holybiblelight-debug:$version"
    releaseImplementation "flynk.ads:flynk-multiplatform-android-holybiblelight:$version"
}
```

**Kjv Bible Library**

```groovy
dependencies {
    def version = '1.0.8'
    debugImplementation "flynk.ads:flynk-multiplatform-android-kjvbiblelibrary-debug:$version"
    releaseImplementation "flynk.ads:flynk-multiplatform-android-kjvbiblelibrary:$version"
}
```

### 初始化

在 `Application` 的 `onCreate` 中完成注册和初始化。注册只建立内存中的 SDK 实例；首次打开时间等磁盘状态会在初始化后由后台任务加载，不会阻塞主线程。

```kotlin
import flynk.multiplatform.Flynk

override fun onCreate() {
    super.onCreate()
    FirebaseApp.initializeApp(this)
    Flynk(
        application = this,
        mainActivityClass = TODO("主页Activity"),
    ).register().initialize()
}
```

注意⚠️：需要在 Firebase 初始化: `FirebaseApp.initializeApp(this)`
之后调用 `initialize()`；新用户状态与 Adjust Token 均由 SDK 自动处理，无需额外参数。

### TopOn流量分组配置

请注册 Adjust 的用于监听归因变化时的监听器: `setOnAttributionChangedListener`, 并在 `onAttributionChanged` 回调里调用
`TopOnTrafficGrouping.callOnAttributionChanged(attribution)`, 实例代码如下:

```kotlin
import flynk.multiplatform.TopOnTrafficGrouping
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAttribution
import com.adjust.sdk.AdjustConfig

val config = AdjustConfig(this, appToken, environment)
//...
config.setOnAttributionChangedListener { attribution: AdjustAttribution? ->
    TopOnTrafficGrouping.callOnAttributionChanged(attribution)
}
//...
Adjust.onCreate(config)
```

### CMP 支持

#### 产品端需要增加一个 **隐私选项入口入口点** 以便用户随时管理其隐私权选项，这个 **隐私选项入口入口点** 通常放置在设置页面(增加一个设置选项, 标题文案可以是：*Ad Privacy Options*)

是否需要展示这样一个 **隐私选项入口入口点** 可以通过如下API判断:

```kotlin
import flynk.multiplatform.FlynkMobileAds

FlynkMobileAds.doOnPrivacyOptionsIsRequired {
    // TODO 展示隐私选项入口入口点
}
```

#### 当用户点击 **隐私选项入口入口点** 后，将会弹窗一个一个 **隐私选项表单**, API如下

```kotlin
import flynk.multiplatform.FlynkMobileAds

// 展示隐私选项表单
FlynkMobileAds.showPrivacyOptionsForm(parameter = PlatformConsentFormShowParameter(activity = this))
```


### 服务端远程配置API支持

SDK 支持从服务端远程获取配置，服务端接口API需要产品段支持，当从服务端获取到远程配置后，可调用如下方法更新本地配置:

```kotlin
import flynk.multiplatform

val jsonFromBackend = ""
FlynkMobileAds.updateConfigurationFromBackend(json = jsonFromBackend)
```

注意⚠️：服务端接口应该在 SDK 初始化完毕后调用.

### 如何预加载共享广告位

我们需要在非新用户条件下，应用启动服务端远程配置接口更新配置结束之后调用如下方法执行共享广告位的预加载工作:

```kotlin
import flynk.multiplatform

FlynkMobileAds.tryPreloadSharedAdWhenAppStartup()
```

### 广告相关API

目前支持三种广告类型

- 插屏广告
- 原生广告
- 激励广告
- 开屏广告
- banner广告

除了banner广告，其他每种广告类型包含两种方法

- 预加载
- 展示, 当有广告展示机会的时候调用，对应一次 adpv

#### 横幅(banner)广告

⚠️：Banner目前的尺寸仅仅支持 **320x50**，不支持自定义

横幅(banner)广告目前只支持如下API, 表示加载并展示, 通常在 Activity 的 `onCreate` 或 Fragment 的 `onViewCreated` 里调用即可：

```kotlin
import flynk.multiplatform.FlynkMobileAds
import flynk.multiplatform.PlatformBannerAdLoadAndShowParameter
import flynk.multiplatform.PlatformConsentFormShowParameter
import flynk.multiplatform.loadAndShowBannerAdWithLifecycle

FlynkMobileAds.loadAndShowBannerAdWithLifecycle(
    lifecycle = activity.lifecycle, // Required
    loadAndShowParameter = PlatformBannerAdLoadAndShowParameter( // Required
        sceneName = "替换为具体的广告场景",
        context = this,
        adViewContainer = activity.findViewById<FrameLayout>(R.id.ad_view_container)
    ),
    onAdClosed = { // Optional
        // 广告被关闭
    }
)
```
`lifecycle` 为 `androidx.lifecycle.Lifecycle` 类型，`activity` 和 `fragment` 都拥有这个属性.

`adViewContainer` 为存放横幅(banner)广告的容器，类型必须是 `FrameLayout`, 宽度必须是 `match_parent`, 高度必须是 `wrap_content`, 代码如下:

```xml
<FrameLayout
    android:id="@+id/ad_view_container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

#### 开屏广告

广告预加载

```kotlin
import flynk.multiplatform.FlynkMobileAds
import flynk.multiplatform.PlatformOpenAdLoadParameter

FlynkMobileAds.preloadOpenAd(
    loadParameter = PlatformOpenAdLoadParameter(
        context = this, // 尽量传入Activity
        sceneName = "替换为具体的广告场景"
    )
)
```

广告展示

```kotlin
import flynk.multiplatform.FlynkMobileAds
import flynk.multiplatform.PlatformOpenAdShowParameter
import flynk.multiplatform.OpenShowListener
import flynk.multiplatform.Cancelable

val cancelable: Cancelable = FlynkMobileAds.showOpenAd(
    showParameter = PlatformOpenAdShowParameter(
        activity = this,
        sceneName = "替换为具体的广告场景",
        openShowListener = object : OpenShowListener {
            override fun onAdClicked() {
                // 当广告点击的时候回调
            }

            override fun onAdDismissedFullScreenContent() {
                // 当广告关闭的时候回调
            }

            override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
                // 当广告展示失败的时候回调
            }

            override fun onAdShowedFullScreenContent() {
                // 当广告展示的时候回调
            }
        }
    )
)
```

注意⚠️：`showOpenAd` 方法会返回一个 `Cancelable`, 当到达广告展示场景时，如何没有可用的缓存广告，此时会进行实时请求，
请求成功后，会展示请求到的广告。但是实时请求可能会消耗很长的时间，如果此时用户退出了当前页面，即使后面广告请求到了，也不应该
展示出来，此时我们需要调用 `Cancelable.cancel()`, 否则会导致内存泄漏，即使用一个已经销毁的Activity去展示广告.

#### 插屏广告

广告预加载

```kotlin
import flynk.multiplatform.FlynkMobileAds
import flynk.multiplatform.PlatformInterstitialAdLoadParameter

FlynkMobileAds.preloadInterstitialAd(
    loadParameter = PlatformInterstitialAdLoadParameter(
        context = this, // 尽量传入Activity
        sceneName = "替换为具体的广告场景"
    )
)
```

广告展示

```kotlin
import flynk.multiplatform.FlynkMobileAds
import flynk.multiplatform.PlatformInterstitialAdShowParameter
import flynk.multiplatform.InterstitialShowListener
import flynk.multiplatform.Cancelable

val cancelable: Cancelable = FlynkMobileAds.showInterstitialAd(
    showParameter = PlatformInterstitialAdShowParameter(
        activity = this,
        sceneName = "替换为具体的广告场景",
        interstitialShowListener = object : InterstitialShowListener {
            override fun onAdClicked() {
                // 当广告点击的时候回调
            }

            override fun onAdDismissedFullScreenContent() {
                // 当广告关闭的时候回调
            }

            override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
                // 当广告展示失败的时候回调
            }

            override fun onAdShowedFullScreenContent() {
                // 当广告展示的时候回调
            }
        }
    )
)
```

注意⚠️：`showInterstitialAd` 方法会返回一个 `Cancelable`, 当到达广告展示场景时，如何没有可用的缓存广告，此时会进行实时请求，
请求成功后，会展示请求到的广告。但是实时请求可能会消耗很长的时间，如果此时用户退出了当前页面，即使后面广告请求到了，也不应该
展示出来，此时我们需要调用 `Cancelable.cancel()`, 否则会导致内存泄漏，即使用一个已经销毁的Activity去展示广告.

#### 激励广告

广告预加载

```kotlin
import flynk.multiplatform.FlynkMobileAds
import flynk.multiplatform.PlatformRewardedAdLoadParameter

FlynkMobileAds.preloadRewardedAd(
    loadParameter = PlatformRewardedAdLoadParameter(
        context = this, // 尽量传入Activity
        sceneName = "替换为具体的广告场景"
    )
)
```

广告展示

```kotlin
import flynk.multiplatform.FlynkMobileAds
import flynk.multiplatform.PlatformRewardedAdShowParameter
import flynk.multiplatform.RewardedShowListener
import flynk.multiplatform.Cancelable
import flynk.multiplatform.OnUserEarnedRewardListener
import flynk.multiplatform.RewardItem

val cancelable: Cancelable = FlynkMobileAds.showRewardedAd(
    showParameter = PlatformRewardedAdShowParameter(
        activity = this,
        sceneName = "替换为具体的广告场景",
        onUserEarnedRewardListener = object : OnUserEarnedRewardListener {
            override fun onUserEarnedReward(reward: RewardItem) {
                // 当收到激励奖励的时候回调
            }
        },
        rewardedShowListener = object : RewardedShowListener {
            override fun onAdClicked() {
                // 当广告点击的时候回调
            }

            override fun onAdDismissedFullScreenContent() {
                // 当广告关闭的时候回调
            }

            override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
                // 当广告展示失败的时候回调
            }

            override fun onAdShowedFullScreenContent() {
                // 当广告展示的时候回调
            }
        }
    )
)
```

注意⚠️：`showRewardedAd` 方法会返回一个 `Cancelable`, 当到达广告展示场景时，如何没有可用的缓存广告，此时会进行实时请求，
请求成功后，会展示请求到的广告。但是实时请求可能会消耗很长的时间，如果此时用户退出了当前页面，即使后面广告请求到了，也不应该
展示出来，此时我们需要调用 `Cancelable.cancel()`, 否则会导致内存泄漏，即使用一个已经销毁的Activity去展示广告.

#### 原生广告

广告预加载

```kotlin
import flynk.multiplatform.FlynkMobileAds
import flynk.multiplatform.PlatformNativeAdLoadParameter

FlynkMobileAds.preloadNativeAd(
    loadParameter = PlatformNativeAdLoadParameter(
        context = this, // 尽量传入Activity
        sceneName = "替换为具体的广告场景"
    )
)
```

广告展示

1. 在布局文件中添加如下View-`flynk.multiplatform.NativeTemplateView`, 这个View将会用来展示原生广告:

```xml

<flynk.multiplatform.NativeTemplateView android:id="@+id/native_ad_view"
    android:layout_width="match_parent" android:layout_height="wrap_content" />
```

2. 展示原生广告，将这个View传递给 `PlatformNativeAdShowParameter` 的 `templateView` 参数:

```kotlin
import flynk.multiplatform.FlynkMobileAds
import flynk.multiplatform.PlatformNativeAdShowParameter
import flynk.multiplatform.Cancelable
import flynk.multiplatform.AdShowListener

val cancelable: Cancelable = FlynkMobileAds.showNativeAd(
    showParameter = PlatformNativeAdShowParameter(
        activity = this,
        sceneName = "替换为具体的广告场景",
        templateView = binding.nativeAdView,
        adShowListener = object : AdShowListener {
            override fun onAdClicked() {
                // 当广告点击的时候回调
            }

            override fun onAdClosed() {
                // 当广告关闭的时候回调
            }

            override fun onAdShowFailed(code: Int, message: String) {
                // 当广告展示失败的时候回调
            }

            override fun onAdShowSuccess() {
                // 当广告展示的时候回调
            }
        }
    )
)
```

注意⚠️：`showNativeAd` 方法会返回一个 `Cancelable`, 当到达广告展示场景时，如何没有可用的缓存广告，此时会进行实时请求，
请求成功后，会展示请求到的广告。但是实时请求可能会消耗很长的时间，如果此时用户退出了当前页面，即使后面广告请求到了，也不应该
展示出来，此时我们需要调用 `Cancelable.cancel()`, 否则会导致内存泄漏，即使用一个已经销毁的Activity去展示广告.

高级：自定义原生广告样式

TODO

### 如何在开发阶段使用测试广告?

[flynk_config.json](../doc/poseidon-config/template/flynk_config.json) 中包含测试的广告配置，可用于开发阶段，使用方法如下：

1. 将 [flynk_config.json](../doc/poseidon-config/template/flynk_config.json) 下载下来
2. 在 [flynk_config.json](../doc/poseidon-config/template/flynk_config.json) 的 `scenes` 块中添加产品的广告展示场景，并指定该场景所使用的
   广告策略(通过 `strategy_id` 指定), `strategies`
   块中已经定义了几种测试广告策略，可以使用他们测试广告:

| strategy_id | 描述         |
|-------------|------------|
| 40          | 测试共享插屏广告   |
| 50          | 测试共享激励视频广告 |
| 60          | 测试共享原生广告   |

3. 将修改后的 [flynk_config.json](../doc/poseidon-config/template/flynk_config.json) Push到 SDCard 根目录，打开外部存储权限

### 注意点

- 如果产品接入了资源混淆([AAB](https://github.com/bytedance/AabResGuard)
  或 [AndResGuard](https://github.com/shwenzhang/AndResGuard)), 需要将
  [whitelists](topon6.4.69-android-resguard-whitelists.txt) 里的内容添加到白名单配置里

## iOS端

TODO

## 如何加解密广告策略(flynk_config.json)

最终的正式包，需要对广告策略文件(flynk_config.json)的内容进行加密后，将生成的密文配置到Firebase云控后台或产品工程Firebase默认云控文件(kv结构, key为: **flynk_config**, value为: 密文)，然后SDK读取该配置.

可以通过[bin](src/rust/cryptor_cli/bin) 目录下的命令行工具进行对广告策略的加解密, 支持的平台如下:

| 二进制可执行文件                                                                      | 支持平台架构                    |
|-------------------------------------------------------------------------------|---------------------------|
| [cryptor_cli](src/rust/cryptor_cli/bin/x86_64-apple-darwin/cryptor_cli)       | x86_64-apple-darwin       |
| [cryptor_cli](src/rust/cryptor_cli/bin/x86_64-pc-windows-gnu/cryptor_cli)     | x86_64-pc-windows-gnu     |
| [cryptor_cli](src/rust/cryptor_cli/bin/x86_64-unknown-linux-gnu/cryptor_cli)  | x86_64-unknown-linux-gnu  |
| [cryptor_cli](src/rust/cryptor_cli/bin/x86_64-unknown-linux-musl/cryptor_cli) | x86_64-unknown-linux-musl |

![img](cryptor_cli_screenshot.png)
