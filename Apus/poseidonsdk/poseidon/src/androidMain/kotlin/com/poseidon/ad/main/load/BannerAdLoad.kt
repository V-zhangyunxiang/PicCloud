package com.poseidon.ad.main.load

import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.main.adinfo.AD_TYPE_BANNER
import com.poseidon.ad.main.adinfo.BannerAdBase
import com.poseidon.ad.main.processor.PlatformBannerAdLoadAndShowParameter
import com.poseidon.ad.mediation.MediationDef

abstract class BannerAdLoad(parameter: BannerParameter) : AdLoaderBase<BannerAdBase, PlatformBannerAdLoadAndShowParameter, InnerAdLoadAndShowCallback<BannerAdBase>>(parameter = parameter) {

    abstract fun destroy()
}

class BannerParameter(
    adUnitId: String,
    startLoadTimeInMillis: Long,
    @MediationDef network: Int,
    sessionId: String,
    sceneName: String,
    poseidonConfigVersion: VersionHelper?
): Parameter(adUnitId = adUnitId, startLoadTimeInMillis = startLoadTimeInMillis, network = network, sessionId = sessionId, adType = AD_TYPE_BANNER, sceneName = sceneName, poseidonConfigVersion = poseidonConfigVersion)
