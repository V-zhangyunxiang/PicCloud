package com.poseidon.ad.statistics.core

import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.main.adinfo.AdTypeDef
import com.poseidon.ad.main.adinfo.getAdTypeName
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.strategy.estimated.ecpm.EstimatedEcpmCfg
import com.poseidon.ad.mediation.MediationDef
import com.poseidon.ad.mediation.getMediationNameById
import com.poseidon.ad.statistics.Reporter
import com.poseidon.ad.statistics.BizAdStatisticsConstants.EventName
import com.poseidon.ad.statistics.BizAdStatisticsConstants.ParamKey

/**
 * 广告点击打点。
 */
internal class BizAdClickTracker(
    private val sessionId: String,
    private val sceneName: String,
    private val adUnitId: String,
    private val adSourcePlacementId: String,
    @AdSourceIdDef private val adSourceId: Int,
    @AdTypeDef private val adType: Int,
    @MediationDef private val networkId: Int,
    private val adSourceName: String?,
    private val poseidonConfigVersion: VersionHelper?,
) : Reporter(eventName = EventName.AD_CLICK) {

    override fun makeParameters(): Map<String, Any?> {
        return mapOf(
            ParamKey.SESSION_ID to sessionId,
            ParamKey.SCENE_NAME to sceneName,
            ParamKey.PLACEMENT_ID to adUnitId,
            ParamKey.NETWORK_PLACEMENT_ID to adSourcePlacementId,
            ParamKey.NETWORK_ID to adSourceId,
            ParamKey.NETWORK_NAME to adSourceName,
            ParamKey.AD_TYPE to getAdTypeName(adType),
            ParamKey.MEDIATION_NAME to getMediationNameById(networkId),
            ParamKey.ESTIMATED_ECPM_VERSION to EstimatedEcpmCfg.version()?.toString(),
        )
    }

    override fun resolvePoseidonConfigVersion(): VersionHelper? = poseidonConfigVersion
}
