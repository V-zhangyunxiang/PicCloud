package com.poseidon.ad.main.adinfo

import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.main.processor.PlatformOpenAdShowParameter
import com.poseidon.ad.main.show.InnerAdShowConfig

abstract class OpenAdBase : FullScreenAdBase() {
    abstract fun show(showParameter: PlatformOpenAdShowParameter, showConfig: InnerAdShowConfig): ShowResultCallback

    final override fun getAdType(): Int = AD_TYPE_OPEN
}