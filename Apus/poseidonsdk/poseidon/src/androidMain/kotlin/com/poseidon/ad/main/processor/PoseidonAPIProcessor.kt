package com.poseidon.ad.main.processor

import android.app.Activity
import android.content.Context
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.CheckResult
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.poseidon.ad.api.AdLoadListener
import com.poseidon.ad.api.AdShowListener
import com.poseidon.ad.api.FullScreenContentCallback
import com.poseidon.ad.api.InterstitialShowListener
import com.poseidon.ad.api.OnUserEarnedRewardListener
import com.poseidon.ad.api.OpenShowListener
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.api.RewardItem
import com.poseidon.ad.api.RewardedShowListener
import com.poseidon.ad.common.toolkit.Cancelable
import com.poseidon.ad.common.toolkit.Destroyable
import com.poseidon.ad.common.toolkit.ResultCallback
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.common.toolkit.StringHelper
import com.poseidon.ad.common.toolkit.TimeHelper
import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.common.toolkit.dispatchToBackground
import com.poseidon.ad.common.toolkit.isMainThread
import com.poseidon.ad.common.toolkit.isNetworkAvailable
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn
import com.poseidon.ad.common.toolkit.postOnMainThread
import com.poseidon.ad.common.toolkit.runOnMainThread
import com.poseidon.ad.common.storage.db.RoomPolicyDispatcher
import com.poseidon.ad.core.CoreRunner
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.main.adinfo.AD_TYPE_BANNER
import com.poseidon.ad.main.adinfo.AD_TYPE_INTERSTITIAL
import com.poseidon.ad.main.adinfo.AD_TYPE_NATIVE
import com.poseidon.ad.main.adinfo.AD_TYPE_OPEN
import com.poseidon.ad.main.adinfo.AD_TYPE_REWARDED
import com.poseidon.ad.main.adinfo.AdBase
import com.poseidon.ad.main.adinfo.ActivityLifecycleCallbacks
import com.poseidon.ad.main.adinfo.BannerAdBase
import com.poseidon.ad.main.adinfo.CODE_SUCCESS
import com.poseidon.ad.main.adinfo.CacheAd
import com.poseidon.ad.main.adinfo.ERROR_CODE_AD_LIMIT_SHOW
import com.poseidon.ad.main.adinfo.ERROR_CODE_AD_LOAD_CANCELED
import com.poseidon.ad.main.adinfo.ERROR_CODE_AD_MAX_IMPRESSION_COUNT_LIMIT
import com.poseidon.ad.main.adinfo.ERROR_CODE_AD_TYPE_MISMATCH
import com.poseidon.ad.main.adinfo.ERROR_CODE_ACTIVITY_DESTROYED
import com.poseidon.ad.main.adinfo.ERROR_CODE_CACHE_AD_TYPE_MISMATCH
import com.poseidon.ad.main.adinfo.ERROR_CODE_CALLBACK_AD_LOADED_BUT_QUERY_CACHE_AD_FAILED
import com.poseidon.ad.main.adinfo.ERROR_CODE_CRITICAL_PRESSURE_LOAD_SUPPRESSED
import com.poseidon.ad.main.adinfo.ERROR_CODE_CRITICAL_PRESSURE_SHOW_SUPPRESSED
import com.poseidon.ad.main.adinfo.ERROR_CODE_CTR_LIMIT
import com.poseidon.ad.main.adinfo.ERROR_CODE_FULLSCREEN_AD_IMPRESSION_INTERVAL_LIMIT
import com.poseidon.ad.main.adinfo.ERROR_CODE_GLOBAL_AD_SWITCH_OFF
import com.poseidon.ad.main.adinfo.ERROR_CODE_NETWORK_UNAVAILABLE
import com.poseidon.ad.main.adinfo.ERROR_CODE_NOTIFICATION_SPLASH_AD_CONFIG_CHECK_FAILED
import com.poseidon.ad.main.adinfo.ERROR_CODE_NOTIFICATION_SPLASH_AD_NO_CACHE
import com.poseidon.ad.main.adinfo.ERROR_CODE_NO_CACHE_DISALLOWED_LOAD
import com.poseidon.ad.main.adinfo.ERROR_CODE_NO_CACHE_THEN_LOAD_CANCELED
import com.poseidon.ad.main.adinfo.ERROR_CODE_NO_CACHE_THEN_LOAD_FAILED
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_AD_UNIT
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_AD_UNITS
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_CONFIGURATION
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_SCENES
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_STRATEGY
import com.poseidon.ad.main.adinfo.ERROR_CODE_SCENE_AD_SWITCH_OFF
import com.poseidon.ad.main.adinfo.ERROR_CODE_SCENE_NOT_CONFIGURED
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNSUPPORTED_AD_TYPE
import com.poseidon.ad.main.adinfo.FullScreenAdBase
import com.poseidon.ad.main.adinfo.FullscreenAdTypeDef
import com.poseidon.ad.main.adinfo.InterstitialAdBase
import com.poseidon.ad.main.adinfo.NativeAdBase
import com.poseidon.ad.main.adinfo.OpenAdBase
import com.poseidon.ad.main.adinfo.RewardedAdBase
import com.poseidon.ad.main.adinfo.addCacheAd
import com.poseidon.ad.main.adinfo.callOnAdLoadStart
import com.poseidon.ad.main.adinfo.consumeCacheAd
import com.poseidon.ad.main.adinfo.custom.NativeTemplateView
import com.poseidon.ad.main.adinfo.getAdTypeName
import com.poseidon.ad.main.adinfo.hasCacheAd
import com.poseidon.ad.main.adinfo.readHighEcpmThreshold
import com.poseidon.ad.main.adinfo.selectTopBidder
import com.poseidon.ad.main.adinfo.selectPrimaryBidderAgainstCompetitors
import com.poseidon.ad.main.load.BannerParameter
import com.poseidon.ad.main.load.InnerAdLoadAndShowCallback
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.InterstitialParameter
import com.poseidon.ad.main.load.NativeParameter
import com.poseidon.ad.main.load.OpenParameter
import com.poseidon.ad.main.load.RewardedParameter
import com.poseidon.ad.main.load.createBannerAdLoader
import com.poseidon.ad.main.load.createInterstitialAdLoader
import com.poseidon.ad.main.load.createNativeAdLoader
import com.poseidon.ad.main.load.createOpenAdLoader
import com.poseidon.ad.main.load.createRewardedAdLoader
import com.poseidon.ad.main.preload.new.NewUserAdPreloadGate
import com.poseidon.ad.main.preload.new.NewUserScopedBidderRegistry
import com.poseidon.ad.main.preload.new.shouldBlockOrdinaryPreload
import com.poseidon.ad.main.adinfo.destroySafely
import com.poseidon.ad.main.adinfo.isBelowEcpmBar
import com.poseidon.ad.main.preload.notification.NotificationSplashAdManager
import com.poseidon.ad.main.preload.notification.check
import com.poseidon.ad.main.preload.notification.isNotificationSplashAdShowScene
import com.poseidon.ad.main.preload.share.CeilAutomation
import com.poseidon.ad.main.preload.share.FloorAutomation
import com.poseidon.ad.main.preload.share.SharedCeilAdPool
import com.poseidon.ad.main.preload.share.SharedFloorAdPool
import com.poseidon.ad.main.preload.share.tryPreloadSharedAdCallShow
import com.poseidon.ad.main.preload.special.SpecificAdSceneCacheBackup
import com.poseidon.ad.main.price.Bidder
import com.poseidon.ad.main.processor.PoseidonAPIProcessor.platformLoadAdWithAnalyzer
import com.poseidon.ad.main.show.FullscreenAdImpressionIntervalLimit
import com.poseidon.ad.main.show.InnerFullScreenContentCallback
import com.poseidon.ad.main.show.getAdShowConfig
import com.poseidon.ad.main.show.mixed.showNativeMixInterstitialAd
import com.poseidon.ad.main.show.mixed.showNativeMixOpenAd
import com.poseidon.ad.main.show.mixed.showNativeMixRewardedAd
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.main.strategy.common.SceneCfg
import com.poseidon.ad.main.strategy.common.StrategyCfg
import com.poseidon.ad.restrict.FrequencyCheckResult
import com.poseidon.ad.restrict.ctr.CtrLimit
import com.poseidon.ad.restrict.gdpr.ConsentManagementPlatform
import com.poseidon.ad.restrict.gdpr.PlatformConsentFormShowParameter
import com.poseidon.ad.restrict.show.AdShowLimit
import com.poseidon.ad.restrict.show.AdShowLimitConfigProcessor
import com.poseidon.ad.restrict.unification.AdFrequencyControlManager
import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import com.poseidon.ad.statistics.biz.AdpvGapDetectionTracker
import com.poseidon.ad.statistics.biz.CriticalPressureReporter
import com.poseidon.ad.statistics.biz.UnitResponseTracker
import com.poseidon.ad.statistics.core.BizAdpvTracker
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicReference


@Suppress("SpellCheckingInspection")
object PoseidonAPIProcessor {

    internal const val TAG = "PoseidonAPIProcessor"

    private fun maybeUnlockOrdinaryPreloadForFullscreenShow(
        @FullscreenAdTypeDef fullScreenAdType: Int,
    ) {
        if (fullScreenAdType != AD_TYPE_INTERSTITIAL && fullScreenAdType != AD_TYPE_REWARDED) {
            return
        }
        NewUserAdPreloadGate.unlockAfterFirstShowCall()
    }

    /**
     * Call [action] when the privacy options entry point is required
     *
     * @see [https://developers.google.com/admob/android/privacy?hl=zh-cn#check-privacy-options]
     */
    fun doOnPrivacyOptionsIsRequired(action: () -> Unit) {
        ConsentManagementPlatform.doOnPrivacyOptionsIsRequired(action)
    }

    /**
     * Display the privacy options form
     *
     * @see [https://developers.google.com/admob/android/privacy?hl=zh-cn#present-privacy-options-form]
     */
    fun showPrivacyOptionsForm(parameter: PlatformConsentFormShowParameter) {
        ConsentManagementPlatform.showPrivacyOptionsForm(parameter = parameter)
    }

    fun tryPreloadSharedAdWhenAppStartup() {
        com.poseidon.ad.main.preload.share.tryPreloadSharedAdWhenAppStartup()
    }

    fun updateConfigurationFromBackend(json: String?) {
        CoreRunner.configHub.updateFromBackend(json)
    }

    fun loadInterstitialAd(loadParameter: PlatformInterstitialAdLoadParameter) {
        dispatchToBackground { CoreRunner.adFlow.loadInterstitialAd(loadParameter) }
    }

    fun loadOpenAd(loadParameter: PlatformOpenAdLoadParameter) {
        dispatchToBackground { CoreRunner.adFlow.loadOpenAd(loadParameter) }
    }

    fun hasCacheAd(parameter: PlatformHasCacheParameter): Boolean {
        return CoreRunner.strategyHub.hasCacheAd(parameter)
    }

    fun showOpenAd(
        showParameter: PlatformOpenAdShowParameter,
    ): Cancelable {
        val openShowListenerRelay = LifecycleCallbackRelay(
            lifecycle = showParameter.activity.lifecycle,
            callback = showParameter.openShowListener
        )
        logInfo(TAG) {
            "showOpenAd: " +
                    "showParameter=${showParameter}, " +
                    "openShowListener=${openShowListenerRelay.get()}."
        }
        AdpvGapDetectionTracker(
            sceneName = showParameter.sceneName,
            adType = AD_TYPE_OPEN,
        ).analyze()
        val sceneName = showParameter.sceneName
        val showParameterLog = showParameter.toString()
        val showCancelable = platformShowFullscreenAd(
            showParameter = showParameter,
            fullScreenAdType = AD_TYPE_OPEN,
            onUserEarnedRewardListener = null,
            fullScreenContentCallback = object : FullScreenContentCallback {
                override fun onAdClicked() {
                    logInfo(TAG) {
                        "showOpenAd.onAdClicked: " +
                                "showParameter=${showParameterLog}, " +
                                "openShowListener=${openShowListenerRelay.get()}."
                    }
                    openShowListenerRelay.get()?.onAdClicked()
                }

                override fun onAdDismissedFullScreenContent() {
                    try {
                        logInfo(TAG) {
                            "showOpenAd.onAdDismissedFullScreenContent: " +
                                    "showParameter=${showParameterLog}, " +
                                    "openShowListener=${openShowListenerRelay.get()}."
                        }
                        openShowListenerRelay.get()?.onAdDismissedFullScreenContent()
                    } finally {
                        openShowListenerRelay.cancel()
                    }
                }

                override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
                    try {
                        logInfo(TAG) {
                            "showOpenAd.onAdFailedToShowFullScreenContent: " +
                                    "showParameter=${showParameterLog}, " +
                                    "openShowListener=${openShowListenerRelay.get()}, " +
                                    "code=${code}, " +
                                    "message=${message}."
                        }
                        BizAdpvTracker(
                            sceneName = sceneName,
                            adType = AD_TYPE_OPEN,
                            resultCode = code,
                            resultMessage = message,
                        ).analyze()

                        if (code != ERROR_CODE_NO_CACHE_THEN_LOAD_CANCELED) {
                            openShowListenerRelay.get()?.onAdFailedToShowFullScreenContent(code, message)
                        }
                    } finally {
                        openShowListenerRelay.cancel()
                    }
                }

                override fun onAdShowedFullScreenContent() {
                    logInfo(TAG) {
                        "showOpenAd.onAdShowedFullScreenContent: " +
                                "showParameter=${showParameterLog}, " +
                                "openShowListener=${openShowListenerRelay.get()}."
                    }
                    BizAdpvTracker(
                        sceneName = sceneName,
                        adType = AD_TYPE_OPEN,
                        resultCode = CODE_SUCCESS,
                        resultMessage = "Advertisement display success",
                    ).analyze()
                    openShowListenerRelay.get()?.onAdShowedFullScreenContent()
                }
            }
        )
        return showCancelable.withDetachedHostCallbacksOnCancel(openShowListenerRelay)
    }

    fun showInterstitialAd(
        showParameter: PlatformInterstitialAdShowParameter,
    ): Cancelable {
        val interstitialShowListenerRelay = LifecycleCallbackRelay(
            lifecycle = showParameter.activity.lifecycle,
            callback = showParameter.interstitialShowListener
        )
        logInfo(TAG) {
            "showInterstitialAd: " +
                    "showParameter=${showParameter}, " +
                    "interstitialShowListener=${interstitialShowListenerRelay.get()}."
        }
        AdpvGapDetectionTracker(
            sceneName = showParameter.sceneName,
            adType = AD_TYPE_INTERSTITIAL,
        ).analyze()
        val sceneName = showParameter.sceneName
        val showParameterLog = showParameter.toString()
        val showCancelable = platformShowFullscreenAd(
            showParameter = showParameter,
            fullScreenAdType = AD_TYPE_INTERSTITIAL,
            onUserEarnedRewardListener = null,
            fullScreenContentCallback = object : FullScreenContentCallback {
                override fun onAdClicked() {
                    logInfo(TAG) {
                        "showInterstitialAd.onAdClicked: " +
                                "showParameter=${showParameterLog}, " +
                                "interstitialShowListener=${interstitialShowListenerRelay.get()}."
                    }
                    interstitialShowListenerRelay.get()?.onAdClicked()
                }

                override fun onAdDismissedFullScreenContent() {
                    try {
                        logInfo(TAG) {
                            "showInterstitialAd.onAdDismissedFullScreenContent: " +
                                    "showParameter=${showParameterLog}, " +
                                    "interstitialShowListener=${interstitialShowListenerRelay.get()}."
                        }
                        interstitialShowListenerRelay.get()?.onAdDismissedFullScreenContent()
                    } finally {
                        interstitialShowListenerRelay.cancel()
                    }
                }

                override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
                    try {
                        logInfo(TAG) {
                            "showInterstitialAd.onAdFailedToShowFullScreenContent: " +
                                    "showParameter=${showParameterLog}, " +
                                    "interstitialShowListener=${interstitialShowListenerRelay.get()}, " +
                                    "code=${code}, " +
                                    "message=${message}."
                        }
                        BizAdpvTracker(
                            sceneName = sceneName,
                            adType = AD_TYPE_INTERSTITIAL,
                            resultCode = code,
                            resultMessage = message,
                        ).analyze()

                        if (code != ERROR_CODE_NO_CACHE_THEN_LOAD_CANCELED) {
                            interstitialShowListenerRelay.get()?.onAdFailedToShowFullScreenContent(code, message)
                        }
                    } finally {
                        interstitialShowListenerRelay.cancel()
                    }
                }

                override fun onAdShowedFullScreenContent() {
                    logInfo(TAG) {
                        "showInterstitialAd.onAdShowedFullScreenContent: " +
                                "showParameter=${showParameterLog}, " +
                                "interstitialShowListener=${interstitialShowListenerRelay.get()}."
                    }
                    BizAdpvTracker(
                        sceneName = sceneName,
                        adType = AD_TYPE_INTERSTITIAL,
                        resultCode = CODE_SUCCESS,
                        resultMessage = "Advertisement display success",
                    ).analyze()
                    interstitialShowListenerRelay.get()?.onAdShowedFullScreenContent()
                }
            }
        )
        return showCancelable.withDetachedHostCallbacksOnCancel(interstitialShowListenerRelay)
    }

    fun loadRewardedAd(loadParameter: PlatformRewardedAdLoadParameter) {
        dispatchToBackground { CoreRunner.adFlow.loadRewardedAd(loadParameter) }
    }

    fun showRewardedAd(
        showParameter: PlatformRewardedAdShowParameter,
    ): Cancelable {
        val onUserEarnedRewardListenerRelay = LifecycleCallbackRelay(
            lifecycle = showParameter.activity.lifecycle,
            callback = showParameter.onUserEarnedRewardListener
        )
        val rewardedShowListenerRelay = LifecycleCallbackRelay(
            lifecycle = showParameter.activity.lifecycle,
            callback = showParameter.rewardedShowListener
        )
        logInfo(TAG) {
            "showRewardedAd: " +
                    "showParameter=${showParameter}, " +
                    "onUserEarnedRewardListener=${onUserEarnedRewardListenerRelay.get()}, " +
                    "rewardedShowListener=${rewardedShowListenerRelay.get()}."
        }
        AdpvGapDetectionTracker(
            sceneName = showParameter.sceneName,
            adType = AD_TYPE_REWARDED,
        ).analyze()
        val sceneName = showParameter.sceneName
        val showParameterLog = showParameter.toString()
        val showCancelable = platformShowFullscreenAd(
            showParameter = showParameter,
            fullScreenAdType = AD_TYPE_REWARDED,
            onUserEarnedRewardListener = object : OnUserEarnedRewardListener {
                override fun onUserEarnedReward(reward: RewardItem) {
                    onUserEarnedRewardListenerRelay.take()?.onUserEarnedReward(reward)
                }
            },
            fullScreenContentCallback = object : FullScreenContentCallback {
                override fun onAdClicked() {
                    logInfo(TAG) {
                        "showRewardedAd.onAdClicked: " +
                                "showParameter=${showParameterLog}, " +
                                "onUserEarnedRewardListener=${onUserEarnedRewardListenerRelay.get()}, " +
                                "rewardedShowListener=${rewardedShowListenerRelay.get()}."
                    }
                    rewardedShowListenerRelay.get()?.onAdClicked()
                }

                override fun onAdDismissedFullScreenContent() {
                    try {
                        logInfo(TAG) {
                            "showRewardedAd.onAdDismissedFullScreenContent: " +
                                    "showParameter=${showParameterLog}, " +
                                    "onUserEarnedRewardListener=${onUserEarnedRewardListenerRelay.get()}, " +
                                    "rewardedShowListener=${rewardedShowListenerRelay.get()}."
                        }
                        rewardedShowListenerRelay.get()?.onAdDismissedFullScreenContent()
                    } finally {
                        rewardedShowListenerRelay.cancel()
                        onUserEarnedRewardListenerRelay.cancel()
                    }
                }

                override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
                    try {
                        logInfo(TAG) {
                            "showRewardedAd.onAdFailedToShowFullScreenContent: " +
                                    "showParameter=${showParameterLog}, " +
                                    "onUserEarnedRewardListener=${onUserEarnedRewardListenerRelay.get()}, " +
                                    "rewardedShowListener=${rewardedShowListenerRelay.get()}, " +
                                    "code=${code}, " +
                                    "message=${message}."
                        }
                        BizAdpvTracker(
                            sceneName = sceneName,
                            adType = AD_TYPE_REWARDED,
                            resultCode = code,
                            resultMessage = message
                        ).analyze()

                        if (code != ERROR_CODE_NO_CACHE_THEN_LOAD_CANCELED) {
                            rewardedShowListenerRelay.get()?.onAdFailedToShowFullScreenContent(code, message)
                        }
                    } finally {
                        rewardedShowListenerRelay.cancel()
                        onUserEarnedRewardListenerRelay.cancel()
                    }
                }

                override fun onAdShowedFullScreenContent() {
                    logInfo(TAG) {
                        "showRewardedAd.onAdShowedFullScreenContent: " +
                                "showParameter=${showParameterLog}, " +
                                "onUserEarnedRewardListener=${onUserEarnedRewardListenerRelay.get()}, " +
                                "rewardedShowListener=${rewardedShowListenerRelay.get()}."
                    }
                    BizAdpvTracker(
                        sceneName = sceneName,
                        adType = AD_TYPE_REWARDED,
                        resultCode = CODE_SUCCESS,
                        resultMessage = "Advertisement display success"
                    ).analyze()
                    rewardedShowListenerRelay.get()?.onAdShowedFullScreenContent()
                }
            }
        )
        return showCancelable.withDetachedHostCallbacksOnCancel(
            rewardedShowListenerRelay,
            onUserEarnedRewardListenerRelay
        )
    }

    fun loadNativeAd(loadParameter: PlatformNativeAdLoadParameter) {
        dispatchToBackground { CoreRunner.adFlow.loadNativeAd(loadParameter) }
    }

    fun showNativeAd(
        showParameter: PlatformNativeAdShowParameter,
    ): Cancelable {
        val adShowListener = showParameter.adShowListener
        logInfo(TAG) {
            "showNativeAd: " +
                    "showParameter=${showParameter}, " +
                    "listener=${adShowListener}"
        }
        AdpvGapDetectionTracker(
            sceneName = showParameter.sceneName,
            adType = AD_TYPE_NATIVE,
        ).analyze()
        return platformShowNativeAd(
            showParameter = showParameter,
            adShowListener = object : AdShowListener {
                override fun onAdClicked() {
                    logInfo(TAG) {
                        "showNativeAd.onAdClicked: " +
                                "showParameter=${showParameter}, " +
                                "listener=${adShowListener}"
                    }
                    // 记录广告点击 - 频控统计
                    AdFrequencyControlManager.recordAdClick(showParameter.sceneName, AD_TYPE_NATIVE)
                    adShowListener?.onAdClicked()
                }

                override fun onAdClosed() {
                    logInfo(TAG) {
                        "showNativeAd.onAdClosed: " +
                                "showParameter=${showParameter}, " +
                                "listener=${adShowListener}"
                    }
                    adShowListener?.onAdClosed()
                }

                override fun onAdShowFailed(code: Int, message: String) {
                    logInfo(TAG) {
                        "showNativeAd.onAdFailedToShow: " +
                                "showParameter=${showParameter}, " +
                                "listener=${adShowListener}, " +
                                "code=${code}, " +
                                "message=${message}."
                    }
                    BizAdpvTracker(
                        sceneName = showParameter.sceneName,
                        adType = AD_TYPE_NATIVE,
                        resultCode = code,
                        resultMessage = message
                    ).analyze()

                    if (code != ERROR_CODE_NO_CACHE_THEN_LOAD_CANCELED) {
                        adShowListener?.onAdShowFailed(code, message)
                    }
                }

                override fun onAdShowSuccess() {
                    logInfo(TAG) {
                        "showNativeAd.onAdImpression: " +
                                "showParameter=${showParameter}, " +
                                "listener=${adShowListener}"
                    }
                    BizAdpvTracker(
                        sceneName = showParameter.sceneName,
                        adType = AD_TYPE_NATIVE,
                        resultCode = CODE_SUCCESS,
                        resultMessage = "Advertisement display success",
                    ).analyze()

                    // 记录广告展示 - 频控统计
                    AdFrequencyControlManager.recordAdDisplay(showParameter.sceneName,
                        AD_TYPE_NATIVE
                    )
                    adShowListener?.onAdShowSuccess()
                }
            }
        )
    }

    internal fun <PlatformAdLoadParameter> platformPreload(loadParameter: PlatformAdLoadParameter) {
        val parameterLoadCallback = whenPlatformAdLoadParameter(
            loadParameter = loadParameter,
            interstitialAdLoadParameterConverter = PlatformInterstitialAdLoadParameter::adLoadCallback,
            rewardedAdLoadParameterConverter = PlatformRewardedAdLoadParameter::adLoadCallback,
            nativeAdLoadParameterConverter = PlatformNativeAdLoadParameter::adLoadCallback,
            openAdLoadParameterConverter = PlatformOpenAdLoadParameter::adLoadCallback
        )
        val loadGateTripReason = CriticalPressureBreaker.loadGateTripReason()
        if (loadGateTripReason != null) {
            val sceneName = whenPlatformAdLoadParameter(
                loadParameter = loadParameter,
                interstitialAdLoadParameterConverter = PlatformInterstitialAdLoadParameter::sceneName,
                rewardedAdLoadParameterConverter = PlatformRewardedAdLoadParameter::sceneName,
                nativeAdLoadParameterConverter = PlatformNativeAdLoadParameter::sceneName,
                openAdLoadParameterConverter = PlatformOpenAdLoadParameter::sceneName
            )
            val message = CriticalPressureReporter.loadSuppressedMessage(sceneName, loadGateTripReason)
            logWarn(tag=TAG) { "platformPreload suppressed at critical pressure: $message" }
            CriticalPressureReporter.reportLoadGate(
                sceneName = sceneName,
                message = message,
            )
            parameterLoadCallback?.onAdLoadFailed(
                code = ERROR_CODE_CRITICAL_PRESSURE_LOAD_SUPPRESSED,
                message = message,
            )
            return
        }
        platformLoadAdWithAnalyzer(loadParameter = loadParameter, loadCallback = parameterLoadCallback)
    }

    internal fun <PlatformAdLoadParameter> platformLoadAdWithAnalyzer(
        loadParameter: PlatformAdLoadParameter,
        loadCallback: AdLoadListener?
    ) {
        val sessionId = StringHelper.newSessionId()
        val sceneName = whenPlatformAdLoadParameter(
            loadParameter = loadParameter,
            interstitialAdLoadParameterConverter = PlatformInterstitialAdLoadParameter::sceneName,
            rewardedAdLoadParameterConverter = PlatformRewardedAdLoadParameter::sceneName,
            nativeAdLoadParameterConverter = PlatformNativeAdLoadParameter::sceneName,
            openAdLoadParameterConverter = PlatformOpenAdLoadParameter::sceneName
        )
        platformLoadAd(
            sessionId = sessionId,
            sceneName = sceneName,
            loadParameter = loadParameter,
            loadCallback = object : AdLoadListener {
                override fun onAdLoadFailed(code: Int, message: String) {
                    runOnMainThread {
                        loadCallback?.onAdLoadFailed(code = code, message = message)
                    }
                    UnitResponseTracker(
                        sessionId = sessionId,
                        sceneName = sceneName,
                        resultCode = code,
                        resultMessage = message,
                    ).analyze()
                }

                override fun onAdLoadSuccess() {
                    runOnMainThread {
                        loadCallback?.onAdLoadSuccess()
                    }
                    UnitResponseTracker(
                        sessionId = sessionId,
                        sceneName = sceneName,
                        resultCode = CODE_SUCCESS,
                        resultMessage = "Advertisement load success",
                    ).analyze()
                }
            })
    }

    private fun <PlatformAdLoadParameter> platformLoadAd(
        sessionId: String,
        sceneName: String,
        loadParameter: PlatformAdLoadParameter,
        loadCallback: AdLoadListener,
        persistedFrequencyResult: FrequencyCheckResult? = null,
    ) {
        if (!isGlobalSwitchOn()) {
            loadCallback.onAdLoadFailed(
                code = ERROR_CODE_GLOBAL_AD_SWITCH_OFF,
                message = "ERROR_CODE_GLOBAL_AD_SWITCH_OFF:sceneName:${sceneName}, switch=${PoseidonConfig.Companion.get()?.switch}"
            )
            return
        }

        if (!isNetworkAvailable()) {
            loadCallback.onAdLoadFailed(
                code = ERROR_CODE_NETWORK_UNAVAILABLE,
                message = "NETWORK_UNAVAILABLE:sceneName:${sceneName}"
            )
            return
        }

        if (!AdShowLimit.canLoadAd()) {
            loadCallback.onAdLoadFailed(
                code = ERROR_CODE_AD_MAX_IMPRESSION_COUNT_LIMIT,
                message = "ERROR_CODE_AD_MAX_IMPRESSION_COUNT_LIMIT:sceneName:${sceneName}"
            )
            return
        }
        when (val result = resolveScene(sceneName)) {
            is ResultCallback.Failure -> {
                loadCallback.onAdLoadFailed(code = result.code, message = result.message)
            }

            is ResultCallback.Success -> {
                val (poseidonConfigVersion, sceneToStrategyPair) = result.value
                val scene = sceneToStrategyPair.first
                val strategy = sceneToStrategyPair.second

                val frequencyResult = persistedFrequencyResult ?: run {
                    val dispatchPolicyCheck = if (
                        AdFrequencyControlManager.isPersistedFrequencyConfigured()
                    ) {
                        RoomPolicyDispatcher::dispatch
                    } else {
                        ::dispatchToBackground
                    }
                    dispatchPolicyCheck {
                        val result = AdFrequencyControlManager.checkLoadFrequency(
                            sceneName,
                            scene.adType,
                        )
                        dispatchToBackground {
                            platformLoadAd(
                                sessionId = sessionId,
                                sceneName = sceneName,
                                loadParameter = loadParameter,
                                loadCallback = loadCallback,
                                persistedFrequencyResult = result,
                            )
                        }
                    }
                    return
                }

                // 频控检查 - 在加载广告时
                AdFrequencyControlManager.convertFrequencyResultToErrorCode(frequencyResult)?.let { errorCode ->
                    loadCallback.onAdLoadFailed(
                        code = errorCode,
                        message = "Frequency control blocked ad load: ${(frequencyResult as FrequencyCheckResult.Block).reason}"
                    )
                    return
                }

                if (strategy == null) {
                    loadCallback.onAdLoadFailed(
                        code = ERROR_CODE_NULL_STRATEGY,
                        message = "There is no advertising strategy that matches the scene: sceneName:${scene.name}"
                    )
                    return
                }

                val adUnits = runCatching { strategy.placements }.onFailure { throwable ->
                    logWarn(throwable = throwable, tag = TAG) {
                        "platformLoadAd.resolveScenePlacements: " +
                                "failed to read placements, sceneName=${scene.name}, strategyId=${strategy.id}"
                    }
                    loadCallback.onAdLoadFailed(
                        ERROR_CODE_NULL_AD_UNITS,
                        "The ad unit configuration in the scene is invalid: sceneName=${scene.name}"
                    )
                }.getOrElse { return }
                if (adUnits.isEmpty()) {
                    loadCallback.onAdLoadFailed(
                        ERROR_CODE_NULL_AD_UNITS,
                        "The ad unit configuration in the scene is empty: sceneName:${scene.name}"
                    )
                    return
                }
                OrdinaryPlacementStrategyLoader.load(
                    sessionId = sessionId,
                    loadParameter = loadParameter,
                    scene = scene,
                    strategy = strategy,
                    poseidonConfigVersion = poseidonConfigVersion,
                    loadCallback = loadCallback
                )
            }
        }
    }

    @CheckResult
    private fun <PlatformAdShowParameter : Any> platformShowFullscreenAd(
        showParameter: PlatformAdShowParameter,
        @FullscreenAdTypeDef fullScreenAdType: Int,
        onUserEarnedRewardListener: OnUserEarnedRewardListener?,
        fullScreenContentCallback: FullScreenContentCallback,
        persistedGateResult: PersistedFullscreenGateResult? = null,
        showAttemptEffectsApplied: Boolean = false,
    ): Cancelable {
        if (!isMainThread()) {
            return postCancelableOnMainThread {
                platformShowFullscreenAd(
                    showParameter = showParameter,
                    fullScreenAdType = fullScreenAdType,
                    onUserEarnedRewardListener = onUserEarnedRewardListener,
                    fullScreenContentCallback = fullScreenContentCallback,
                    persistedGateResult = persistedGateResult,
                    showAttemptEffectsApplied = showAttemptEffectsApplied,
                )
            }
        }

        val sceneName = whenPlatformAdShowParameter(
            showParameter = showParameter,
            interstitialAdShowParameterConverter = PlatformInterstitialAdShowParameter::sceneName,
            rewardedAdShowParameterConverter = PlatformRewardedAdShowParameter::sceneName,
            openAdShowParameterConverter = PlatformOpenAdShowParameter::sceneName,
            nativeAdShowParameterConverter = PlatformNativeAdShowParameter::sceneName
        )
        val loadParameter: Any = whenPlatformAdShowParameter(
            showParameter = showParameter,
            interstitialAdShowParameterConverter = PlatformInterstitialAdShowParameter::toLoadParameter,
            rewardedAdShowParameterConverter = PlatformRewardedAdShowParameter::toLoadParameter,
            openAdShowParameterConverter = PlatformOpenAdShowParameter::toLoadParameter,
            nativeAdShowParameterConverter = PlatformNativeAdShowParameter::toLoadParameter
        )

        // Room policy evaluation resumes this method on main. Apply show-call side effects only
        // on the first entry so the Room-to-main continuation cannot unlock/preload twice.
        if (!showAttemptEffectsApplied) {
            maybeUnlockOrdinaryPreloadForFullscreenShow(fullScreenAdType = fullScreenAdType)
            tryPreloadSharedAdCallShow(sceneName = sceneName)
        }

        fun CacheAd<*>.show(
            rowId: Long?,
            sceneName: String,
            loadParameter: Any,
            allowPreloadWhenFullscreenAdDismissed: Boolean,
            allowPreloadWhenAdFailedToShow: Boolean,
            onUserEarnedRewardListener: OnUserEarnedRewardListener?,
            fullScreenContentCallback: FullScreenContentCallback
        ) {
            ad.updateSceneName(sceneName = sceneName)

            logInfo(TAG) {
                "FullscreenAdPrepareToShow(number: ${number}): " +
                        "showParameter=${showParameter}"
            }

            fun destroyAfterTerminalCallback(eventName: String, reporterName: String) {
                runCatching { ad.destroy() }
                    .onFailure { t ->
                        logWarn(throwable = t, tag = TAG) {
                            "${eventName}(number: ${number}): destroy failed!"
                        }
                        if (PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().debug) {
                            error(t)
                        } else {
                            ErrorDetectionReporter(
                                name = reporterName,
                                extra = t.message ?: t.stackTraceToString()
                            ).analyze()
                        }
                    }
                    .onSuccess {
                        logInfo(TAG) {
                            "${eventName}(number: ${number}): destroy success!"
                        }
                    }
            }

            val callback = object : InnerFullScreenContentCallback {
                override fun onAdDismissedFullScreenContent() {
                    try {
                        if (fullScreenAdType == AD_TYPE_REWARDED && ad !is RewardedAdBase) {
                            onUserEarnedRewardListener?.onUserEarnedReward(RewardItem.Companion.UNKNOWN)
                        }
                        fullScreenContentCallback.onAdDismissedFullScreenContent()
                        if (allowPreloadWhenFullscreenAdDismissed) {
                            platformPreload(loadParameter = loadParameter)
                        }
                    } finally {
                        destroyAfterTerminalCallback(
                            eventName = "onAdDismissedFullScreenContent",
                            reporterName = "DestroyAdOnDismiss"
                        )
                    }
                }

                override fun onAdShowedFullScreenContent() {
                    AdShowLimitConfigProcessor.updateShown(rowId = rowId)
                    // 记录广告展示 - 频控统计
                    AdFrequencyControlManager.recordAdDisplay(sceneName, fullScreenAdType)
                    fullScreenContentCallback.onAdShowedFullScreenContent()
                }

                override fun onAdClicked() {
                    // 记录广告点击 - 频控统计
                    AdFrequencyControlManager.recordAdClick(sceneName, fullScreenAdType)
                    fullScreenContentCallback.onAdClicked()
                }

                override fun onAdFailedToShowFullScreenContent(
                    code: Int,
                    message: String
                ) {
                    try {
                        fullScreenContentCallback.onAdFailedToShowFullScreenContent(code, message)
                        if (allowPreloadWhenAdFailedToShow) {
                            platformPreload(loadParameter = loadParameter)
                        }
                    } finally {
                        destroyAfterTerminalCallback(
                            eventName = "onAdFailedToShowFullScreenContent",
                            reporterName = "DestroyAdOnFailedToShow"
                        )
                    }
                }
            }
            if (ad is FullScreenAdBase) {
                ad.setFullScreenContentCallback(callback)
                val showActivity = resolveUsableFullscreenShowActivity(showParameter)
                if (showActivity == null) {
                    fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                        code = ERROR_CODE_ACTIVITY_DESTROYED,
                        message = "No usable Activity for fullscreen ad show: sceneName:${sceneName}"
                    )
                    return
                }
                when (ad) {
                    is InterstitialAdBase -> {
                        val sp: PlatformInterstitialAdShowParameter? = when (fullScreenAdType) {
                            AD_TYPE_INTERSTITIAL -> {
                                (showParameter as PlatformInterstitialAdShowParameter).withActivity(showActivity)
                            }

                            AD_TYPE_REWARDED -> {
                                (showParameter as PlatformRewardedAdShowParameter).withActivity(showActivity).toInterstitialAdShowParameter()
                            }

                            AD_TYPE_OPEN -> {
                                (showParameter as PlatformOpenAdShowParameter).withActivity(showActivity).toInterstitialAdShowParameter()
                            }

                            else -> null
                        }
                        if (sp != null) {
                            when (val showResult = ad.show(
                                showParameter = sp,
                                showConfig = getAdShowConfig { setSceneName(sceneName) })) {
                                is ShowResultCallback.Failure -> {
                                    fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                                        code = showResult.code,
                                        message = showResult.message
                                    )
                                }

                                ShowResultCallback.Success -> {
                                    // nothing to do
                                }
                            }
                        } else {
                            fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                                code = ERROR_CODE_CACHE_AD_TYPE_MISMATCH,
                                message = "Cache Ad Type Mismatch: sceneName:${sceneName}, expected:${
                                    getAdTypeName(
                                        fullScreenAdType
                                    )
                                }, actual: ${getAdTypeName(ad.getAdType())}"
                            )
                        }
                    }

                    is OpenAdBase -> {
                        val sp: PlatformOpenAdShowParameter? = when (fullScreenAdType) {
                            AD_TYPE_INTERSTITIAL -> {
                                (showParameter as PlatformInterstitialAdShowParameter).withActivity(showActivity).toOpenAdShowParameter()
                            }

                            AD_TYPE_REWARDED -> {
                                (showParameter as PlatformRewardedAdShowParameter).withActivity(showActivity).toOpenAdShowParameter()
                            }

                            AD_TYPE_OPEN -> {
                                (showParameter as PlatformOpenAdShowParameter).withActivity(showActivity)
                            }

                            else -> null
                        }
                        if (sp != null) {
                            when (val showResult = ad.show(
                                showParameter = sp,
                                showConfig = getAdShowConfig { setSceneName(sceneName) })) {
                                is ShowResultCallback.Failure -> {
                                    fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                                        code = showResult.code,
                                        message = showResult.message
                                    )
                                }

                                ShowResultCallback.Success -> {
                                    // nothing to do
                                }
                            }
                        } else {
                            fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                                code = ERROR_CODE_CACHE_AD_TYPE_MISMATCH,
                                message = "Cache Ad Type Mismatch: sceneName:${sceneName}, expect:${
                                    getAdTypeName(
                                        fullScreenAdType
                                    )
                                }, actual: ${getAdTypeName(ad.getAdType())}"
                            )
                        }
                    }

                    is RewardedAdBase -> {
                        val sp: PlatformRewardedAdShowParameter? = when (fullScreenAdType) {
                            AD_TYPE_INTERSTITIAL -> {
                                (showParameter as PlatformInterstitialAdShowParameter).withActivity(showActivity).toRewardedAdShowParameter()
                            }

                            AD_TYPE_OPEN -> {
                                (showParameter as PlatformOpenAdShowParameter).withActivity(showActivity).toRewardedAdShowParameter()
                            }

                            AD_TYPE_REWARDED -> {
                                (showParameter as PlatformRewardedAdShowParameter).withActivity(showActivity)
                            }

                            else -> null
                        }
                        if (sp != null) {
                            when (val showResult = ad.show(
                                showParameter = sp,
                                showConfig = getAdShowConfig { setSceneName(sceneName) },
                                onUserEarnedRewardListener = onUserEarnedRewardListener
                            )) {
                                is ShowResultCallback.Failure -> {
                                    fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                                        code = showResult.code,
                                        message = showResult.message
                                    )
                                }

                                ShowResultCallback.Success -> {
                                    // nothing to do
                                }
                            }
                        } else {
                            fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                                code = ERROR_CODE_CACHE_AD_TYPE_MISMATCH,
                                message = "Cache ad type mismatch: sceneName:${sceneName}, expect:${
                                    getAdTypeName(
                                        fullScreenAdType
                                    )
                                }, actual: ${getAdTypeName(ad.getAdType())}"
                            )
                        }
                    }

                    else -> {
                        fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                            code = ERROR_CODE_CACHE_AD_TYPE_MISMATCH,
                            message = "Cache ad type mismatch: sceneName:${sceneName}, expect:${
                                getAdTypeName(
                                    fullScreenAdType
                                )
                            }, actual: ${getAdTypeName(ad.getAdType())}"
                        )
                    }
                }
            } else if (ad is NativeAdBase) {
                when (fullScreenAdType) {
                    AD_TYPE_INTERSTITIAL -> showNativeMixInterstitialAd(
                        platformAdShowParameter = showParameter as PlatformInterstitialAdShowParameter,
                        ad = ad,
                        callback = callback
                    )

                    AD_TYPE_REWARDED -> showNativeMixRewardedAd(
                        platformAdShowParameter = showParameter as PlatformRewardedAdShowParameter,
                        ad = ad,
                        callback = callback,
                        onUserEarnedRewardListener = object : OnUserEarnedRewardListener {
                            override fun onUserEarnedReward(reward: RewardItem) {
                                // nothing to do
                            }
                        }
                    )

                    AD_TYPE_OPEN -> showNativeMixOpenAd(
                        platformAdShowParameter = showParameter as PlatformOpenAdShowParameter,
                        ad = ad,
                        callback = callback,
                    )

                    else -> {
                        fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                            code = ERROR_CODE_CACHE_AD_TYPE_MISMATCH,
                            message = "Cache ad type mismatch: sceneName:${sceneName}, expect:${
                                getAdTypeName(
                                    fullScreenAdType
                                )
                            }, actual: ${getAdTypeName(ad.getAdType())}"
                        )
                    }
                }
            } else {
                fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                    code = ERROR_CODE_CACHE_AD_TYPE_MISMATCH,
                    message = "Cache ad type mismatch: sceneName:${sceneName}, expect:${
                        getAdTypeName(
                            fullScreenAdType
                        )
                    }, actual: ${getAdTypeName(ad.getAdType())}"
                )
            }
        }

        if (!isGlobalSwitchOn()) {
            fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                code = ERROR_CODE_GLOBAL_AD_SWITCH_OFF,
                message = "ERROR_CODE_GLOBAL_AD_SWITCH_OFF:sceneName:${sceneName}, switch=${PoseidonConfig.Companion.get()?.switch}"
            )
            return Cancelable.Companion.EMPTY
        }

        val resolvedPersistedGate = persistedGateResult ?: return postCancelableAfterRoomPolicy(
            prepare = {
                val frequencyResult = AdFrequencyControlManager.checkShowFrequency(
                    sceneName,
                    fullScreenAdType,
                )
                PersistedFullscreenGateResult(
                    frequencyResult = frequencyResult,
                    showLimitResult = if (frequencyResult is FrequencyCheckResult.Block) {
                        null
                    } else {
                        AdShowLimitConfigProcessor.check(sceneName)
                    },
                )
            },
            action = { result ->
                platformShowFullscreenAd(
                    showParameter = showParameter,
                    fullScreenAdType = fullScreenAdType,
                    onUserEarnedRewardListener = onUserEarnedRewardListener,
                    fullScreenContentCallback = fullScreenContentCallback,
                    persistedGateResult = result,
                    showAttemptEffectsApplied = true,
                )
            },
        )

        // 频控检查 - 在展示广告时
        val frequencyResult = resolvedPersistedGate.frequencyResult
        AdFrequencyControlManager.convertFrequencyResultToErrorCode(frequencyResult)?.let { errorCode ->
            fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                code = errorCode,
                message = "Frequency control blocked ad show: ${(frequencyResult as FrequencyCheckResult.Block).reason}"
            )
            return Cancelable.Companion.EMPTY
        }

        val (rowId, canShow) = resolvedPersistedGate.showLimitResult ?: run {
            logWarn(tag = TAG) {
                "Show-limit result missing after frequency gate passed; allow show to avoid host crash"
            }
            null to true
        }
        if (!canShow) {
            fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                code = ERROR_CODE_AD_LIMIT_SHOW,
                message = "ERROR_CODE_AD_LIMIT_SHOW:sceneName:${sceneName}"
            )
            return Cancelable.Companion.EMPTY
        }

        if (!AdShowLimit.canShowAd()) {
            fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                code = ERROR_CODE_AD_MAX_IMPRESSION_COUNT_LIMIT,
                message = "ERROR_CODE_AD_MAX_IMPRESSION_COUNT_LIMIT:sceneName:${sceneName}"
            )
            return Cancelable.Companion.EMPTY
        }

        if (!CtrLimit.canShowAd()) {
            fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                code = ERROR_CODE_CTR_LIMIT,
                message = "ERROR_CODE_CTR_LIMIT:sceneName:${sceneName}"
            )
            return Cancelable.Companion.EMPTY
        }

        if (!FullscreenAdImpressionIntervalLimit.canShowAd(fullScreenAdType = fullScreenAdType)) {
            fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                code = ERROR_CODE_FULLSCREEN_AD_IMPRESSION_INTERVAL_LIMIT,
                message = "ERROR_CODE_FULLSCREEN_AD_IMPRESSION_INTERVAL_LIMIT:sceneName:${sceneName}"
            )
            return Cancelable.Companion.EMPTY
        }

        for (interceptor in platformShowFullscreenAdInterceptor()) {
            if (interceptor.intercept(
                    showParameter = showParameter, fullScreenAdType = fullScreenAdType,
                    onUserEarnedRewardListener = onUserEarnedRewardListener,
                    fullScreenContentCallback = fullScreenContentCallback
                )
            ) {
                return Cancelable.Companion.EMPTY
            }
        }
        if (!isNetworkAvailable() && !allowShowAdNoNetwork()) {
            fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                code = ERROR_CODE_NETWORK_UNAVAILABLE,
                message = "NETWORK_UNAVAILABLE:sceneName:${sceneName}"
            )
            return Cancelable.Companion.EMPTY
        }

        /**
         * Critical-pressure show gate: when the breaker is tripped, show only a cached ad whose
         * eCPM clears the exemption bar; otherwise destroy the candidates and suppress the show.
         * Returns true when the gate handled the request (shown or suppressed).
         */
        fun interveneCriticalPressureShowGate(
            bidders: List<Bidder>,
            rowId: Long?,
            sceneName: String,
            loadParameter: Any,
        ): Boolean {
            val tripReason = CriticalPressureBreaker.showGateTripReason() ?: return false
            val minEcpmToKeep = CriticalPressureBreaker.showMinEcpm()
            val qualifier = bidders
                .filter { !isBelowEcpmBar(it.getCacheAd().cachedEcpm, minEcpmToKeep) }
                .maxByOrNull { it.getCacheAd().cachedEcpm ?: 0.0 }
            if (qualifier != null) {
                qualifier.notifyWin()
                bidders.filter { it !== qualifier }.forEach(Bidder::notifyLoose)
                qualifier.getCacheAd().show(
                    rowId = rowId,
                    sceneName = sceneName,
                    loadParameter = loadParameter,
                    allowPreloadWhenFullscreenAdDismissed = qualifier.allowPreloadWhenFullscreenAdDismissed(),
                    allowPreloadWhenAdFailedToShow = qualifier.allowPreloadWhenAdFailedToShow(),
                    onUserEarnedRewardListener = onUserEarnedRewardListener,
                    fullScreenContentCallback = fullScreenContentCallback
                )
                dispatchToBackground {
                    CriticalPressureBreaker.scavengeSubThresholdCaches(reason = "show_gate_post_qualifier")
                }
            } else {
                val bestEcpm = bidders.mapNotNull { it.getCacheAd().cachedEcpm }.maxOrNull()
                bidders.forEach { it.getCacheAd().destroySafely(reason = "show_gate_suppressed") }
                val destroyedScavenged =
                    CriticalPressureBreaker.scavengeSubThresholdCaches(reason = "show_gate_suppressed")
                dispatchToBackground { CriticalPressureBreaker.maybeRunEmergencyGc() }
                val message = CriticalPressureReporter.showSuppressedMessage(
                    sceneName = sceneName,
                    reason = tripReason,
                    candidates = bidders.size,
                    bestEcpm = bestEcpm,
                    destroyedCandidates = bidders.size,
                    destroyedScavenged = destroyedScavenged,
                )
                // The callback relay reports this once through BizAdpvTracker. A show decision
                // must never be emitted as a UnitResponseTracker event.
                fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                    code = ERROR_CODE_CRITICAL_PRESSURE_SHOW_SUPPRESSED,
                    message = message,
                )
            }
            return true
        }

        fun consumeFullscreenSharedCompetitors(): List<Bidder?> {
            val ceilAd = when (fullScreenAdType) {
                AD_TYPE_OPEN -> {
                    SharedCeilAdPool.consumeCacheAd(AD_TYPE_NATIVE, AD_TYPE_INTERSTITIAL, AD_TYPE_OPEN)
                }

                AD_TYPE_INTERSTITIAL -> {
                    SharedCeilAdPool.consumeCacheAd(AD_TYPE_NATIVE, AD_TYPE_INTERSTITIAL)
                }

                AD_TYPE_REWARDED -> {
                    SharedCeilAdPool.consumeCacheAd(
                        AD_TYPE_NATIVE,
                        AD_TYPE_INTERSTITIAL,
                        AD_TYPE_REWARDED
                    )
                }

                else -> null
            }
            val ceilAdBidder: Bidder? = if (ceilAd != null) {
                object : Bidder() {
                    override fun notifyLoose() {
                        SharedCeilAdPool.addAdToCache(getCacheAd())
                    }

                    override fun getCacheAd(): CacheAd<out AdBase> {
                        return ceilAd
                    }

                    override fun allowPreloadWhenFullscreenAdDismissed(): Boolean {
                        return false
                    }

                    override fun allowPreloadWhenAdFailedToShow(): Boolean {
                        return false
                    }
                }
            } else {
                null
            }
            logInfo(TAG) {
                "platformShowFullscreenAd(sceneName=$sceneName): ceilAdBidder= ${
                    ceilAdBidder?.getCacheAd()?.let { "$it: ${it.ad.eCPM()}" }
                }"
            }

            val ceilAutomationAd = when (fullScreenAdType) {
                AD_TYPE_OPEN -> {
                    CeilAutomation.consumeCacheAd(AD_TYPE_NATIVE, AD_TYPE_INTERSTITIAL, AD_TYPE_OPEN)
                }

                AD_TYPE_INTERSTITIAL -> {
                    CeilAutomation.consumeCacheAd(AD_TYPE_NATIVE, AD_TYPE_INTERSTITIAL)
                }

                AD_TYPE_REWARDED -> {
                    CeilAutomation.consumeCacheAd(
                        AD_TYPE_NATIVE,
                        AD_TYPE_INTERSTITIAL,
                        AD_TYPE_REWARDED
                    )
                }

                else -> null
            }
            val ceilAutomationAdBidder: Bidder? = if (ceilAutomationAd != null) {
                object : Bidder() {
                    override fun notifyLoose() {
                        CeilAutomation.addAdToCache(getCacheAd())
                    }

                    override fun getCacheAd(): CacheAd<out AdBase> {
                        return ceilAutomationAd
                    }

                    override fun allowPreloadWhenFullscreenAdDismissed(): Boolean {
                        return false
                    }

                    override fun allowPreloadWhenAdFailedToShow(): Boolean {
                        return false
                    }
                }
            } else {
                null
            }
            logInfo(TAG) {
                "platformShowFullscreenAd(sceneName=$sceneName): ceilAutomationAdBidder= ${
                    ceilAutomationAdBidder?.getCacheAd()?.let { "$it: ${it.ad.eCPM()}" }
                }"
            }

            val floorAd = when (fullScreenAdType) {
                AD_TYPE_OPEN -> {
                    SharedFloorAdPool.consumeCacheAd(AD_TYPE_NATIVE, AD_TYPE_INTERSTITIAL, AD_TYPE_OPEN)
                }

                AD_TYPE_INTERSTITIAL -> {
                    SharedFloorAdPool.consumeCacheAd(AD_TYPE_NATIVE, AD_TYPE_INTERSTITIAL)
                }

                AD_TYPE_REWARDED -> {
                    SharedFloorAdPool.consumeCacheAd(
                        AD_TYPE_NATIVE,
                        AD_TYPE_INTERSTITIAL,
                        AD_TYPE_REWARDED
                    )
                }

                else -> null
            }
            val floorAdBidder: Bidder? = if (floorAd != null) {
                object : Bidder() {
                    override fun notifyLoose() {
                        SharedFloorAdPool.addAdToCache(getCacheAd())
                    }

                    override fun getCacheAd(): CacheAd<out AdBase> {
                        return floorAd
                    }

                    override fun allowPreloadWhenFullscreenAdDismissed(): Boolean {
                        return false
                    }

                    override fun allowPreloadWhenAdFailedToShow(): Boolean {
                        return false
                    }
                }
            } else {
                null
            }
            logInfo(TAG) {
                "platformShowFullscreenAd(sceneName=$sceneName): floorAdBidder= ${
                    floorAdBidder?.getCacheAd()?.let { "$it: ${it.ad.eCPM()}" }
                }"
            }

            val floorAutomationAd = when (fullScreenAdType) {
                AD_TYPE_OPEN -> {
                    FloorAutomation.consumeCacheAd(AD_TYPE_NATIVE, AD_TYPE_INTERSTITIAL, AD_TYPE_OPEN)
                }

                AD_TYPE_INTERSTITIAL -> {
                    FloorAutomation.consumeCacheAd(AD_TYPE_NATIVE, AD_TYPE_INTERSTITIAL)
                }

                AD_TYPE_REWARDED -> {
                    FloorAutomation.consumeCacheAd(
                        AD_TYPE_NATIVE,
                        AD_TYPE_INTERSTITIAL,
                        AD_TYPE_REWARDED
                    )
                }

                else -> null
            }
            val floorAutomationAdBidder: Bidder? = if (floorAutomationAd != null) {
                object : Bidder() {
                    override fun notifyLoose() {
                        FloorAutomation.addAdToCache(getCacheAd())
                    }

                    override fun getCacheAd(): CacheAd<out AdBase> {
                        return floorAutomationAd
                    }

                    override fun allowPreloadWhenFullscreenAdDismissed(): Boolean {
                        return false
                    }

                    override fun allowPreloadWhenAdFailedToShow(): Boolean {
                        return false
                    }
                }
            } else {
                null
            }
            logInfo(TAG) {
                "platformShowFullscreenAd(sceneName=$sceneName): floorAutomationAdBidder= ${
                    floorAutomationAdBidder?.getCacheAd()?.let { "$it: ${it.ad.eCPM()}" }
                }"
            }

            val backupAd = SpecificAdSceneCacheBackup.consumeCacheAd(sceneName = sceneName)

            val backupAdBidder: Bidder? = if (backupAd != null) {
                object : Bidder() {
                    override fun notifyLoose() {
                        SpecificAdSceneCacheBackup.addAdToCache(getCacheAd())
                    }

                    override fun getCacheAd(): CacheAd<out AdBase> {
                        return backupAd
                    }

                    override fun allowPreloadWhenFullscreenAdDismissed(): Boolean {
                        return false
                    }

                    override fun allowPreloadWhenAdFailedToShow(): Boolean {
                        return false
                    }
                }
            } else {
                null
            }
            logInfo(TAG) {
                "platformShowFullscreenAd(sceneName=$sceneName): backupAdBidder= ${
                    backupAdBidder?.getCacheAd()?.let { "$it: ${it.ad.eCPM()}" }
                }"
            }

            return listOf(
                ceilAutomationAdBidder,
                ceilAdBidder,
                floorAutomationAdBidder,
                floorAdBidder,
                backupAdBidder,
            )
        }

        fun createSceneMainBidder(scene: SceneCfg, strategy: StrategyCfg): Bidder? {
            val mainAd = consumeCacheAd(strategy.id)
            val mainAdBidder = mainAd?.let {
                object : Bidder() {
                    override fun notifyLoose() {
                        addCacheAd(getCacheAd())
                    }

                    override fun getCacheAd(): CacheAd<out AdBase> = it

                    override fun allowPreloadWhenFullscreenAdDismissed(): Boolean {
                        return scene.allowPreloadWhenFullscreenAdDismissed
                    }

                    override fun allowPreloadWhenAdFailedToShow(): Boolean {
                        return scene.allowPreloadWhenAdFailedToShow
                    }
                }
            }
            logInfo(TAG) {
                "platformShowFullscreenAd(sceneName=$sceneName): mainAdBidder=${mainAdBidder?.getCacheAd()?.let { "$it: ${it.cachedEcpm}" }}"
            }
            return mainAdBidder
        }

        fun consumeScopedNewUserBidders(
            excludedStrategyIds: Set<Int>,
            allowPreloadWhenFullscreenAdDismissed: Boolean,
            allowPreloadWhenAdFailedToShow: Boolean,
        ): List<Bidder> {
            return NewUserScopedBidderRegistry.eligibleStrategyIdsForFullscreen(
                fullScreenAdType = fullScreenAdType,
                excludedStrategyIds = excludedStrategyIds,
            ).mapNotNull { strategyId ->
                val scopedAd = consumeCacheAd(strategyId) ?: return@mapNotNull null
                object : Bidder() {
                    override fun notifyLoose() {
                        addCacheAd(getCacheAd())
                    }

                    override fun getCacheAd(): CacheAd<out AdBase> = scopedAd

                    override fun allowPreloadWhenFullscreenAdDismissed(): Boolean {
                        return allowPreloadWhenFullscreenAdDismissed
                    }

                    override fun allowPreloadWhenAdFailedToShow(): Boolean {
                        return allowPreloadWhenAdFailedToShow
                    }
                }
            }
        }

        fun tryShowResolvedFullscreenScene(scene: SceneCfg, strategy: StrategyCfg): Boolean {
            val sharedCompetitors = consumeFullscreenSharedCompetitors()
            val scopedNewUserCompetitors = consumeScopedNewUserBidders(
                excludedStrategyIds = setOf(strategy.id),
                allowPreloadWhenFullscreenAdDismissed = scene.allowPreloadWhenFullscreenAdDismissed,
                allowPreloadWhenAdFailedToShow = scene.allowPreloadWhenAdFailedToShow,
            )
            val mainAdBidder = createSceneMainBidder(scene, strategy)
            val selection = selectPrimaryBidderAgainstCompetitors(
                primaryBidder = mainAdBidder,
                competitorBidders = buildList {
                    addAll(sharedCompetitors)
                    addAll(scopedNewUserCompetitors)
                },
                clientBiddingEnabled = scene.enableClientBidding,
                highEcpmThreshold = readHighEcpmThreshold(),
            )
            logInfo(TAG) { "all bidders: ${selection.bidders}" }
            val winner = selection.winner
            logInfo(TAG) {
                "platformShowFullscreenAd(sceneName=$sceneName): winner=${winner?.getCacheAd()?.let { "$it: ${it.cachedEcpm}" }}"
            }
            if (interveneCriticalPressureShowGate(
                    bidders = selection.bidders,
                    rowId = rowId,
                    sceneName = sceneName,
                    loadParameter = loadParameter,
                )
            ) {
                return true
            }
            if (winner == null) return false
            winner.notifyWin()
            selection.bidders.asSequence().filter { it != winner }.forEach(Bidder::notifyLoose)
            winner.getCacheAd().show(
                rowId = rowId,
                sceneName = sceneName,
                loadParameter = loadParameter,
                allowPreloadWhenFullscreenAdDismissed = winner.allowPreloadWhenFullscreenAdDismissed(),
                allowPreloadWhenAdFailedToShow = winner.allowPreloadWhenAdFailedToShow(),
                onUserEarnedRewardListener = onUserEarnedRewardListener,
                fullScreenContentCallback = fullScreenContentCallback
            )
            return true
        }

        val isNotificationSplashAdShowScene = isNotificationSplashAdShowScene(sceneName)
        if (isNotificationSplashAdShowScene) {
            val notificationSplashAd = PoseidonConfig.Companion.get()?.notificationSplashAd
            val (passed, message) = notificationSplashAd.check()
            if (!passed) {
                fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                    code = ERROR_CODE_NOTIFICATION_SPLASH_AD_CONFIG_CHECK_FAILED,
                    message = message
                )
                return Cancelable.Companion.EMPTY
            }

            val sharedCompetitors = consumeFullscreenSharedCompetitors()
            val mainAd = NotificationSplashAdManager.consumeCacheAd()
            val mainAdBidder: Bidder? = if (mainAd != null) {
                object : Bidder() {

                    override fun notifyLoose() {
                        NotificationSplashAdManager.setCacheAd(mainAd)
                    }

                    override fun getCacheAd(): CacheAd<out AdBase> {
                        return mainAd
                    }

                    override fun allowPreloadWhenFullscreenAdDismissed(): Boolean {
                        return false
                    }

                    override fun allowPreloadWhenAdFailedToShow(): Boolean {
                        return false
                    }
                }
            } else {
                null
            }
            logInfo(TAG) {
                "platformShowFullscreenAd(sceneName=$sceneName): mainAdBidder= ${
                    mainAdBidder?.getCacheAd()?.let { "$it: ${it.ad.eCPM()}" }
                }"
            }

            val fallbackStrategyId = notificationSplashAd!!.fallbackStrategyId
            val fallbackCacheAdBidder: Bidder? = if (fallbackStrategyId != null) {
                val fallbackCacheAd = consumeCacheAd(fallbackStrategyId)
                if (fallbackCacheAd != null) {
                    object : Bidder() {
                        override fun notifyLoose() {
                            addCacheAd(getCacheAd())
                        }

                        override fun getCacheAd(): CacheAd<out AdBase> {
                            return fallbackCacheAd
                        }

                        override fun allowPreloadWhenFullscreenAdDismissed(): Boolean {
                            return notificationSplashAd.allowPreloadWhenFallbackAdDismissed
                        }

                        override fun allowPreloadWhenAdFailedToShow(): Boolean {
                            return notificationSplashAd.allowPreloadWhenFallbackAdFailedToShow
                        }
                    }
                } else {
                    null
                }
            } else {
                null
            }
            logInfo(TAG) {
                "platformShowFullscreenAd(sceneName=$sceneName): fallbackCacheAdBidder= ${
                    fallbackCacheAdBidder?.getCacheAd()?.let { "$it: ${it.ad.eCPM()}" }
                }"
            }

            val scopedNewUserCompetitors = consumeScopedNewUserBidders(
                excludedStrategyIds = setOfNotNull(fallbackStrategyId),
                allowPreloadWhenFullscreenAdDismissed = false,
                allowPreloadWhenAdFailedToShow = false,
            )
            logInfo(TAG) {
                "platformShowFullscreenAd(sceneName=$sceneName): scopedNewUserCompetitors=${
                    scopedNewUserCompetitors.map { bidder ->
                        bidder.getCacheAd().let { "$it: ${it.cachedEcpm}" }
                    }
                }"
            }

            val bidders = if (notificationSplashAd.enableClientBidding) {
                buildList {
                    addAll(sharedCompetitors.filterNotNull())
                    addAll(scopedNewUserCompetitors)
                    mainAdBidder?.let(::add)
                    fallbackCacheAdBidder?.let(::add)
                }
            } else {
                sharedCompetitors.forEach { it?.notifyLoose() }
                scopedNewUserCompetitors.forEach(Bidder::notifyLoose)
                fallbackCacheAdBidder?.notifyLoose()

                listOfNotNull(mainAdBidder)
            }
            val ecpmThreshold = readHighEcpmThreshold()
            logInfo(TAG) {
                "all bidders: ${bidders}"
            }
            val winner = selectTopBidder(bidders, ecpmThreshold)
            logInfo(TAG) {
                "platformShowFullscreenAd(sceneName=$sceneName): winner= ${
                    winner?.getCacheAd()?.let { "$it: ${it.ad.eCPM()}" }
                }"
            }
            if (interveneCriticalPressureShowGate(
                    bidders = bidders,
                    rowId = rowId,
                    sceneName = sceneName,
                    loadParameter = loadParameter,
                )
            ) {
                return Cancelable.Companion.EMPTY
            }
            if (winner != null) {
                winner.notifyWin()
                bidders.filter { it !== winner }.forEach(Bidder::notifyLoose)
                winner.getCacheAd().show(
                    rowId = rowId,
                    sceneName = sceneName,
                    loadParameter = loadParameter,
                    allowPreloadWhenFullscreenAdDismissed = winner.allowPreloadWhenFullscreenAdDismissed(),
                    allowPreloadWhenAdFailedToShow = winner.allowPreloadWhenAdFailedToShow(),
                    onUserEarnedRewardListener = onUserEarnedRewardListener,
                    fullScreenContentCallback = fullScreenContentCallback
                )
            } else {
                fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                    code = ERROR_CODE_NOTIFICATION_SPLASH_AD_NO_CACHE,
                    message = "ERROR_CODE_NOTIFICATION_SPLASH_AD_NO_CACHE: sceneName:${sceneName}"
                )
            }

            return Cancelable.Companion.EMPTY
        }

        when (val result = resolveScene(sceneName = sceneName)) {
            is ResultCallback.Failure -> {
                fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                    code = result.code,
                    message = result.message
                )
                return Cancelable.Companion.EMPTY
            }

            is ResultCallback.Success -> {
                val (_, sceneToStrategyPair) = result.value
                val scene = sceneToStrategyPair.first
                val strategy = sceneToStrategyPair.second

                if (strategy == null) {
                    fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                        code = ERROR_CODE_NULL_STRATEGY,
                        message = "There is no advertising strategy that matches the scene: sceneName:${scene.name}"
                    )
                    return Cancelable.Companion.EMPTY
                }

                if (tryShowResolvedFullscreenScene(scene = scene, strategy = strategy)) {
                    return Cancelable.Companion.EMPTY
                }
                    val showGateTripReason = CriticalPressureBreaker.showGateTripReason()
                    if (showGateTripReason != null) {
                        val destroyedScavenged =
                            CriticalPressureBreaker.scavengeSubThresholdCaches(reason = "show_gate_no_cache")
                        dispatchToBackground { CriticalPressureBreaker.maybeRunEmergencyGc() }
                        val message = CriticalPressureReporter.showSuppressedMessage(
                            sceneName = sceneName,
                            reason = showGateTripReason,
                            candidates = 0,
                            bestEcpm = null,
                            destroyedCandidates = 0,
                            destroyedScavenged = destroyedScavenged,
                        )
                        // The callback relay reports this once through BizAdpvTracker. A show
                        // decision must never be emitted as a UnitResponseTracker event.
                        fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                            code = ERROR_CODE_CRITICAL_PRESSURE_SHOW_SUPPRESSED,
                            message = message,
                        )
                        return Cancelable.Companion.EMPTY
                    }
                    if (!scene.allowNoCacheRequest || shouldBlockOrdinaryPreload(sceneName = sceneName)) {
                        fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                            code = ERROR_CODE_NO_CACHE_DISALLOWED_LOAD,
                            message = "No ads are cached and no live requests are allowed: sceneName:${scene.name}"
                        )
                        if (scene.allowNoCachePreload) {
                            platformPreload(loadParameter = loadParameter)
                        }
                        return Cancelable.Companion.EMPTY
                    }
                    val loadCallback = object : AdLoadListener {
                        override fun onAdLoadFailed(code: Int, message: String) {
                            if (code == ERROR_CODE_AD_LOAD_CANCELED) {
                                fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                                    code = ERROR_CODE_NO_CACHE_THEN_LOAD_CANCELED,
                                    message = "The ad request is canceled when there is no cached ad available: sceneName:${scene.name}"
                                )
                            } else {
                                fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                                    code = ERROR_CODE_NO_CACHE_THEN_LOAD_FAILED,
                                    message = "Failed to request ad when no cached ad is available: sceneName:${scene.name}, code=$code, message=$message"
                                )
                            }
                        }

                        override fun onAdLoadSuccess() {
                            if (!tryShowResolvedFullscreenScene(scene = scene, strategy = strategy)) {
                                fullScreenContentCallback.onAdFailedToShowFullScreenContent(
                                    code = ERROR_CODE_CALLBACK_AD_LOADED_BUT_QUERY_CACHE_AD_FAILED,
                                    message = "The ad has been successfully loaded, but no bidder is available when the cache pools are queried again.: sceneName:${scene.name}"
                                )
                            }
                        }
                    }
                    return LifecycleCancelableAdLoader(
                        showParameter = showParameter,
                        loadCallback = loadCallback
                    ).apply {
                        platformLoadAd(loadParameter = loadParameter)
                    }
            }
        }
    }

    fun loadAndShowBannerAd(
        loadAndShowParameter: PlatformBannerAdLoadAndShowParameter,
        onAdClosed: (() -> Unit)? = null
    ): Destroyable {
        logInfo(TAG) {
            "loadAndShowBannerAd: loadAndShowParameter=${loadAndShowParameter}"
        }
        return platformLoadAndShowBannerAd(
            loadAndShowParameter = loadAndShowParameter,
            loadAndShowCallback = object : InnerAdLoadAndShowCallback<BannerAdBase> {
                override fun onAdLoadFailed(code: Int, message: String) {
                    logInfo(TAG) {
                        "loadAndShowBannerAd.onAdFailedToLoad: " +
                                "loadAndShowParameter=${loadAndShowParameter}, " +
                                "code=${code}, " +
                                "message=${message}."
                    }
                    AdpvGapDetectionTracker(
                        sceneName = loadAndShowParameter.sceneName,
                        adType = AD_TYPE_BANNER,
                    ).analyze()
                    BizAdpvTracker(
                        sceneName = loadAndShowParameter.sceneName,
                        adType = AD_TYPE_BANNER,
                        resultCode = code,
                        resultMessage = message,
                    ).analyze()
                    loadAndShowParameter.adLoadCallback?.onAdLoadFailed(code = code, message = message)
                }

                override fun onAdLoadSuccess(ad: BannerAdBase) {
                    loadAndShowParameter.adLoadCallback?.onAdLoadSuccess()
                }

                override fun onAdClicked() {
                    logInfo(TAG) {
                        "loadAndShowBannerAd.onAdClicked: " +
                                "loadAndShowParameter=${loadAndShowParameter}"
                    }
                    AdFrequencyControlManager.recordAdClick(loadAndShowParameter.sceneName,
                        AD_TYPE_BANNER
                    )
                    loadAndShowParameter.adShowListener?.onAdClicked()
                }

                override fun onAdClosed() {
                    logInfo(TAG) {
                        "loadAndShowBannerAd.onAdClosed: " +
                                "loadAndShowParameter=${loadAndShowParameter}"
                    }
                    loadAndShowParameter.adShowListener?.onAdClosed()
                    onAdClosed?.invoke()
                }

                override fun onAdShowFailed(code: Int, message: String) {
                    logInfo(TAG) {
                        "loadAndShowBannerAd.onAdFailedToShow: " +
                                "loadAndShowParameter=${loadAndShowParameter}, " +
                                "code=${code}, " +
                                "message=${message}."
                    }
                    AdpvGapDetectionTracker(
                        sceneName = loadAndShowParameter.sceneName,
                        adType = AD_TYPE_BANNER,
                    ).analyze()
                    BizAdpvTracker(
                        sceneName = loadAndShowParameter.sceneName,
                        adType = AD_TYPE_BANNER,
                        resultCode = code,
                        resultMessage = message,
                    ).analyze()
                    loadAndShowParameter.adShowListener?.onAdShowFailed(code = code, message = message)
                }

                override fun onAdShowSuccess() {
                    logInfo(TAG) {
                        "loadAndShowBannerAd.onAdImpression: " +
                                "showParameter=${loadAndShowParameter}"
                    }
                    AdpvGapDetectionTracker(
                        sceneName = loadAndShowParameter.sceneName,
                        adType = AD_TYPE_BANNER,
                    ).analyze()
                    BizAdpvTracker(
                        sceneName = loadAndShowParameter.sceneName,
                        adType = AD_TYPE_BANNER,
                        resultCode = CODE_SUCCESS,
                        resultMessage = "Advertisement display success",
                       
                    ).analyze()
                    AdFrequencyControlManager.recordAdDisplay(loadAndShowParameter.sceneName,
                        AD_TYPE_BANNER
                    )
                    loadAndShowParameter.adShowListener?.onAdShowSuccess()
                }
            }
        )
    }

    private fun platformLoadAndShowBannerAd(
        loadAndShowParameter: PlatformBannerAdLoadAndShowParameter,
        loadAndShowCallback: InnerAdLoadAndShowCallback<BannerAdBase>,
        persistedFrequencyGate: PersistedBannerFrequencyGate? = null,
    ): Destroyable {
        val sceneName = loadAndShowParameter.sceneName
        if (!isGlobalSwitchOn()) {
            loadAndShowCallback.onAdShowFailed(
                code = ERROR_CODE_GLOBAL_AD_SWITCH_OFF,
                message = "ERROR_CODE_GLOBAL_AD_SWITCH_OFF:sceneName:${sceneName}, switch=${PoseidonConfig.Companion.get()?.switch}"
            )
            return Destroyable.Companion.EMPTY
        }
        if (shouldBlockOrdinaryPreload(sceneName = sceneName)) {
            logInfo(TAG) {
                "Skip ordinary banner load before first show: sceneName=$sceneName"
            }
            return Destroyable.Companion.EMPTY
        }
        if (!AdShowLimit.canShowAd()) {
            loadAndShowCallback.onAdShowFailed(
                code = ERROR_CODE_AD_MAX_IMPRESSION_COUNT_LIMIT,
                message = "ERROR_CODE_AD_MAX_IMPRESSION_COUNT_LIMIT:sceneName:${sceneName}"
            )
            return Destroyable.Companion.EMPTY
        }

        if (!isNetworkAvailable()) {
            loadAndShowCallback.onAdShowFailed(
                code = ERROR_CODE_NETWORK_UNAVAILABLE,
                message = "Network unavailable:sceneName:${sceneName}"
            )
            return Destroyable.Companion.EMPTY
        }

        when (val result = resolveScene(sceneName = sceneName)) {
            is ResultCallback.Failure -> {
                loadAndShowCallback.onAdShowFailed(
                    code = result.code,
                    message = result.message
                )
                return Destroyable.Companion.EMPTY
            }

            is ResultCallback.Success -> {
                val (poseidonConfigVersion, sceneToStrategyPair) = result.value
                val scene = sceneToStrategyPair.first
                val strategy = sceneToStrategyPair.second
                if (strategy == null) {
                    loadAndShowCallback.onAdShowFailed(
                        code = ERROR_CODE_NULL_STRATEGY,
                        message = "There is no advertising strategy that matches the scene: sceneName:${scene.name}"
                    )
                    return Destroyable.Companion.EMPTY
                }
                if (scene.adType != AD_TYPE_BANNER) {
                    loadAndShowCallback.onAdShowFailed(
                        code = ERROR_CODE_AD_TYPE_MISMATCH,
                        message = "Ad type of the scene is not banner."
                    )
                    return Destroyable.Companion.EMPTY
                }
                val adUnit = strategy.placements.firstOrNull()
                if (adUnit == null) {
                    loadAndShowCallback.onAdShowFailed(
                        code = ERROR_CODE_NULL_AD_UNIT,
                        message = "There is no ad unit that matches the scene."
                    )
                    return Destroyable.Companion.EMPTY
                }
                if (adUnit.adType != AD_TYPE_BANNER) {
                    loadAndShowCallback.onAdShowFailed(
                        code = ERROR_CODE_AD_TYPE_MISMATCH,
                        message = "Ad type of the strategy is not banner."
                    )
                    return Destroyable.Companion.EMPTY
                }

                val resolvedFrequencyGate = persistedFrequencyGate ?: run {
                    val prepareFrequencyGate: suspend () -> PersistedBannerFrequencyGate = {
                            val loadResult = AdFrequencyControlManager.checkLoadFrequency(
                                scene.name,
                                AD_TYPE_BANNER,
                            )
                            PersistedBannerFrequencyGate(
                                loadResult = loadResult,
                                showResult = if (loadResult is FrequencyCheckResult.Block) {
                                    null
                                } else {
                                    AdFrequencyControlManager.checkShowFrequency(
                                        scene.name,
                                        AD_TYPE_BANNER,
                                    )
                                },
                            )
                    }
                    val resumeBanner: (PersistedBannerFrequencyGate) -> Destroyable = { result ->
                            platformLoadAndShowBannerAd(
                                loadAndShowParameter = loadAndShowParameter,
                                loadAndShowCallback = loadAndShowCallback,
                                persistedFrequencyGate = result,
                            )
                    }
                    return if (AdFrequencyControlManager.isPersistedFrequencyConfigured()) {
                        postDestroyableAfterRoomPolicy(prepareFrequencyGate, resumeBanner)
                    } else {
                        postDestroyableAfterBackgroundPolicy(prepareFrequencyGate, resumeBanner)
                    }
                }

                val loadFrequencyResult = resolvedFrequencyGate.loadResult
                AdFrequencyControlManager.convertFrequencyResultToErrorCode(loadFrequencyResult)?.let { errorCode ->
                    loadAndShowCallback.onAdLoadFailed(
                        code = errorCode,
                        message = "Frequency control blocked banner load: ${(loadFrequencyResult as FrequencyCheckResult.Block).reason}"
                    )
                    return Destroyable.Companion.EMPTY
                }

                val showFrequencyResult = resolvedFrequencyGate.showResult ?: run {
                    logWarn(tag = TAG) {
                        "Banner show-frequency result missing after load-frequency gate passed; allow show to avoid host crash"
                    }
                    FrequencyCheckResult.Allow
                }
                AdFrequencyControlManager.convertFrequencyResultToErrorCode(showFrequencyResult)?.let { errorCode ->
                    loadAndShowCallback.onAdShowFailed(
                        code = errorCode,
                        message = "Frequency control blocked banner show: ${(showFrequencyResult as FrequencyCheckResult.Block).reason}"
                    )
                    return Destroyable.Companion.EMPTY
                }

                return when (val bannerCreateResult = createBannerAdLoader(
                    network = adUnit.mediation,
                    parameter = BannerParameter(
                        adUnitId = adUnit.id,
                        startLoadTimeInMillis = TimeHelper.currentTimeInMillis(),
                        network = adUnit.mediation,
                        sessionId = StringHelper.newSessionId(),
                        sceneName = scene.name,
                        poseidonConfigVersion = poseidonConfigVersion
                    )
                )) {
                    is ResultCallback.Failure -> {
                        loadAndShowCallback.onAdShowFailed(
                            bannerCreateResult.code,
                            bannerCreateResult.message
                        )
                        Destroyable.Companion.EMPTY
                    }

                    is ResultCallback.Success -> {
                        val bannerAdLoader = bannerCreateResult.value
                        bannerAdLoader.load(
                            loadParameter = loadAndShowParameter,
                            loadCallback = loadAndShowCallback
                        )
                        Destroyable {
                            bannerAdLoader.destroy()
                        }
                    }
                }
            }
        }
    }

    private fun allowShowAdNoNetwork(): Boolean {
        return PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor().remoteConfig.getString("poseidon_allow_show_ad_no_network")
            .ifEmpty { "1" } == "1"
    }

    @CheckResult
    private fun platformShowNativeAd(
        showParameter: PlatformNativeAdShowParameter,
        adShowListener: AdShowListener,
        persistedFrequencyResult: FrequencyCheckResult? = null,
        showAttemptEffectsApplied: Boolean = false,
    ): Cancelable {
        if (!isMainThread()) {
            return postCancelableOnMainThread {
                platformShowNativeAd(
                    showParameter = showParameter,
                    adShowListener = adShowListener,
                    persistedFrequencyResult = persistedFrequencyResult,
                    showAttemptEffectsApplied = showAttemptEffectsApplied,
                )
            }
        }

        val sceneName = showParameter.sceneName

        // The Room-to-main continuation re-enters this method with the frequency result.
        if (!showAttemptEffectsApplied) {
            tryPreloadSharedAdCallShow(sceneName = sceneName)
        }

        if (!isGlobalSwitchOn()) {
            adShowListener.onAdShowFailed(
                code = ERROR_CODE_GLOBAL_AD_SWITCH_OFF,
                message = "ERROR_CODE_GLOBAL_AD_SWITCH_OFF:sceneName:${sceneName}, switch=${PoseidonConfig.Companion.get()?.switch}"
            )
            return Cancelable.Companion.EMPTY
        }

        val resolvedFrequencyResult = persistedFrequencyResult ?: run {
            val prepareFrequency: suspend () -> FrequencyCheckResult = {
                AdFrequencyControlManager.checkShowFrequency(sceneName, AD_TYPE_NATIVE)
            }
            val resumeNativeShow: (FrequencyCheckResult) -> Cancelable = { result ->
                platformShowNativeAd(
                    showParameter = showParameter,
                    adShowListener = adShowListener,
                    persistedFrequencyResult = result,
                    showAttemptEffectsApplied = true,
                )
            }
            return if (AdFrequencyControlManager.isPersistedFrequencyConfigured()) {
                postCancelableAfterRoomPolicy(prepareFrequency, resumeNativeShow)
            } else {
                postCancelableAfterBackgroundPolicy(prepareFrequency, resumeNativeShow)
            }
        }

        // 频控检查 - 在展示原生广告时
        val frequencyResult = resolvedFrequencyResult
        AdFrequencyControlManager.convertFrequencyResultToErrorCode(frequencyResult)?.let { errorCode ->
            adShowListener.onAdShowFailed(
                code = errorCode,
                message = "Frequency control blocked native ad show: ${(frequencyResult as FrequencyCheckResult.Block).reason}"
            )
            return Cancelable.Companion.EMPTY
        }

        if (!AdShowLimit.canShowAd()) {
            adShowListener.onAdShowFailed(
                code = ERROR_CODE_AD_MAX_IMPRESSION_COUNT_LIMIT,
                message = "ERROR_CODE_AD_MAX_IMPRESSION_COUNT_LIMIT:sceneName:${sceneName}"
            )
            return Cancelable.Companion.EMPTY
        }

        if (!CtrLimit.canShowAd()) {
            adShowListener.onAdShowFailed(
                code = ERROR_CODE_CTR_LIMIT,
                message = "ERROR_CODE_CTR_LIMIT:sceneName:${sceneName}"
            )
            return Cancelable.Companion.EMPTY
        }

        if (!isNetworkAvailable() && !allowShowAdNoNetwork()) {
            adShowListener.onAdShowFailed(
                code = ERROR_CODE_NETWORK_UNAVAILABLE,
                message = "Network unavailable:sceneName:${sceneName}"
            )
            return Cancelable.Companion.EMPTY
        }

        when (val result = resolveScene(sceneName = sceneName)) {
            is ResultCallback.Failure -> {
                adShowListener.onAdShowFailed(
                    code = result.code,
                    message = result.message
                )
                return Cancelable.Companion.EMPTY
            }

            is ResultCallback.Success -> {
                val (_, sceneToStrategyPair) = result.value
                val scene = sceneToStrategyPair.first
                val strategy = sceneToStrategyPair.second
                if (strategy == null) {
                    adShowListener.onAdShowFailed(
                        code = ERROR_CODE_NULL_STRATEGY,
                        message = "There is no advertising strategy that matches the scene: sceneName:${scene.name}"
                    )
                    return Cancelable.Companion.EMPTY
                }

                fun CacheAd<*>.show(adShowListener: AdShowListener) {
                    ad.updateSceneName(sceneName = sceneName)
                    if (ad is NativeAdBase) {
                        logInfo(TAG) {
                            "NativeAdPrepareToShow(number: ${number}): " +
                                    "showParameter=${showParameter}, " +
                                    "adShowListener=${adShowListener}"
                        }

                        ad.setAdListener(object : AdShowListener {
                            override fun onAdClicked() {
                                adShowListener.onAdClicked()
                            }

                            override fun onAdClosed() {
                                adShowListener.onAdClosed()
                            }

                            override fun onAdShowFailed(code: Int, message: String) {
                                adShowListener.onAdShowFailed(code, message)
                                if (scene.allowPreloadWhenAdFailedToShow &&
                                    !CriticalPressureBreaker.shouldSuppressOptionalWork()
                                ) {
                                    platformPreload(loadParameter = showParameter.toLoadParameter())
                                }
                            }

                            override fun onAdShowSuccess() {
                                adShowListener.onAdShowSuccess()
                                if (scene.allowPreloadWhenNativeAdImpressed &&
                                    !CriticalPressureBreaker.shouldSuppressOptionalWork()
                                ) {
                                    platformPreload(loadParameter = showParameter.toLoadParameter())
                                }
                            }
                        })
                        when (val showResult = ad.render(showParameter)) {
                            is ShowResultCallback.Failure -> {
                                adShowListener.onAdShowFailed(
                                    code = showResult.code,
                                    message = showResult.message
                                )
                            }

                            ShowResultCallback.Success -> {
                                // nothing to do
                            }
                        }
                    } else {
                        adShowListener.onAdShowFailed(
                            code = ERROR_CODE_CACHE_AD_TYPE_MISMATCH,
                            message = "Cache ad type mismatch: sceneName:${scene.name}, expect:${
                                getAdTypeName(
                                    AD_TYPE_NATIVE
                                )
                            }, actual: ${getAdTypeName(ad.getAdType())}"
                        )
                    }
                }

                val ceilAd = SharedCeilAdPool.consumeCacheAd(AD_TYPE_NATIVE)
                val ceilAdBidder: Bidder? = if (ceilAd != null) {
                    object : Bidder() {
                        override fun notifyLoose() {
                            SharedCeilAdPool.addAdToCache(getCacheAd())
                        }

                        override fun getCacheAd(): CacheAd<out AdBase> {
                            return ceilAd
                        }
                    }
                } else {
                    null
                }
                logInfo(TAG) {
                    "platformShowNativeAd(sceneName=$sceneName): ceilAdBidder= ${
                        ceilAdBidder?.getCacheAd()?.let { "$it: ${it.ad.eCPM()}" }
                    }"
                }

                val ceilAutomationAd = CeilAutomation.consumeCacheAd(AD_TYPE_NATIVE)
                val ceilAutomationAdBidder: Bidder? = if (ceilAutomationAd != null) {
                    object : Bidder() {
                        override fun notifyLoose() {
                            CeilAutomation.addAdToCache(getCacheAd())
                        }

                        override fun getCacheAd(): CacheAd<out AdBase> {
                            return ceilAutomationAd
                        }
                    }
                } else {
                    null
                }
                logInfo(TAG) {
                    "platformShowNativeAd(sceneName=$sceneName): ceilAutomationAdBidder= ${
                        ceilAutomationAdBidder?.getCacheAd()?.let { "$it: ${it.ad.eCPM()}" }
                    }"
                }

                val mainAd = consumeCacheAd(strategy.id)
                val mainAdBidder: Bidder? = if (mainAd != null) {
                    object : Bidder() {
                        override fun notifyLoose() {
                            addCacheAd(getCacheAd())
                        }

                        override fun getCacheAd(): CacheAd<out AdBase> {
                            return mainAd
                        }
                    }
                } else {
                    null
                }
                logInfo(TAG) {
                    "platformShowNativeAd(sceneName=$sceneName): mainAdBidder= ${
                        mainAdBidder?.getCacheAd()?.let { "$it: ${it.ad.eCPM()}" }
                    }"
                }

                val floorAd = SharedFloorAdPool.consumeCacheAd(AD_TYPE_NATIVE)
                val floorAdBidder: Bidder? = if (floorAd != null) {
                    object : Bidder() {
                        override fun notifyLoose() {
                            SharedFloorAdPool.addAdToCache(getCacheAd())
                        }

                        override fun getCacheAd(): CacheAd<out AdBase> {
                            return floorAd
                        }
                    }
                } else {
                    null
                }
                logInfo(TAG) {
                    "platformShowNativeAd(sceneName=$sceneName): floorAdBidder= ${
                        floorAdBidder?.getCacheAd()?.let { "$it: ${it.ad.eCPM()}" }
                    }"
                }

                val floorAutomationAd = FloorAutomation.consumeCacheAd(AD_TYPE_NATIVE)
                val floorAutomationAdBidder: Bidder? = if (floorAutomationAd != null) {
                    object : Bidder() {
                        override fun notifyLoose() {
                            FloorAutomation.addAdToCache(getCacheAd())
                        }

                        override fun getCacheAd(): CacheAd<out AdBase> {
                            return floorAutomationAd
                        }
                    }
                } else {
                    null
                }
                logInfo(TAG) {
                    "platformShowNativeAd(sceneName=$sceneName): floorAdBidder= ${
                        floorAutomationAdBidder?.getCacheAd()?.let { "$it: ${it.ad.eCPM()}" }
                    }"
                }

                val bidders = if (scene.enableClientBidding) {
                    listOfNotNull(
                        ceilAutomationAdBidder,
                        ceilAdBidder,
                        floorAutomationAdBidder,
                        floorAdBidder,
                        mainAdBidder
                    )
                } else {
                    ceilAutomationAdBidder?.notifyLoose()
                    ceilAdBidder?.notifyLoose()
                    floorAutomationAdBidder?.notifyLoose()
                    floorAdBidder?.notifyLoose()

                    listOfNotNull(mainAdBidder)
                }
                val nativeEcpmThreshold = readHighEcpmThreshold()
                logInfo(TAG) {
                    "all bidders: ${bidders}"
                }
                val winner = selectTopBidder(bidders, nativeEcpmThreshold)
                logInfo(TAG) {
                    "platformShowNativeAd(sceneName=$sceneName): winner= ${
                        winner?.getCacheAd()?.let { "$it: ${it.ad.eCPM()}" }
                    }"
                }
                if (winner != null) {
                    winner.notifyWin()
                    bidders.filter { it !== winner }.forEach(Bidder::notifyLoose)
                    winner.getCacheAd().show(adShowListener)
                    return Cancelable.Companion.EMPTY
                } else {
                    if (!scene.allowNoCacheRequest || shouldBlockOrdinaryPreload(sceneName = sceneName)) {
                        adShowListener.onAdShowFailed(
                            code = ERROR_CODE_NO_CACHE_DISALLOWED_LOAD,
                            message = "No ads are cached and no live requests are allowed: sceneName:${scene.name}"
                        )
                        if (scene.allowNoCachePreload &&
                            !CriticalPressureBreaker.shouldSuppressOptionalWork()
                        ) {
                            platformPreload(loadParameter = showParameter.toLoadParameter())
                        }
                        return Cancelable.Companion.EMPTY
                    }
                    val loadCallback = object : AdLoadListener {
                        override fun onAdLoadFailed(code: Int, message: String) {
                            if (code == ERROR_CODE_AD_LOAD_CANCELED) {
                                adShowListener.onAdShowFailed(
                                    code = ERROR_CODE_NO_CACHE_THEN_LOAD_CANCELED,
                                    message = "The ad request was canceled when there is no cached ad available.: sceneName:${scene.name}"
                                )
                            } else {
                                adShowListener.onAdShowFailed(
                                    code = ERROR_CODE_NO_CACHE_THEN_LOAD_FAILED,
                                    message = "Failed to request ad when no cached ad is available: sceneName:${scene.name}, code=$code, message=$message"
                                )
                            }
                        }

                        override fun onAdLoadSuccess() {
                            val consumeCacheAd = consumeCacheAd(strategy.id)
                            if (consumeCacheAd != null) {
                                consumeCacheAd.show(adShowListener)
                            } else {
                                adShowListener.onAdShowFailed(
                                    code = ERROR_CODE_CALLBACK_AD_LOADED_BUT_QUERY_CACHE_AD_FAILED,
                                    message = "The ad has been successfully loaded, but the ad is not found when it is queried from the cache.: sceneName:${scene.name}"
                                )
                            }
                        }
                    }
                    return LifecycleCancelableAdLoader(
                        showParameter = showParameter,
                        loadCallback = loadCallback
                    ).apply {
                        platformLoadAd(loadParameter = showParameter.toLoadParameter())
                    }
                }
            }
        }
    }

    internal fun resolveScene(sceneName: String): ResultCallback<Pair<VersionHelper?, Pair<SceneCfg, StrategyCfg?>>> {
        val configuration = PoseidonConfig.Companion.get() ?: return ResultCallback.Failure(
            code = ERROR_CODE_NULL_CONFIGURATION,
            message = "EMPTY_CONFIGURATION:sceneName:${sceneName}"
        )
        val scenes = configuration.scenes
        if (scenes.isNullOrEmpty()) {
            return ResultCallback.Failure(
                code = ERROR_CODE_NULL_SCENES,
                message = "EMPTY_SCENES:sceneName:${sceneName}"
            )
        }
        val scene = scenes.firstOrNull { it.name == sceneName }
        if (scene == null) {
            return ResultCallback.Failure(
                code = ERROR_CODE_SCENE_NOT_CONFIGURED,
                message = "SCENE_NOT_CONFIGURED: sceneName:${sceneName}"
            )
        }
        return if (scene.isSwitchOn()) {
            val strategy =
                configuration.strategies?.firstOrNull { it.id == scene.strategyId }
            ResultCallback.Success(value = configuration.version to (scene to strategy))
        } else {
            ResultCallback.Failure(
                code = ERROR_CODE_SCENE_AD_SWITCH_OFF,
                message = "SCENE_AD_SWITCH_OFF:sceneName:${sceneName}"
            )
        }
    }
}

class PlatformOpenAdLoadParameter private constructor(
    val context: Context,
    val sceneName: String,
    val adLoadCallback: AdLoadListener?,
    @Suppress("unused") private val originalContextName: String
) {
    @JvmOverloads
    constructor(
        context: Context,
        sceneName: String,
        loadCallback: AdLoadListener? = null
    ) : this(
        context = context.applicationContext,
        sceneName = sceneName,
        adLoadCallback = loadCallback,
        originalContextName = context::class.java.name
    )
}

/**
 * 开屏广告展示参数，封装 Activity、场景与全屏监听器。
 *
 * @property openShowListener 开屏广告展示的监听器，可选。
 */
class PlatformOpenAdShowParameter @JvmOverloads constructor(
    val activity: ComponentActivity,
    val sceneName: String,
    val openShowListener: OpenShowListener? = null,
)

fun PlatformOpenAdShowParameter.toLoadParameter(): PlatformOpenAdLoadParameter {
    return PlatformOpenAdLoadParameter(
        context = activity.applicationContext,
        sceneName = sceneName,
        loadCallback = null
    )
}

class PlatformInterstitialAdLoadParameter private constructor(
    val context: Context,
    val sceneName: String,
    val adLoadCallback: AdLoadListener?,
    @Suppress("unused") private val originalContextName: String
) {
    @JvmOverloads
    constructor(
        context: Context,
        sceneName: String,
        loadCallback: AdLoadListener? = null
    ) : this(
        context = context.applicationContext,
        sceneName = sceneName,
        adLoadCallback = loadCallback,
        originalContextName = context::class.java.name
    )
}

/**
 * 插屏广告展示参数，存放宿主 Activity、广告场景以及插屏监听器。
 *
 * @property interstitialShowListener 插屏广告的全屏监听器，可选。
 */
class PlatformInterstitialAdShowParameter @JvmOverloads constructor(
    val activity: ComponentActivity,
    val sceneName: String,
    val interstitialShowListener: InterstitialShowListener? = null,
)

fun PlatformInterstitialAdShowParameter.toLoadParameter(): PlatformInterstitialAdLoadParameter {
    return PlatformInterstitialAdLoadParameter(
        context = activity.applicationContext,
        sceneName = sceneName,
        loadCallback = null
    )
}

class PlatformRewardedAdLoadParameter private constructor(
    val context: Context,
    val sceneName: String,
    val adLoadCallback: AdLoadListener?,
    @Suppress("unused") private val originalContextName: String
) {
    @JvmOverloads
    constructor(
        context: Context,
        sceneName: String,
        loadCallback: AdLoadListener? = null
    ) : this(
        context = context.applicationContext,
        sceneName = sceneName,
        adLoadCallback = loadCallback,
        originalContextName = context::class.java.name
    )
}

/**
 * 激励广告展示参数，持有宿主上下文、奖励监听与全屏监听器。
 *
 * @property onUserEarnedRewardListener 激励奖励回调，可选。
 * @property rewardedShowListener 激励广告的全屏回调监听器，可选。
 */
class PlatformRewardedAdShowParameter @JvmOverloads constructor(
    val activity: ComponentActivity,
    val sceneName: String,
    val onUserEarnedRewardListener: OnUserEarnedRewardListener? = null,
    val rewardedShowListener: RewardedShowListener? = null,
)

fun PlatformRewardedAdShowParameter.toLoadParameter(): PlatformRewardedAdLoadParameter {
    return PlatformRewardedAdLoadParameter(
        context = activity.applicationContext,
        sceneName = sceneName,
        loadCallback = null
    )
}

class PlatformNativeAdLoadParameter private constructor(
    val context: Context,
    val sceneName: String,
    val adLoadCallback: AdLoadListener?,
    @Suppress("unused") private val originalContextName: String
) {
    @JvmOverloads
    constructor(
        context: Context,
        sceneName: String,
        loadCallback: AdLoadListener? = null
    ) : this(
        context = context.applicationContext,
        sceneName = sceneName,
        adLoadCallback = loadCallback,
        originalContextName = context::class.java.name
    )
}

/**
 * 原生广告展示参数，包含宿主 Activity、模版视图与展示监听器。
 *
 * @property adShowListener 原生广告展示监听器，可选。
 */
class PlatformNativeAdShowParameter @JvmOverloads constructor(
    val activity: ComponentActivity,
    val sceneName: String,
    val templateView: NativeTemplateView,
    val adShowListener: AdShowListener? = null,
)

data class PlatformBannerAdLoadAndShowParameter @JvmOverloads constructor(
    val sceneName: String,
    val context: Context,
    val adViewContainer: FrameLayout,
    val onAdClosed: (() -> Unit)? = null,
    val adLoadCallback: AdLoadListener? = null,
    val adShowListener: AdShowListener? = null,
)

data class PlatformHasCacheParameter(
    val sceneName: String,
    val checkingSharedFloorAdPool: Boolean,
    val activity: Activity
)

fun PoseidonAPIProcessor.loadAndShowBannerAdWithLifecycle(
    lifecycle: Lifecycle,
    loadAndShowParameter: PlatformBannerAdLoadAndShowParameter,
    onAdClosed: (() -> Unit)? = null
) {
    val destroyable = loadAndShowBannerAd(
        loadAndShowParameter = loadAndShowParameter,
        onAdClosed = onAdClosed
    )
    if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
        destroyable.destroy()
    } else {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                lifecycle.removeObserver(this)
                logInfo(TAG) {
                    "loadAndShowBannerAdWithLifecycle: onDestroy, " +
                            "loadAndShowParameter=${loadAndShowParameter}"
                }
                destroyable.destroy()
            }
        })
    }
}

fun PlatformNativeAdShowParameter.toLoadParameter(): PlatformNativeAdLoadParameter {
    return PlatformNativeAdLoadParameter(context = activity.applicationContext, sceneName = sceneName)
}

fun platformShowFullscreenAdInterceptor(): Array<PlatformShowFullscreenAdInterceptor> {
    return arrayOf(
        object : PlatformShowFullscreenAdInterceptor {
            override fun <PlatformAdShowParameter> intercept(
                showParameter: PlatformAdShowParameter,
                fullScreenAdType: Int,
                onUserEarnedRewardListener: OnUserEarnedRewardListener?,
                fullScreenContentCallback: FullScreenContentCallback
            ): Boolean {
                val sceneName = whenPlatformAdShowParameter(
                    showParameter = showParameter,
                    interstitialAdShowParameterConverter = PlatformInterstitialAdShowParameter::sceneName,
                    rewardedAdShowParameterConverter = PlatformRewardedAdShowParameter::sceneName,
                    openAdShowParameterConverter = PlatformOpenAdShowParameter::sceneName,
                    nativeAdShowParameterConverter = PlatformNativeAdShowParameter::sceneName
                )
                val activity = whenPlatformAdShowParameter(
                    showParameter = showParameter,
                    interstitialAdShowParameterConverter = PlatformInterstitialAdShowParameter::activity,
                    rewardedAdShowParameterConverter = PlatformRewardedAdShowParameter::activity,
                    openAdShowParameterConverter = PlatformOpenAdShowParameter::activity,
                    nativeAdShowParameterConverter = PlatformNativeAdShowParameter::activity
                )
                // 暂时注释掉，用于ADPV利用率提升的尝试，改为写死return false。
                return false
//                return if (activity.isFinishing || activity.isDestroyed) {
//                    fullScreenContentCallback.onAdFailedToShowFullScreenContent(
//                        code = ERROR_CODE_ACTIVITY_DESTROYED,
//                        message = "Activity${activity} has been destroyed or is about to be destroyed:sceneName:${sceneName}"
//                    )
//                    true
//                } else {
//                    false
//                }
            }
        },
        object : PlatformShowFullscreenAdInterceptor {
            override fun <PlatformAdShowParameter> intercept(
                showParameter: PlatformAdShowParameter,
                fullScreenAdType: Int,
                onUserEarnedRewardListener: OnUserEarnedRewardListener?,
                fullScreenContentCallback: FullScreenContentCallback
            ): Boolean {
                val sceneName = whenPlatformAdShowParameter(
                    showParameter = showParameter,
                    interstitialAdShowParameterConverter = PlatformInterstitialAdShowParameter::sceneName,
                    rewardedAdShowParameterConverter = PlatformRewardedAdShowParameter::sceneName,
                    openAdShowParameterConverter = PlatformOpenAdShowParameter::sceneName,
                    nativeAdShowParameterConverter = PlatformNativeAdShowParameter::sceneName
                )
                // 暂时注释掉，用于ADPV利用率提升的尝试，改为写死return false。
                return false
//                return if (PoseidonSetupProcessor.Companion.isAppInForeground) {
//                    false
//                } else {
//                    fullScreenContentCallback.onAdFailedToShowFullScreenContent(
//                        code = ERROR_CODE_APP_IN_BACKGROUND,
//                        message = "APP_IN_BACKGROUND:sceneName:${sceneName}"
//                    )
//                    true
//                }
            }
        }
    )
}

fun PlatformRewardedAdShowParameter.toInterstitialAdShowParameter(): PlatformInterstitialAdShowParameter {
    return PlatformInterstitialAdShowParameter(
        activity = activity,
        sceneName = sceneName,
        interstitialShowListener = rewardedShowListener.asInterstitialShowListener(sceneName)
    )
}

fun PlatformRewardedAdShowParameter.toOpenAdShowParameter(): PlatformOpenAdShowParameter {
    return PlatformOpenAdShowParameter(
        activity = activity,
        sceneName = sceneName,
        openShowListener = rewardedShowListener.asOpenShowListener(sceneName)
    )
}

fun PlatformInterstitialAdShowParameter.toRewardedAdShowParameter(): PlatformRewardedAdShowParameter {
    return PlatformRewardedAdShowParameter(
        activity = activity,
        sceneName = sceneName,
        rewardedShowListener = interstitialShowListener.asRewardedShowListener(sceneName)
    )
}

fun PlatformInterstitialAdShowParameter.toOpenAdShowParameter(): PlatformOpenAdShowParameter {
    return PlatformOpenAdShowParameter(
        activity = activity,
        sceneName = sceneName,
        openShowListener = interstitialShowListener.asOpenShowListener(sceneName)
    )
}

fun PlatformOpenAdShowParameter.toInterstitialAdShowParameter(): PlatformInterstitialAdShowParameter {
    return PlatformInterstitialAdShowParameter(
        activity = activity,
        sceneName = sceneName,
        interstitialShowListener = openShowListener.asInterstitialShowListener(sceneName)
    )
}

fun PlatformOpenAdShowParameter.toRewardedAdShowParameter(): PlatformRewardedAdShowParameter {
    return PlatformRewardedAdShowParameter(
        activity = activity,
        sceneName = sceneName,
        rewardedShowListener = openShowListener.asRewardedShowListener(sceneName)
    )
}

private fun PlatformOpenAdShowParameter.withActivity(activity: ComponentActivity): PlatformOpenAdShowParameter {
    if (this.activity === activity) return this
    return PlatformOpenAdShowParameter(
        activity = activity,
        sceneName = sceneName,
        openShowListener = openShowListener
    )
}

private fun PlatformInterstitialAdShowParameter.withActivity(activity: ComponentActivity): PlatformInterstitialAdShowParameter {
    if (this.activity === activity) return this
    return PlatformInterstitialAdShowParameter(
        activity = activity,
        sceneName = sceneName,
        interstitialShowListener = interstitialShowListener
    )
}

private fun PlatformRewardedAdShowParameter.withActivity(activity: ComponentActivity): PlatformRewardedAdShowParameter {
    if (this.activity === activity) return this
    return PlatformRewardedAdShowParameter(
        activity = activity,
        sceneName = sceneName,
        onUserEarnedRewardListener = onUserEarnedRewardListener,
        rewardedShowListener = rewardedShowListener
    )
}

private fun <PlatformAdShowParameter> resolveUsableFullscreenShowActivity(
    showParameter: PlatformAdShowParameter
): ComponentActivity? {
    val requestedActivity = whenPlatformAdShowParameter(
        showParameter = showParameter,
        interstitialAdShowParameterConverter = PlatformInterstitialAdShowParameter::activity,
        rewardedAdShowParameterConverter = PlatformRewardedAdShowParameter::activity,
        openAdShowParameterConverter = PlatformOpenAdShowParameter::activity,
        nativeAdShowParameterConverter = PlatformNativeAdShowParameter::activity
    )
    if (requestedActivity.isUsableForFullscreenShow()) {
        return requestedActivity
    }

    val topActivity = ActivityLifecycleCallbacks.topActivity() as? ComponentActivity
    return topActivity?.takeIf { it.isUsableForFullscreenShow() }
}

private fun ComponentActivity.isUsableForFullscreenShow(): Boolean {
    // A back/up click can mark the Activity as finishing before the fullscreen ad is shown,
    // while its lifecycle and window are still started and usable. Rejecting isFinishing here
    // prevents the common "show an interstitial before leaving" flow.
    return !isDestroyed &&
        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}

/**
 * 将 RewardedShowListener 适配为 InterstitialShowListener，确保混类型调用仍能收到插屏回调。
 */
private fun RewardedShowListener?.asInterstitialShowListener(sceneName: String): InterstitialShowListener? {
    val delegate = this ?: return null
    return object : InterstitialShowListener {
        override fun onInterstitialExposure(sceneName: String) {
            logInfo(PoseidonAPIProcessor.TAG) {
                "Rewarded->Interstitial bridge: RewardedShowListener has no interstitial exposure callback. scene=$sceneName"
            }
        }

        override fun onAdClicked() {
            delegate.onAdClicked()
        }

        override fun onAdDismissedFullScreenContent() {
            delegate.onAdDismissedFullScreenContent()
        }

        override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
            delegate.onAdFailedToShowFullScreenContent(code, message)
        }

        override fun onAdShowedFullScreenContent() {
            delegate.onAdShowedFullScreenContent()
        }
    }
}

/**
 * 将 RewardedShowListener 适配为 OpenShowListener，复用开屏监听逻辑。
 */
private fun RewardedShowListener?.asOpenShowListener(sceneName: String): OpenShowListener? {
    val delegate = this ?: return null
    return object : OpenShowListener {
        override fun onSplashLaunch() {
            logInfo(PoseidonAPIProcessor.TAG) {
                "Rewarded->Open bridge: RewardedShowListener has no splash launch callback. scene=$sceneName"
            }
        }

        override fun onAdClicked() {
            delegate.onAdClicked()
        }

        override fun onAdDismissedFullScreenContent() {
            delegate.onAdDismissedFullScreenContent()
        }

        override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
            delegate.onAdFailedToShowFullScreenContent(code, message)
        }

        override fun onAdShowedFullScreenContent() {
            delegate.onAdShowedFullScreenContent()
        }
    }
}

/**
 * 将 InterstitialShowListener 适配为 RewardedShowListener，支撑插屏兜底激励的回调复用。
 */
private fun InterstitialShowListener?.asRewardedShowListener(sceneName: String): RewardedShowListener? {
    val delegate = this ?: return null
    return object : RewardedShowListener {
        override fun onRewardEvaluation() {
            logInfo(PoseidonAPIProcessor.TAG) {
                "Interstitial->Rewarded bridge: InterstitialShowListener has no reward evaluation callback. scene=$sceneName"
            }
        }

        override fun onAdClicked() {
            delegate.onAdClicked()
        }

        override fun onAdDismissedFullScreenContent() {
            delegate.onAdDismissedFullScreenContent()
        }

        override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
            delegate.onAdFailedToShowFullScreenContent(code, message)
        }

        override fun onAdShowedFullScreenContent() {
            delegate.onAdShowedFullScreenContent()
        }
    }
}

/**
 * 将 InterstitialShowListener 适配为 OpenShowListener，支持插屏回调复用到开屏。
 */
private fun InterstitialShowListener?.asOpenShowListener(sceneName: String): OpenShowListener? {
    val delegate = this ?: return null
    return object : OpenShowListener {
        override fun onSplashLaunch() {
            logInfo(PoseidonAPIProcessor.TAG) {
                "Interstitial->Open bridge: InterstitialShowListener has no splash launch callback. scene=$sceneName"
            }
        }

        override fun onAdClicked() {
            delegate.onAdClicked()
        }

        override fun onAdDismissedFullScreenContent() {
            delegate.onAdDismissedFullScreenContent()
        }

        override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
            delegate.onAdFailedToShowFullScreenContent(code, message)
        }

        override fun onAdShowedFullScreenContent() {
            delegate.onAdShowedFullScreenContent()
        }
    }
}

/**
 * 将 OpenShowListener 适配为 InterstitialShowListener，支撑开屏兜底插屏的混类型场景。
 */
private fun OpenShowListener?.asInterstitialShowListener(sceneName: String): InterstitialShowListener? {
    val delegate = this ?: return null
    return object : InterstitialShowListener {
        override fun onInterstitialExposure(sceneName: String) {
            logInfo(PoseidonAPIProcessor.TAG) {
                "Open->Interstitial bridge: OpenShowListener has no interstitial exposure callback. scene=$sceneName"
            }
        }

        override fun onAdClicked() {
            delegate.onAdClicked()
        }

        override fun onAdDismissedFullScreenContent() {
            delegate.onAdDismissedFullScreenContent()
        }

        override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
            delegate.onAdFailedToShowFullScreenContent(code, message)
        }

        override fun onAdShowedFullScreenContent() {
            delegate.onAdShowedFullScreenContent()
        }
    }
}

/**
 * 将 OpenShowListener 适配为 RewardedShowListener，复用开屏的全屏监听。
 */
private fun OpenShowListener?.asRewardedShowListener(sceneName: String): RewardedShowListener? {
    val delegate = this ?: return null
    return object : RewardedShowListener {
        override fun onRewardEvaluation() {
            logInfo(PoseidonAPIProcessor.TAG) {
                "Open->Rewarded bridge: OpenShowListener has no reward evaluation callback. scene=$sceneName"
            }
        }

        override fun onAdClicked() {
            delegate.onAdClicked()
        }

        override fun onAdDismissedFullScreenContent() {
            delegate.onAdDismissedFullScreenContent()
        }

        override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
            delegate.onAdFailedToShowFullScreenContent(code, message)
        }

        override fun onAdShowedFullScreenContent() {
            delegate.onAdShowedFullScreenContent()
        }
    }
}

fun PlatformInterstitialAdLoadParameter.toOpenAdLoadParameter(): PlatformOpenAdLoadParameter {
    return PlatformOpenAdLoadParameter(
        context = context,
        sceneName = sceneName,
        loadCallback = adLoadCallback
    )
}

fun PlatformRewardedAdLoadParameter.toOpenAdLoadParameter(): PlatformOpenAdLoadParameter {
    return PlatformOpenAdLoadParameter(
        context = context,
        sceneName = sceneName,
        loadCallback = adLoadCallback
    )
}

fun PlatformNativeAdLoadParameter.toOpenAdLoadParameter(): PlatformOpenAdLoadParameter {
    return PlatformOpenAdLoadParameter(
        context = context,
        sceneName = sceneName,
        loadCallback = adLoadCallback
    )
}

fun PlatformOpenAdLoadParameter.toInterstitialAdLoadParameter(): PlatformInterstitialAdLoadParameter {
    return PlatformInterstitialAdLoadParameter(
        context = context,
        sceneName = sceneName,
        loadCallback = adLoadCallback
    )
}

fun PlatformRewardedAdLoadParameter.toInterstitialAdLoadParameter(): PlatformInterstitialAdLoadParameter {
    return PlatformInterstitialAdLoadParameter(
        context = context,
        sceneName = sceneName,
        loadCallback = adLoadCallback
    )
}

fun PlatformNativeAdLoadParameter.toInterstitialAdLoadParameter(): PlatformInterstitialAdLoadParameter {
    return PlatformInterstitialAdLoadParameter(
        context = context,
        sceneName = sceneName,
        loadCallback = adLoadCallback
    )
}

fun PlatformOpenAdLoadParameter.toRewardedAdLoadParameter(): PlatformRewardedAdLoadParameter {
    return PlatformRewardedAdLoadParameter(
        context = context,
        sceneName = sceneName,
        loadCallback = adLoadCallback
    )
}

fun PlatformInterstitialAdLoadParameter.toRewardedAdLoadParameter(): PlatformRewardedAdLoadParameter {
    return PlatformRewardedAdLoadParameter(
        context = context,
        sceneName = sceneName,
        loadCallback = adLoadCallback
    )
}

fun PlatformNativeAdLoadParameter.toRewardedAdLoadParameter(): PlatformRewardedAdLoadParameter {
    return PlatformRewardedAdLoadParameter(
        context = context,
        sceneName = sceneName,
        loadCallback = adLoadCallback
    )
}

fun PlatformOpenAdLoadParameter.toNativeAdLoadParameter(): PlatformNativeAdLoadParameter {
    return PlatformNativeAdLoadParameter(
        context = context,
        sceneName = sceneName,
        loadCallback = adLoadCallback
    )
}

fun PlatformInterstitialAdLoadParameter.toNativeAdLoadParameter(): PlatformNativeAdLoadParameter {
    return PlatformNativeAdLoadParameter(
        context = context,
        sceneName = sceneName,
        loadCallback = adLoadCallback
    )
}

fun PlatformRewardedAdLoadParameter.toNativeAdLoadParameter(): PlatformNativeAdLoadParameter {
    return PlatformNativeAdLoadParameter(
        context = context,
        sceneName = sceneName,
        loadCallback = adLoadCallback
    )
}

interface PlatformShowFullscreenAdInterceptor {
    fun <PlatformAdShowParameter> intercept(
        showParameter: PlatformAdShowParameter,
        @FullscreenAdTypeDef fullScreenAdType: Int,
        onUserEarnedRewardListener: OnUserEarnedRewardListener?,
        fullScreenContentCallback: FullScreenContentCallback
    ): Boolean
}

fun <PlatformAdShowParameter, R> whenPlatformAdShowParameter(
    showParameter: PlatformAdShowParameter,
    interstitialAdShowParameterConverter: PlatformInterstitialAdShowParameter.() -> R,
    rewardedAdShowParameterConverter: PlatformRewardedAdShowParameter.() -> R,
    openAdShowParameterConverter: PlatformOpenAdShowParameter.() -> R,
    nativeAdShowParameterConverter: PlatformNativeAdShowParameter.() -> R,
): R {
    return when (showParameter) {
        is PlatformInterstitialAdShowParameter -> interstitialAdShowParameterConverter(showParameter)
        is PlatformRewardedAdShowParameter -> rewardedAdShowParameterConverter(showParameter)
        is PlatformOpenAdShowParameter -> openAdShowParameterConverter(showParameter)
        is PlatformNativeAdShowParameter -> nativeAdShowParameterConverter(showParameter)
        else -> error("Unsupported show parameter type: $showParameter")
    }
}

fun <PlatformAdLoadParameter, R> whenPlatformAdLoadParameter(
    loadParameter: PlatformAdLoadParameter,
    interstitialAdLoadParameterConverter: PlatformInterstitialAdLoadParameter.() -> R,
    rewardedAdLoadParameterConverter: PlatformRewardedAdLoadParameter.() -> R,
    nativeAdLoadParameterConverter: PlatformNativeAdLoadParameter.() -> R,
    openAdLoadParameterConverter: PlatformOpenAdLoadParameter.() -> R,
): R {
    return when (loadParameter) {
        is PlatformInterstitialAdLoadParameter -> interstitialAdLoadParameterConverter(loadParameter)
        is PlatformRewardedAdLoadParameter -> rewardedAdLoadParameterConverter(loadParameter)
        is PlatformNativeAdLoadParameter -> nativeAdLoadParameterConverter(loadParameter)
        is PlatformOpenAdLoadParameter -> openAdLoadParameterConverter(loadParameter)
        else -> error("Unsupported load parameter type: $loadParameter")
    }
}

internal fun isGlobalSwitchOn(): Boolean {
    return PoseidonConfig.Companion.get()?.isSwitchOn() ?: false
}

@Suppress("SpellCheckingInspection")
fun resolveEncryptedPoseidonConfigStringFromBackend(json: String): String? {
    return CoreRunner.configHub.resolveEncryptedConfig(json)
}

internal fun createPlatformInterstitialAdLoadParameter(sceneName: String): PlatformInterstitialAdLoadParameter {
    return PlatformInterstitialAdLoadParameter(sceneName = sceneName, context = PoseidonSetupProcessor.Companion.getInstance().application)
}

/**
 * Holds an app-facing callback only while its host lifecycle is alive.
 *
 * Mediation listeners keep receiving impression, revenue, and terminal events after this relay is
 * detached. Detaching only prevents those events from reaching a callback that may retain a
 * destroyed host Activity.
 */
internal class LifecycleCallbackRelay<T : Any>(
    lifecycle: Lifecycle,
    callback: T?
) : DefaultLifecycleObserver, Cancelable {

    private val callbackReference = DetachableCallbackReference(callback)
    private val lifecycleAtomic = AtomicReference<Lifecycle?>()

    init {
        if (callback != null) {
            runOnMainThread {
                if (callbackReference.get() == null) {
                    return@runOnMainThread
                }
                if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
                    callbackReference.detach()
                    return@runOnMainThread
                }
                if (lifecycleAtomic.compareAndSet(null, lifecycle)) {
                    lifecycle.addObserver(this)
                    if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
                        cancel()
                    }
                }
            }
        }
    }

    fun get(): T? = callbackReference.get()

    /** Returns the callback once and detaches it from the host lifecycle before delivery. */
    fun take(): T? {
        val callback = callbackReference.take()
        detachLifecycleObserver()
        return callback
    }

    override fun onDestroy(owner: LifecycleOwner) {
        cancel()
    }

    override fun cancel() {
        callbackReference.detach()
        detachLifecycleObserver()
    }

    private fun detachLifecycleObserver() {
        lifecycleAtomic.getAndSet(null)?.let { lifecycle ->
            runOnMainThread {
                lifecycle.removeObserver(this)
            }
        }
    }
}

private class DetachableCallbackReference<T : Any>(callback: T?) {
    private val callbackAtomic = AtomicReference(callback)

    fun get(): T? = callbackAtomic.get()

    fun detach() {
        callbackAtomic.set(null)
    }

    fun take(): T? = callbackAtomic.getAndSet(null)
}

private fun Cancelable.withDetachedHostCallbacksOnCancel(
    vararg callbackRelays: Cancelable
): Cancelable {
    return Cancelable {
        callbackRelays.forEach(Cancelable::cancel)
        this.cancel()
    }
}

@Suppress("FunctionName")
internal fun <PlatformAdShowParameter> LifecycleCancelableAdLoader(
    showParameter: PlatformAdShowParameter,
    loadCallback: AdLoadListener
): CancelableAdLoader {
    val cancelableAdLoader = CancelableAdLoader(loadCallback)
    val activity = whenPlatformAdShowParameter(
        showParameter = showParameter,
        interstitialAdShowParameterConverter = PlatformInterstitialAdShowParameter::activity,
        rewardedAdShowParameterConverter = PlatformRewardedAdShowParameter::activity,
        openAdShowParameterConverter = PlatformOpenAdShowParameter::activity,
        nativeAdShowParameterConverter = PlatformNativeAdShowParameter::activity
    )
    activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            owner.lifecycle.removeObserver(this)
            cancelableAdLoader.cancel()
        }
    })
    return cancelableAdLoader
}

private data class PersistedFullscreenGateResult(
    val frequencyResult: FrequencyCheckResult,
    val showLimitResult: Pair<Long?, Boolean>?,
)

private data class PersistedBannerFrequencyGate(
    val loadResult: FrequencyCheckResult,
    val showResult: FrequencyCheckResult?,
)

private fun <T> postCancelableAfterRoomPolicy(
    prepare: suspend () -> T,
    action: (T) -> Cancelable,
): Cancelable = postCancelableAfterPolicy(
    dispatchPolicy = RoomPolicyDispatcher::dispatch,
    prepare = prepare,
    action = action,
)

private fun <T> postCancelableAfterBackgroundPolicy(
    prepare: suspend () -> T,
    action: (T) -> Cancelable,
): Cancelable = postCancelableAfterPolicy(
    dispatchPolicy = ::dispatchToBackground,
    prepare = prepare,
    action = action,
)

private fun <T> postCancelableAfterPolicy(
    dispatchPolicy: (suspend () -> Unit) -> Job,
    prepare: suspend () -> T,
    action: (T) -> Cancelable,
): Cancelable {
    val delegateRef = AtomicReference<Cancelable?>()
    val mainJobRef = AtomicReference<Job?>()
    val canceled = atomic(false)
    val policyJob = dispatchPolicy policyTask@{
        if (canceled.value) {
            return@policyTask
        }
        val prepared = prepare()
        if (canceled.value) {
            return@policyTask
        }
        val mainJob = postOnMainThread {
            if (canceled.value) {
                return@postOnMainThread
            }
            delegateRef.set(action(prepared))
            if (canceled.value) {
                delegateRef.getAndSet(null)?.cancel()
            }
        }
        mainJobRef.set(mainJob)
        if (canceled.value) {
            mainJobRef.getAndSet(null)?.cancel()
        }
    }
    return Cancelable {
        if (canceled.compareAndSet(expect = false, update = true)) {
            policyJob.cancel()
            mainJobRef.getAndSet(null)?.cancel()
            delegateRef.getAndSet(null)?.cancel()
        }
    }
}

private fun <T> postDestroyableAfterRoomPolicy(
    prepare: suspend () -> T,
    action: (T) -> Destroyable,
): Destroyable = postDestroyableAfterPolicy(
    dispatchPolicy = RoomPolicyDispatcher::dispatch,
    prepare = prepare,
    action = action,
)

private fun <T> postDestroyableAfterBackgroundPolicy(
    prepare: suspend () -> T,
    action: (T) -> Destroyable,
): Destroyable = postDestroyableAfterPolicy(
    dispatchPolicy = ::dispatchToBackground,
    prepare = prepare,
    action = action,
)

private fun <T> postDestroyableAfterPolicy(
    dispatchPolicy: (suspend () -> Unit) -> Job,
    prepare: suspend () -> T,
    action: (T) -> Destroyable,
): Destroyable {
    val delegateRef = AtomicReference<Destroyable?>()
    val mainJobRef = AtomicReference<Job?>()
    val destroyed = atomic(false)
    val policyJob = dispatchPolicy policyTask@{
        if (destroyed.value) {
            return@policyTask
        }
        val prepared = prepare()
        if (destroyed.value) {
            return@policyTask
        }
        val mainJob = postOnMainThread {
            if (destroyed.value) {
                return@postOnMainThread
            }
            delegateRef.set(action(prepared))
            if (destroyed.value) {
                delegateRef.getAndSet(null)?.destroy()
            }
        }
        mainJobRef.set(mainJob)
        if (destroyed.value) {
            mainJobRef.getAndSet(null)?.cancel()
        }
    }
    return Destroyable {
        if (destroyed.compareAndSet(expect = false, update = true)) {
            policyJob.cancel()
            mainJobRef.getAndSet(null)?.cancel()
            delegateRef.getAndSet(null)?.destroy()
        }
    }
}

private fun postCancelableOnMainThread(action: () -> Cancelable): Cancelable {
    val delegateRef = AtomicReference<Cancelable?>()
    val canceled = atomic(false)
    val job = postOnMainThread {
        if (canceled.value) {
            return@postOnMainThread
        }
        delegateRef.set(action())
        if (canceled.value) {
            delegateRef.getAndSet(null)?.cancel()
        }
    }
    return Cancelable {
        if (canceled.compareAndSet(expect = false, update = true)) {
            job.cancel()
            delegateRef.getAndSet(null)?.cancel()
        }
    }
}

internal class CancelableAdLoader(private val loadCallback: AdLoadListener) : Cancelable {

    private val isCanceled: AtomicBoolean = atomic(false)

    fun <PlatformAdLoadParameter> platformLoadAd(loadParameter: PlatformAdLoadParameter) {
        platformLoadAdWithAnalyzer(
            loadParameter = loadParameter,
            loadCallback = object : AdLoadListener {
                override fun onAdLoadFailed(code: Int, message: String) {
                    val isCanceled = isCanceled.value
                    if (isCanceled) {
                        return
                    }
                    loadCallback.onAdLoadFailed(code = code, message = message)
                }

                override fun onAdLoadSuccess() {
                    val isCanceled = isCanceled.value
                    if (isCanceled) {
                        return
                    }
                    loadCallback.onAdLoadSuccess()
                }
            }
        )
    }

    override fun cancel() {
        if (isCanceled.compareAndSet(expect = false, update = true)) {
            loadCallback.onAdLoadFailed(
                code = ERROR_CODE_AD_LOAD_CANCELED,
                message = "Ad loading canceled"
            )
        }
    }
}
