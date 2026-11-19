package com.poseidon.ad.api

import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import com.poseidon.ad.common.toolkit.Cancelable
import com.poseidon.ad.common.toolkit.Destroyable
import com.poseidon.ad.main.processor.PlatformBannerAdLoadAndShowParameter
import com.poseidon.ad.main.processor.PlatformHasCacheParameter
import com.poseidon.ad.main.processor.PlatformInterstitialAdLoadParameter
import com.poseidon.ad.main.processor.PlatformInterstitialAdShowParameter
import com.poseidon.ad.main.processor.PlatformNativeAdLoadParameter
import com.poseidon.ad.main.processor.PlatformNativeAdShowParameter
import com.poseidon.ad.main.processor.PlatformOpenAdLoadParameter
import com.poseidon.ad.main.processor.PlatformOpenAdShowParameter
import com.poseidon.ad.main.processor.PlatformRewardedAdLoadParameter
import com.poseidon.ad.main.processor.PlatformRewardedAdShowParameter
import com.poseidon.ad.main.processor.PoseidonAPIProcessor
import com.poseidon.ad.main.processor.loadAndShowBannerAdWithLifecycle
import com.poseidon.ad.restrict.gdpr.PlatformConsentFormShowParameter

/**
 * 对外暴露 SDK 主流程的门面，便于上层统一接入。
 */
class PoseidonAPI private constructor() {

    companion object {

        /**
         * 负责将实例注册为全局平台，必须在主线程调用。
         *
         * @param poseidonInitConfig Android端具体实例
         * @return 已注册的对象
         */
        @MainThread
        fun register(poseidonInitConfig: PoseidonSetupProcessor): PoseidonSetupProcessor {
            return poseidonInitConfig.register()
        }

        /**
         * 调用底层实例完成初始化。
         */
        @MainThread
        fun initialize() {
            PoseidonSetupProcessor.Companion.getInstance().initialize()
        }

        /**
         * GDPR 隐私入口的回调注册。
         */
        fun doOnPrivacyOptionsIsRequired(action: () -> Unit) {
            PoseidonAPIProcessor.doOnPrivacyOptionsIsRequired(action)
        }

        fun setMemoryPressureListener(listener: PoseidonMemoryPressureListener?) {
            PoseidonSetupProcessor.Companion.getInstance().setMemoryPressureListener(listener)
        }

        /**
         * 展示隐私设置界面。
         */
        fun showPrivacyOptionsForm(parameter: PlatformConsentFormShowParameter) {
            PoseidonAPIProcessor.showPrivacyOptionsForm(parameter)
        }

        /**
         * 查询是否已有缓存的广告。
         */
        fun hasCacheAd(parameter: PlatformHasCacheParameter): Boolean {
            return PoseidonAPIProcessor.hasCacheAd(parameter)
        }

        /**
         * 加载插屏广告。
         */
        fun loadInterstitialAd(loadParameter: PlatformInterstitialAdLoadParameter) {
            PoseidonAPIProcessor.loadInterstitialAd(loadParameter)
        }

        /**
         * 加载开屏广告。
         */
        fun loadOpenAd(loadParameter: PlatformOpenAdLoadParameter) {
            PoseidonAPIProcessor.loadOpenAd(loadParameter)
        }

        /**
         * 加载激励广告。
         */
        fun loadRewardedAd(loadParameter: PlatformRewardedAdLoadParameter) {
            PoseidonAPIProcessor.loadRewardedAd(loadParameter)
        }

        /**
         * 加载原生广告。
         */
        fun loadNativeAd(loadParameter: PlatformNativeAdLoadParameter) {
            PoseidonAPIProcessor.loadNativeAd(loadParameter)
        }

        /**
         * 展示开屏广告，监听器可选，未设置时仅触发 SDK 内部回调。
         */
        fun showOpenAd(showParameter: PlatformOpenAdShowParameter): Cancelable {
            return PoseidonAPIProcessor.showOpenAd(showParameter = showParameter)
        }

        /**
         * 展示插屏广告，监听器可选，未设置时仅触发 SDK 内部回调。
         */
        fun showInterstitialAd(showParameter: PlatformInterstitialAdShowParameter): Cancelable {
            return PoseidonAPIProcessor.showInterstitialAd(showParameter = showParameter)
        }

        /**
         * 展示激励广告，奖励监听与展示监听均为可选参数。
         */
        fun showRewardedAd(showParameter: PlatformRewardedAdShowParameter): Cancelable {
            return PoseidonAPIProcessor.showRewardedAd(
                showParameter = showParameter
            )
        }

        /**
         * 展示原生广告，监听器可选。
         */
        fun showNativeAd(showParameter: PlatformNativeAdShowParameter): Cancelable {
            return PoseidonAPIProcessor.showNativeAd(showParameter = showParameter)
        }

        /**
         * 加载并展示 Banner 广告，返回可控销毁句柄。
         */
        fun loadAndShowBannerAd(
            loadAndShowParameter: PlatformBannerAdLoadAndShowParameter
        ): Destroyable {
            return loadAndShowBannerAd(
                loadAndShowParameter = loadAndShowParameter,
                onAdClosed = loadAndShowParameter.onAdClosed
            )
        }

        fun loadAndShowBannerAd(
            loadAndShowParameter: PlatformBannerAdLoadAndShowParameter,
            onAdClosed: (() -> Unit)? = null
        ): Destroyable {
            return PoseidonAPIProcessor.loadAndShowBannerAd(
                loadAndShowParameter = loadAndShowParameter,
                onAdClosed = onAdClosed
            )
        }

        /**
         * 基于宿主生命周期自动管理 Banner 广告的加载与销毁。
         */
        fun loadAndShowBannerAdWithLifecycle(
            lifecycle: Lifecycle,
            loadAndShowParameter: PlatformBannerAdLoadAndShowParameter
        ) {
            loadAndShowBannerAdWithLifecycle(
                lifecycle = lifecycle,
                loadAndShowParameter = loadAndShowParameter,
                onAdClosed = loadAndShowParameter.onAdClosed
            )
        }

        fun loadAndShowBannerAdWithLifecycle(
            lifecycle: Lifecycle,
            loadAndShowParameter: PlatformBannerAdLoadAndShowParameter,
            onAdClosed: (() -> Unit)? = null
        ) {
            PoseidonAPIProcessor.loadAndShowBannerAdWithLifecycle(
                lifecycle = lifecycle,
                loadAndShowParameter = loadAndShowParameter,
                onAdClosed = onAdClosed
            )
        }
    }
}
