package com.poseidon.ad.main.adinfo

import androidx.annotation.IntDef

internal const val AD_TYPE_INTERSTITIAL = 1
internal const val AD_TYPE_REWARDED = 2
internal const val AD_TYPE_NATIVE = 3
internal const val AD_TYPE_OPEN = 4
internal const val AD_TYPE_BANNER = 5

@IntDef(
    AD_TYPE_INTERSTITIAL,
    AD_TYPE_REWARDED,
    AD_TYPE_NATIVE,
    AD_TYPE_OPEN,
    AD_TYPE_BANNER
)
internal annotation class AdTypeDef


@IntDef(
    AD_TYPE_INTERSTITIAL,
    AD_TYPE_REWARDED,
    AD_TYPE_OPEN
)
internal annotation class FullscreenAdTypeDef

internal fun getAdTypeName(@AdTypeDef adType: Int): String {
    return when (adType) {
        AD_TYPE_INTERSTITIAL -> "inter"
        AD_TYPE_NATIVE -> "native"
        AD_TYPE_REWARDED -> "reward"
        AD_TYPE_OPEN -> "open"
        AD_TYPE_BANNER -> "banner"
        else -> "Unknown adType: $adType"
    }
}

internal fun priorityByAdType(@AdTypeDef adType: Int): Int {
    return when (adType) {
        AD_TYPE_REWARDED -> 10
        AD_TYPE_INTERSTITIAL -> 7
        AD_TYPE_OPEN -> 5
        AD_TYPE_BANNER -> 4
        AD_TYPE_NATIVE -> 3
        else -> -1
    }
}

internal fun isFullscreenAd(@AdTypeDef adType: Int): Boolean {
    return adType == AD_TYPE_INTERSTITIAL || adType == AD_TYPE_REWARDED || adType == AD_TYPE_OPEN
}