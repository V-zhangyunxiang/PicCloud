package com.poseidon.ad.main.load

import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.main.adinfo.AD_TYPE_INTERSTITIAL
import com.poseidon.ad.main.adinfo.InterstitialAdBase
import com.poseidon.ad.main.processor.PlatformInterstitialAdLoadParameter
import com.poseidon.ad.mediation.MediationDef

abstract class InterstitialAdLoader(parameter: InterstitialParameter) : AdLoaderBase<InterstitialAdBase, PlatformInterstitialAdLoadParameter, InnerAdLoadCallback<InterstitialAdBase>>(parameter = parameter) {

}

class InterstitialParameter(
    adUnitId: String,
    startLoadTimeInMillis: Long,
    @MediationDef network: Int,
    sessionId: String,
    sceneName: String,
    poseidonConfigVersion: VersionHelper?
): Parameter(adUnitId = adUnitId, startLoadTimeInMillis = startLoadTimeInMillis, network = network, sessionId = sessionId, adType = AD_TYPE_INTERSTITIAL, sceneName = sceneName, poseidonConfigVersion = poseidonConfigVersion)
