package com.poseidon.ad.statistics.core

import com.poseidon.ad.mediation.MediationDef
import com.poseidon.ad.mediation.getMediationNameById
import com.poseidon.ad.statistics.Reporter
import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey

/**
 * 广告网络初始化过程打点。
 */
internal class BizAdInitTracker(
    @MediationDef private val mediationId: Int,
    private val result: String,
    private val take: Long,
) : Reporter(eventName = EventName.AD_INIT) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            ParamKey.MEDIATION_NAME to getMediationNameById(mediationId),
            ParamKey.INIT_RESULT to result,
            ParamKey.DURATION to take,
        )
    }
}
