package com.poseidon.ad.statistics.biz

import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey
import com.poseidon.ad.statistics.Reporter

/**
 * 广告累计收益阈值打点。
 */
internal class AdRevenueAccumulationTracker(
    private val revenue: Double,
    private val currency: String,
) : Reporter(eventName = EventName.REVENUE_THRESHOLD) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            ParamKey.REVENUE_VALUE_GENERIC to revenue,
            ParamKey.CURRENCY to currency,
        )
    }
}
