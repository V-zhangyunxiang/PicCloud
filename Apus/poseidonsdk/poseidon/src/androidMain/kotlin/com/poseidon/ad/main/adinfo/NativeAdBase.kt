package com.poseidon.ad.main.adinfo

import com.poseidon.ad.api.AdShowListener
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.main.processor.PlatformNativeAdShowParameter

abstract class NativeAdBase : AdBase() {
    final override fun getAdType(): Int = AD_TYPE_NATIVE

    abstract fun setAdListener(listener: AdShowListener)

    abstract fun render(showParameter: PlatformNativeAdShowParameter): ShowResultCallback
}