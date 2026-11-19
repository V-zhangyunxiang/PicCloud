# PoseidonAPI 对接指南

`com.poseidon.ad.api.PoseidonAPI` 是 SDK 向宿主暴露的统一门面类，所有初始化、加载与展示广告的动作都通过该类提供的静态方法完成。本文根据最新源码（`poseidon/src/androidMain/kotlin/com/poseidon/ad/api/PoseidonAPI.kt`）逐项说明各 API 的功能与参数。

## 初始化流程

| 方法 | 说明 |
| --- | --- |
| `register(poseidonInitConfig: PoseidonSetupProcessor)` | 在主线程注册宿主实现的 `PoseidonSetupProcessor`，在 `Application#onCreate` 中调用一次。该调用只注册内存状态，不读取磁盘；返回值即注册后的处理器实例，可继续链式初始化。 |
| `initialize()` | 完成 `register` 后调用，触发底层 SDK 初始化流程。需要运行在主线程。 |

## GDPR / 隐私入口

| 方法 | 说明 |
| --- | --- |
| `doOnPrivacyOptionsIsRequired(action: () -> Unit)` | 注册当 SDK 需要展示隐私设置入口时的回调。 |
| `showPrivacyOptionsForm(parameter: PlatformConsentFormShowParameter)` | 展示 GDPR/隐私设置界面。调用方需构造 `PlatformConsentFormShowParameter`，包含 Activity、回调等上下文信息。 |

## 缓存与加载

| 方法 | 说明 |
| --- | --- |
| `hasCacheAd(parameter: PlatformHasCacheParameter): Boolean` | 查询指定广告位是否已有可用缓存。返回 `true` 表示存在。 |
| `loadInterstitialAd(loadParameter: PlatformInterstitialAdLoadParameter)` | 请求加载插屏广告。 |
| `loadOpenAd(loadParameter: PlatformOpenAdLoadParameter)` | 请求加载开屏广告。 |
| `loadRewardedAd(loadParameter: PlatformRewardedAdLoadParameter)` | 请求加载激励视频广告。 |
| `loadNativeAd(loadParameter: PlatformNativeAdLoadParameter)` | 请求加载原生广告。 |

> 所有 `Platform*AdLoadParameter` 都会在内部持有 `Context`、`sceneName` 以及可选的 `AdLoadListener`，用于接收加载结果。

## 展示广告

| 方法 | 说明 |
| --- | --- |
| `showOpenAd(showParameter: PlatformOpenAdShowParameter): Cancelable` | 展示开屏广告。`showParameter.openShowListener` 可选；未设置则仅触发 SDK 内部回调 & 统计。 |
| `showInterstitialAd(showParameter: PlatformInterstitialAdShowParameter): Cancelable` | 展示插屏广告。`showParameter.interstitialShowListener` 可选。 |
| `showRewardedAd(showParameter: PlatformRewardedAdShowParameter): Cancelable` | 展示激励广告。`onUserEarnedRewardListener` 与 `rewardedShowListener` 均为可选；缺省时不会抛异常，但也不会收到宿主回调。 |
| `showNativeAd(showParameter: PlatformNativeAdShowParameter): Cancelable` | 展示原生广告。`showParameter.adShowListener` 可选；模板视图需通过 `templateView` 传入。 |

> `Platform*AdShowParameter` 中除 Activity / sceneName 外，还封装了各广告类型的监听器，便于在混类型兜底时保持回调一致。监听器留空时，SDK 仍会进行内部日志与上报，但宿主不会收到事件。

## Banner 广告

| 方法 | 说明 |
| --- | --- |
| `loadAndShowBannerAd(loadAndShowParameter: PlatformBannerAdLoadAndShowParameter): Destroyable` | 在指定容器内加载并展示 Banner。若未显式传入 `onAdClosed`，默认使用 `loadAndShowParameter.onAdClosed`。返回值可在页面销毁时手动 `destroy()`。 |
| `loadAndShowBannerAd(loadAndShowParameter: PlatformBannerAdLoadAndShowParameter, onAdClosed: (() -> Unit)?): Destroyable` | 同上，但允许覆盖默认的关闭回调。 |
| `loadAndShowBannerAdWithLifecycle(lifecycle: Lifecycle, loadAndShowParameter: PlatformBannerAdLoadAndShowParameter)` | 结合宿主 `Lifecycle` 自动管理 Banner 的加载、暂停与销毁，默认沿用参数中的 `onAdClosed`。 |
| `loadAndShowBannerAdWithLifecycle(lifecycle: Lifecycle, loadAndShowParameter: PlatformBannerAdLoadAndShowParameter, onAdClosed: (() -> Unit)?)` | 绑定生命周期并允许手动传入关闭回调。 |

## 相关参数 & 回调

- `PoseidonSetupProcessor`: SDK 初始化配置，在 `register` 时使用。
- `Platform*AdLoadParameter` / `Platform*AdShowParameter`: 各广告类型的载体，封装上下文、场景名、模板视图、监听器等。
- `OpenShowListener` / `InterstitialShowListener` / `RewardedShowListener` / `AdShowListener` / `OnUserEarnedRewardListener`: 宿主可选的展示回调接口。
- `Cancelable` / `Destroyable`: 广告展示或 Banner 控件的生命周期句柄。

通过以上 API，可以依次完成「注册并初始化 → 预加载或查询缓存 → 展示广告 → 监听/回收资源」的完整流程。监听器可根据业务需要选择性设置，未设置时 SDK 仍会保障内部流程及监控上报。***
