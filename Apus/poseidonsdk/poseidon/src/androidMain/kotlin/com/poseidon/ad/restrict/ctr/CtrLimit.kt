package com.poseidon.ad.restrict.ctr

/**
 * CTR 限制入口对象，对外暴露点击、曝光、关闭等回调以及展示权限判断。
 */
internal object CtrLimit {

    /**
     * 统一日志入口，保证英文输出。
     */
    internal fun logInfo(lazyMessage: () -> String) {
        CtrLimitLogger.info(lazyMessage)
    }

    private fun isSwitchOn(): Boolean {
        return CtrRemoteConfigProvider.isCtrSwitchOn()
    }

    /**
     * 处理广告点击，统计点击次数并根据策略强制关闭或禁播。
     */
    fun callOnAdClicked(sessionId: String, onAdForceClosed: () -> Unit) {
        logInfo { "callOnAdClicked: sessionId=$sessionId" }
        if (!isSwitchOn()) {
            return
        }

        val count = CtrClickTracker.increment(sessionId)

        val forceCloseAdOnClickCount = CtrRemoteConfigProvider.forceCloseThreshold()
        if (count > forceCloseAdOnClickCount) {
            closeResumedAd(sessionId, onAdForceClosed)
        }

        val cannotShowAdTodayOnClickCount = CtrRemoteConfigProvider.dailyLimitThreshold()
        resetCountIfNewDay()
        if (count >= cannotShowAdTodayOnClickCount) {
            closeResumedAd(sessionId, onAdForceClosed)
            CtrDailyLimitStore.markLimitReached()
        }
    }

    /**
     * 判断当前是否可以继续展示广告。
     */
    fun canShowAd(): Boolean {
        if (!isSwitchOn()) {
            return true
        }
        resetCountIfNewDay()
        return !CtrDailyLimitStore.isLimitReached()
    }

    private fun resetCountIfNewDay() {
        CtrDailyLimitStore.resetIfNewDay()
    }
}

/**
 * 尝试根据 sessionId 关闭相关广告 Activity。
 */
internal fun CtrLimit.closeResumedAd(sessionId: String, onAdForceClosed: () -> Unit) {
    CtrAdCloser.close(sessionId, onAdForceClosed)
}

/**
 * 记录广告曝光时的 Activity。
 */
internal fun CtrLimit.callOnAdImpression(sessionId: String) {
    CtrActivityTracker.recordImpression(sessionId)
}

/**
 * 清理广告关闭后的会话状态。
 */
internal fun CtrLimit.callOnAdClosed(sessionId: String) {
    CtrActivityTracker.clearSession(sessionId)
}
