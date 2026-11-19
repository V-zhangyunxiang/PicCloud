package com.poseidon.ad.statistics.core

import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.main.adinfo.AdTypeDef
import com.poseidon.ad.main.adinfo.CodeDef
import com.poseidon.ad.main.adinfo.getAdTypeName
import com.poseidon.ad.main.load.AdLoadDurations
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.strategy.estimated.ecpm.EstimatedEcpmCfg
import com.poseidon.ad.mediation.MediationDef
import com.poseidon.ad.mediation.getMediationNameById
import com.poseidon.ad.statistics.Reporter
import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey

/**
 * 广告加载详细打点。
 */
internal class BizAdResponseTracker(
    @MediationDef private val sessionId: String,
    private val sceneName: String,
    private val adUnitId: String,
    private val adSourcePlacementId: String?,
    @AdSourceIdDef private val adSourceId: Int?,
    @AdTypeDef private val adType: Int,
    @MediationDef private val networkId: Int,
    private val take: Long,
    private val durations: AdLoadDurations?,
    @CodeDef private val resultCode: Int,
    private val resultMessage: String,
    private val revenue: Double?,
    private val poseidonConfigVersion: VersionHelper?,
    private val adSourceName: String?,
    private val estimatedRevenue: Double?= -1.0,
) : Reporter(eventName = EventName.AD_RESPONSE) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf<String, Any?>(
            ParamKey.SESSION_ID to sessionId,
            ParamKey.SCENE_NAME to sceneName,
            ParamKey.PLACEMENT_ID to adUnitId,
            ParamKey.NETWORK_PLACEMENT_ID to adSourcePlacementId,
            ParamKey.NETWORK_ID to adSourceId,
            ParamKey.NETWORK_NAME to adSourceName,
            ParamKey.AD_TYPE to getAdTypeName(adType),
            ParamKey.MEDIATION_NAME to getMediationNameById(networkId),
            ParamKey.DURATION to take,
            ParamKey.BIZ_PRE_REQ_DURATION to durations?.preRequestDuration,
            ParamKey.BIZ_REQ_DURATION to durations?.requestDuration,
            ParamKey.BIZ_AFTER_REQ_DURATION to durations?.afterRequestDuration,
            ParamKey.BIZ_TRAFFIC_CONTROL_DURATION to durations?.trafficControlDuration,
            ParamKey.RESULT_CODE to resultCode,
            ParamKey.RESULT_MESSAGE to resultMessage,
            ParamKey.REVENUE_VALUE to revenue,
            ParamKey.ESTIMATED_ECPM_VERSION to EstimatedEcpmCfg.version()?.toString(),
            ParamKey.ESTIMATED_ECPM_REVENUE to estimatedRevenue
        ).filterValues { value: Any? -> value != null }
    }

    override fun resolvePoseidonConfigVersion(): VersionHelper? = poseidonConfigVersion
}
