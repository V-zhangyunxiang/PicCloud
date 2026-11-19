package com.poseidon.ad.core.flow

import com.poseidon.ad.core.log.LogHub
import com.poseidon.ad.main.processor.PoseidonAPIProcessor
import com.poseidon.ad.main.adinfo.AD_TYPE_INTERSTITIAL
import com.poseidon.ad.main.adinfo.AD_TYPE_REWARDED
import com.poseidon.ad.main.processor.PlatformInterstitialAdLoadParameter
import com.poseidon.ad.main.processor.PlatformNativeAdLoadParameter
import com.poseidon.ad.main.processor.PlatformOpenAdLoadParameter
import com.poseidon.ad.main.processor.PlatformRewardedAdLoadParameter
import com.poseidon.ad.main.preload.notification.NotificationSplashAdManager
import com.poseidon.ad.main.preload.notification.isNotificationSplashAdPreloadScene
import com.poseidon.ad.main.preload.new.isNewUserEntryScene
import com.poseidon.ad.main.preload.new.shouldBlockOrdinaryPreload

/**
 * AdFlow heavy-lifts ad loading/showing logic away from API shells.
 * AdFlow 将广告的加载与展示流程集中管理，旧 API 仅作为委派层，逻辑保持不变。
 */
object AdFlow {

    private const val TAG = "AdFlow"

    fun loadInterstitialAd(loadParameter: PlatformInterstitialAdLoadParameter) {
        LogHub.info(TAG) { "loadInterstitialAd: scene=${loadParameter.sceneName}" }
        if (isNewUserEntryScene(loadParameter.sceneName, AD_TYPE_INTERSTITIAL)) {
            PoseidonAPIProcessor.platformPreload(loadParameter = loadParameter)
            return
        }
        if (shouldBlockOrdinaryPreload(loadParameter.sceneName)) {
            LogHub.info(TAG) { "Skip ordinary interstitial preload before first show: sceneName=${loadParameter.sceneName}" }
            return
        }
        if (isNotificationSplashAdPreloadScene(sceneName = loadParameter.sceneName)) {
            NotificationSplashAdManager.preloadAd(loadParameter = loadParameter)
            return
        }
        PoseidonAPIProcessor.platformPreload(loadParameter = loadParameter)
    }

    fun loadOpenAd(loadParameter: PlatformOpenAdLoadParameter) {
        LogHub.info(TAG) { "loadOpenAd: scene=${loadParameter.sceneName}" }
        if (shouldBlockOrdinaryPreload(loadParameter.sceneName)) {
            LogHub.info(TAG) { "Skip ordinary open preload before first show: sceneName=${loadParameter.sceneName}" }
            return
        }
        PoseidonAPIProcessor.platformPreload(loadParameter = loadParameter)
    }

    fun loadRewardedAd(loadParameter: PlatformRewardedAdLoadParameter) {
        LogHub.info(TAG) { "loadRewardedAd: scene=${loadParameter.sceneName}" }
        if (isNewUserEntryScene(loadParameter.sceneName, AD_TYPE_REWARDED)) {
            PoseidonAPIProcessor.platformPreload(loadParameter = loadParameter)
            return
        }
        if (shouldBlockOrdinaryPreload(loadParameter.sceneName)) {
            LogHub.info(TAG) { "Skip ordinary rewarded preload before first show: sceneName=${loadParameter.sceneName}" }
            return
        }
        PoseidonAPIProcessor.platformPreload(loadParameter = loadParameter)
    }

    fun loadNativeAd(loadParameter: PlatformNativeAdLoadParameter) {
        LogHub.info(TAG) { "loadNativeAd: scene=${loadParameter.sceneName}" }
        if (shouldBlockOrdinaryPreload(loadParameter.sceneName)) {
            LogHub.info(TAG) { "Skip ordinary native preload before first show: sceneName=${loadParameter.sceneName}" }
            return
        }
        PoseidonAPIProcessor.platformPreload(loadParameter = loadParameter)
    }
}
