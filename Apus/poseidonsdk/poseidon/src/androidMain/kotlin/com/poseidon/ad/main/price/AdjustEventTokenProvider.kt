package com.poseidon.ad.main.price

import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.main.strategy.common.AdjustEventTokensCfg
import com.poseidon.ad.main.strategy.common.PoseidonConfig

/**
 * 统一的 Adjust token 读取器，确保缺失配置时能够打印日志并安全降级。
 */
internal object AdjustEventTokenProvider {

    private const val TAG = "AdjustEventTokens"

    fun adImpressionToken(): String? = resolveToken { it.adImpressionToken }

    fun fullscreenImpressionToken(): String? = resolveToken { it.fullscreenAdImpressionToken }

    fun highValueFirstDayToken(): String? = resolveToken { it.highValueFirstDayToken }

    fun highValueNextDayToken(): String? = resolveToken { it.highValueNextDayToken }

    private inline fun resolveToken(selector: (AdjustEventTokensCfg) -> String?): String? {
        val tokens = PoseidonConfig.get()?.adjustTokens
        if (tokens == null) {
            logInfo(TAG) { "Adjust tokens not prepared yet" }
            return null
        }
        val token = selector(tokens)
        if (token.isNullOrBlank()) {
            logInfo(TAG) { "Requested Adjust token is empty" }
            return null
        }
        return token
    }
}
