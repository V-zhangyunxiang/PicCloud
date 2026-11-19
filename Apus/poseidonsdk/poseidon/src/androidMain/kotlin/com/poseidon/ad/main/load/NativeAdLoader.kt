package com.poseidon.ad.main.load

import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.main.adinfo.AD_TYPE_NATIVE
import com.poseidon.ad.main.adinfo.NativeAdBase
import com.poseidon.ad.main.processor.PlatformNativeAdLoadParameter
import com.poseidon.ad.mediation.MediationDef

abstract class NativeAdLoader(parameter: NativeParameter) : AdLoaderBase<NativeAdBase, PlatformNativeAdLoadParameter, InnerAdLoadCallback<NativeAdBase>>(parameter = parameter) {

}

class NativeParameter(
    adUnitId: String,
    startLoadTimeInMillis: Long,
    @MediationDef network: Int,
    sessionId: String,
    sceneName: String,
    poseidonConfigVersion: VersionHelper?
): Parameter(adUnitId = adUnitId, startLoadTimeInMillis = startLoadTimeInMillis, network = network, sessionId = sessionId, adType = AD_TYPE_NATIVE, sceneName = sceneName, poseidonConfigVersion = poseidonConfigVersion)
