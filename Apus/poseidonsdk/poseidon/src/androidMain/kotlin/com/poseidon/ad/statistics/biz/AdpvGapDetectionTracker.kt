package com.poseidon.ad.statistics.biz

import com.poseidon.ad.main.adinfo.AdTypeDef
import com.poseidon.ad.special.topon.AttributionChannelMtg
import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey
import com.poseidon.ad.statistics.Reporter

/**
 * 广告空档检测打点。
 */
@Suppress("SpellCheckingInspection")
internal class AdpvGapDetectionTracker(
    private val sceneName: String,
    @AdTypeDef private val adType: Int,
) : Reporter(eventName = EventName.ADPV_GAP) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            ParamKey.SCENE_NAME to sceneName,
            ParamKey.AD_TYPE to adType,
            ParamKey.ATTRIBUTION_CHANNEL_SNAKE to AttributionChannelMtg.get(),
        )
    }
}
