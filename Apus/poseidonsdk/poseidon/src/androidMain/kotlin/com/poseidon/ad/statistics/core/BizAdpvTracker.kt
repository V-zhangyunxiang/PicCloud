package com.poseidon.ad.statistics.core

import com.poseidon.ad.main.adinfo.AdTypeDef
import com.poseidon.ad.main.adinfo.ShowCodeDef
import com.poseidon.ad.main.adinfo.getAdTypeName
import com.poseidon.ad.main.strategy.estimated.ecpm.EstimatedEcpmCfg
import com.poseidon.ad.statistics.Reporter
import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey
import com.poseidon.ad.statistics.biz.criticalPressureAnalyticsMessage

/**
 * 广告展示机会打点。
 */
@Suppress("SpellCheckingInspection")
internal class BizAdpvTracker(
    private val sceneName: String,
    @AdTypeDef private val adType: Int,
    @ShowCodeDef private val resultCode: Int,
    private val resultMessage: String,
) : Reporter(eventName = EventName.AD_PV) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            ParamKey.SCENE_NAME to sceneName,
            ParamKey.AD_TYPE to getAdTypeName(adType),
            ParamKey.RESULT_CODE to resultCode,
            ParamKey.RESULT_MESSAGE to criticalPressureAnalyticsMessage(resultMessage),
            ParamKey.ESTIMATED_ECPM_VERSION to EstimatedEcpmCfg.version()?.toString(),
        )
    }
}
