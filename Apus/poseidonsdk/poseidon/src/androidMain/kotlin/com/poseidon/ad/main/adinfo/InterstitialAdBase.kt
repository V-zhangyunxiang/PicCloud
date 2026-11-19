package com.poseidon.ad.main.adinfo

import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.main.processor.PlatformInterstitialAdShowParameter
import com.poseidon.ad.main.show.InnerAdShowConfig

abstract class InterstitialAdBase : FullScreenAdBase() {
    abstract fun show(showParameter: PlatformInterstitialAdShowParameter, showConfig: InnerAdShowConfig): ShowResultCallback

    final override fun getAdType(): Int = AD_TYPE_INTERSTITIAL
}