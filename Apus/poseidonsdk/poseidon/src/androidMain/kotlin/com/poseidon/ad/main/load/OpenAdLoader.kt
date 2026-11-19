package com.poseidon.ad.main.load

import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.main.adinfo.AD_TYPE_INTERSTITIAL
import com.poseidon.ad.main.adinfo.AD_TYPE_OPEN
import com.poseidon.ad.main.adinfo.OpenAdBase
import com.poseidon.ad.main.processor.PlatformOpenAdLoadParameter
import com.poseidon.ad.mediation.MediationDef

abstract class OpenAdLoader(parameter: OpenParameter) : AdLoaderBase<OpenAdBase, PlatformOpenAdLoadParameter, InnerAdLoadCallback<OpenAdBase>>(parameter = parameter) {

}

class OpenParameter(
    adUnitId: String,
    startLoadTimeInMillis: Long,
    @MediationDef network: Int,
    sessionId: String,
    sceneName: String,
    poseidonConfigVersion: VersionHelper?
): Parameter(adUnitId = adUnitId, startLoadTimeInMillis = startLoadTimeInMillis, network = network, sessionId = sessionId, adType = AD_TYPE_OPEN, sceneName = sceneName, poseidonConfigVersion = poseidonConfigVersion)
