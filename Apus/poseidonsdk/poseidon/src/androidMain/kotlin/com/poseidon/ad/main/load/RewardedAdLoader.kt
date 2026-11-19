package com.poseidon.ad.main.load

import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.main.adinfo.AD_TYPE_REWARDED
import com.poseidon.ad.main.adinfo.RewardedAdBase
import com.poseidon.ad.main.processor.PlatformRewardedAdLoadParameter
import com.poseidon.ad.mediation.MediationDef

abstract class RewardedAdLoader(parameter: RewardedParameter) : AdLoaderBase<RewardedAdBase, PlatformRewardedAdLoadParameter, InnerAdLoadCallback<RewardedAdBase>>(parameter = parameter) {

}

class RewardedParameter(
    adUnitId: String,
    startLoadTimeInMillis: Long,
    @MediationDef network: Int,
    sessionId: String,
    sceneName: String,
    poseidonConfigVersion: VersionHelper?
): Parameter(adUnitId = adUnitId, startLoadTimeInMillis = startLoadTimeInMillis, network = network, sessionId = sessionId, adType = AD_TYPE_REWARDED, sceneName = sceneName, poseidonConfigVersion = poseidonConfigVersion)
