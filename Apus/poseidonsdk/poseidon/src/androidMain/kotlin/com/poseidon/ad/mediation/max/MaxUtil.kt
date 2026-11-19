package com.poseidon.ad.mediation.max

import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.google.common.base.MoreObjects
import com.poseidon.ad.main.adinfo.normalizePlacementIdByAdSource
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Admob
import com.poseidon.ad.main.price.AD_SOURCE_ID_Applovin
import com.poseidon.ad.main.price.AD_SOURCE_ID_Bigo
import com.poseidon.ad.main.price.AD_SOURCE_ID_Facebook
import com.poseidon.ad.main.price.AD_SOURCE_ID_Fyber
import com.poseidon.ad.main.price.AD_SOURCE_ID_GoogleAdManager
import com.poseidon.ad.main.price.AD_SOURCE_ID_Mintegral
import com.poseidon.ad.main.price.AD_SOURCE_ID_UnityAds
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AD_SOURCE_ID_Vungle
import com.poseidon.ad.main.price.AdPaidInfo
import com.poseidon.ad.main.adinfo.AdSourceInfo
import com.poseidon.ad.main.price.ESTIMATED
import com.poseidon.ad.main.price.PRECISE
import com.poseidon.ad.main.price.PUBLISHER_PROVIDED
import com.poseidon.ad.main.price.UNKNOWN

internal fun MaxAd.contentToString(): String {
    return MoreObjects.toStringHelper(MaxAd::class.java)
        .add("adUnitId", adUnitId)
        .add("revenue", revenue)
        .add("waterfall", waterfall)
        .add("size", size)
        .add("revenuePrecision", revenuePrecision)
        .add("requestLatencyMillis", requestLatencyMillis)
        .add("networkName", networkName)
        .add("networkPlacement", networkPlacement)
        .add("placement", placement)
        .add("format", format)
        .add("creativeId", creativeId)
        .add("dspId", dspId)
        .add("dspName", dspName)
        .add("nativeAd", nativeAd)
        .toString()
}

internal fun MaxError.contentToString(): String {
    return MoreObjects.toStringHelper(MaxError::class.java)
        .add("code", code)
        .add("message", message)
        .add("waterfall", waterfall)
        .add("mediatedNetworkErrorMessage", mediatedNetworkErrorMessage)
        .add("requestLatencyMillis", requestLatencyMillis)
        .toString()
}

internal fun MaxReward.contentToString(): String {
    return MoreObjects.toStringHelper(MaxError::class.java)
        .add("label", label)
        .add("amount", amount)
        .toString()
}

internal fun resolveAdPaidInfo(maxAd: MaxAd, loadParameter: Parameter) : AdPaidInfo {
    return resolveAdPaidInfo(
        maxAd = maxAd,
        loadParameter = loadParameter,
        adSourceInfo = maxAd.resolveAdSourceInfo()
    )
}

internal fun resolveAdPaidInfo(
    maxAd: MaxAd,
    loadParameter: Parameter,
    adSourceInfo: AdSourceInfo?
) : AdPaidInfo {
    val currencyCode = "USD"
    val precision = maxAd.revenuePrecision
    val revenue = maxAd.revenue
    return AdPaidInfo(
        revenue = revenue,
        eCPM = revenue * 1000,
        currencyCode = currencyCode,
        precision = when (precision) {
            "estimated" -> ESTIMATED
            "publisher_defined" -> PUBLISHER_PROVIDED
            "exact" -> PRECISE
            "undefined" -> UNKNOWN
            else -> UNKNOWN
        },
        networkId = loadParameter.network,
        adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
        adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
        adUnitId = loadParameter.adUnitId,
        adSourceName = adSourceInfo?.adSourceName
    )
}

fun MaxAd?.resolveAdSourceInfo(): AdSourceInfo? {
    if (this == null) return null
    // https://developers.applovin.com/en/max/android/overview/advanced-settings#impression-level-user-revenue-api
    val networkName: String? = networkName
    val networkPlacement: String? = networkPlacement
    val lowercaseNetworkName = networkName?.lowercase().orEmpty()
    val adSourceId = when {
        lowercaseNetworkName.contains("applovin") -> AD_SOURCE_ID_Applovin
        lowercaseNetworkName.contains("admob") -> AD_SOURCE_ID_Admob
        lowercaseNetworkName.contains("google ad manager") -> AD_SOURCE_ID_GoogleAdManager
        lowercaseNetworkName.contains("vungle") || lowercaseNetworkName.contains("liftoff") -> AD_SOURCE_ID_Vungle
        lowercaseNetworkName.contains("unity") -> AD_SOURCE_ID_UnityAds
        lowercaseNetworkName.contains("bigo") -> AD_SOURCE_ID_Bigo
        lowercaseNetworkName.contains("facebook") || lowercaseNetworkName.contains("meta") || lowercaseNetworkName.contains("audience") -> AD_SOURCE_ID_Facebook
        lowercaseNetworkName.contains("mintegral") -> AD_SOURCE_ID_Mintegral
        lowercaseNetworkName.contains("fyber") || lowercaseNetworkName.contains("dt exchange") -> AD_SOURCE_ID_Fyber
        else -> AD_SOURCE_ID_Unknown
    }

    return AdSourceInfo(
        adSourceId = adSourceId,
        adSourcePlacementId = normalizePlacementIdByAdSource(
            adSourceId = adSourceId,
            placementId = networkPlacement.takeIf { it?.isNotBlank() == true }
        ),
        adSourceName = networkName.takeIf { it?.isNotBlank() == true }
    )
}
