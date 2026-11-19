package com.poseidon.ad.statistics.biz

import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey
import com.poseidon.ad.statistics.Reporter

/**
 * TopOn 流量分组打点。
 */
internal class TopOnTrafficGroupingReporter(
    private val adUnitId: String,
    private val attributionChannelName: String,
) : Reporter(eventName = EventName.TOPON_TRAFFIC) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            ParamKey.AD_UNIT_ID to adUnitId,
            ParamKey.ATTRIBUTION_CHANNEL to attributionChannelName,
        )
    }
}

/**
 * Tohunga 渠道状态打点。
 */
@Suppress("SpellCheckingInspection")
internal class TohungaAnalyzer(
    private val attributionChannelName: String?,
) : Reporter(eventName = EventName.TOHUNGA) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            ParamKey.ATTRIBUTION_CHANNEL_SNAKE to attributionChannelName,
        )
    }
}

/**
 * Adjust 归因网络打点。
 */
internal class AdjustAttributionNetworkAnalyzer(
    private val attributionNetworkFrom: String,
    private val attributionNetworkValue: String?,
) : Reporter(eventName = EventName.ADJUST_ATTR_NETWORK) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            ParamKey.ATTRIBUTION_NETWORK_FROM to attributionNetworkFrom,
            ParamKey.ATTRIBUTION_NETWORK_VALUE to attributionNetworkValue,
        )
    }
}

/**
 * Adjust 归因活动打点。
 */
internal class AdjustAttributionCampaignAnalyzer(
    private val attributionCampaignFrom: String,
    private val attributionCampaignValue: String?,
) : Reporter(eventName = EventName.ADJUST_ATTR_CAMPAIGN) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            ParamKey.ATTRIBUTION_CAMPAIGN_FROM to attributionCampaignFrom,
            ParamKey.ATTRIBUTION_CAMPAIGN_VALUE to attributionCampaignValue,
        )
    }
}

/**
 * Adjust 归因原始信息打点。
 */
internal class AdjustAttributionAnalyzer(
    private val attributionFrom: String?,
    private val attributionValue: String?,
) : Reporter(eventName = EventName.ADJUST_ATTR_RAW) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            ParamKey.ATTRIBUTION_FROM to attributionFrom,
            ParamKey.ATTRIBUTION_VALUE to attributionValue,
        )
    }
}
