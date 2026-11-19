package com.poseidon.ad.statistics.biz

import com.poseidon.ad.main.adinfo.CodeDef
import com.poseidon.ad.mediation.MediationDef
import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey
import com.poseidon.ad.statistics.Reporter

/**
 * 广告素材返回打点。
 */
internal class UnitResponseTracker(
    @MediationDef private val sessionId: String,
    private val sceneName: String,
    @CodeDef private val resultCode: Int,
    private val resultMessage: String,
) : Reporter(eventName = EventName.UNIT_RESPONSE) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            ParamKey.UNIT_RESPONSE_SESSION_ID to sessionId,
            ParamKey.UNIT_RESPONSE_SCENE_NAME to sceneName,
            ParamKey.UNIT_RESPONSE_ERROR_CODE to resultCode,
            ParamKey.UNIT_RESPONSE_ERROR_INFO to criticalPressureAnalyticsMessage(resultMessage),
        )
    }
}
