package com.poseidon.ad.api

import com.poseidon.ad.common.toolkit.logInfo

/**
 * 开屏广告全屏展示监听器（OpenShowListener）。
 *
 * 负责记录开屏广告在展示流程中的关键事件，默认输出英文日志。
 */
interface OpenShowListener : FullScreenContentCallback {

    /**
     * 开屏广告冷启动阶段触发，便于业务侧观察首屏体验。
     */
    fun onSplashLaunch() {
        logInfo(TAG) { "Splash launch callback triggered." }
    }

    override fun onAdClicked() {
        logInfo(TAG) { "Open ad clicked." }
        super.onAdClicked()
    }

    override fun onAdDismissedFullScreenContent() {
        logInfo(TAG) { "Open ad dismissed fullscreen content." }
        super.onAdDismissedFullScreenContent()
    }

    override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
        logInfo(TAG) { "Open ad failed, code=$code, message=$message." }
        super.onAdFailedToShowFullScreenContent(code, message)
    }

    override fun onAdShowedFullScreenContent() {
        logInfo(TAG) { "Open ad showed fullscreen content." }
        super.onAdShowedFullScreenContent()
    }

    private companion object {
        private const val TAG = "OpenShowListener"
    }
}
