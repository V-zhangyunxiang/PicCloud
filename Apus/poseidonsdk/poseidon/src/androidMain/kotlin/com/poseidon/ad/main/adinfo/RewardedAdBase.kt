package com.poseidon.ad.main.adinfo

import com.poseidon.ad.api.OnUserEarnedRewardListener
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.main.processor.PlatformRewardedAdShowParameter
import com.poseidon.ad.main.show.InnerAdShowConfig

abstract class RewardedAdBase : FullScreenAdBase() {

    abstract fun show(showParameter: PlatformRewardedAdShowParameter, showConfig: InnerAdShowConfig, onUserEarnedRewardListener: OnUserEarnedRewardListener?): ShowResultCallback

    final override fun getAdType(): Int = AD_TYPE_REWARDED
}