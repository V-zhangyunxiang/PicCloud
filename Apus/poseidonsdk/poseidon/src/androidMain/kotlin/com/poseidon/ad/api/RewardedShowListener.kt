package com.poseidon.ad.api

import com.poseidon.ad.common.toolkit.logInfo

/**
 * 激励广告全屏展示监听器（RewardedShowListener）。
 *
 * 在激励广告播放过程中输出英文日志，便于宿主核对奖励触发流程。
 */
interface RewardedShowListener : FullScreenContentCallback {

    /**
     * 激励奖励即将结算时触发，可用于业务侧前置准备。
     */
    fun onRewardEvaluation() {
        logInfo(TAG) { "Reward evaluation callback triggered." }
    }

    override fun onAdClicked() {
        logInfo(TAG) { "Rewarded ad clicked." }
        super.onAdClicked()
    }

    override fun onAdDismissedFullScreenContent() {
        logInfo(TAG) { "Rewarded ad dismissed fullscreen content." }
        super.onAdDismissedFullScreenContent()
    }

    override fun onAdFailedToShowFullScreenContent(code: Int, message: String) {
        logInfo(TAG) { "Rewarded ad failed, code=$code, message=$message." }
        super.onAdFailedToShowFullScreenContent(code, message)
    }

    override fun onAdShowedFullScreenContent() {
        logInfo(TAG) { "Rewarded ad showed fullscreen content." }
        super.onAdShowedFullScreenContent()
    }

    private companion object {
        private const val TAG = "RewardedShowListener"
    }
}
