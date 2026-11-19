package com.poseidon.ad.api

import com.poseidon.ad.common.toolkit.logInfo

/**
 * 插屏广告全屏展示监听器（InterstitialShowListener）。
 *
 * 提供插屏广告在展示全屏内容时的事件回调，并默认输出英文日志，方便快速排查。
 */
interface InterstitialShowListener : FullScreenContentCallback {

    /**
     * 插屏广告曝光时触发，可用于记录业务打点。
     *
     * @param sceneName 插屏广告场景名，方便定位问题。
     */
    fun onInterstitialExposure(sceneName: String) {
        logInfo(TAG) { "Interstitial ad exposure, scene=$sceneName." }
    }

    override fun onAdClicked() {
        logInfo(TAG) { "Interstitial ad clicked." }
        super.onAdClicked()
    }

    override fun onAdDismissedFullScreenContent() {
        logInfo(TAG) { "Interstitial ad dismissed fullscreen content." }
        super.onAdDismissedFullScreenContent()
    }

    override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
        logInfo(TAG) { "Interstitial ad failed, code=$code, message=$message." }
        super.onAdFailedToShowFullScreenContent(code, message)
    }

    override fun onAdShowedFullScreenContent() {
        logInfo(TAG) { "Interstitial ad showed fullscreen content." }
        super.onAdShowedFullScreenContent()
    }

    private companion object {
        private const val TAG = "InterstitialShowListener"
    }
}
