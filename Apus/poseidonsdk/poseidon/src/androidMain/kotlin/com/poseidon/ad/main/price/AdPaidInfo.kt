package com.poseidon.ad.main.price

import androidx.annotation.IntDef
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAdRevenue
import com.adjust.sdk.AdjustEvent
import com.google.firebase.analytics.FirebaseAnalytics
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.statistics.SupportAnalyticsBackend
import com.poseidon.ad.statistics.biz.AdRevenueAccumulationTracker
import com.poseidon.ad.common.storage.sp.Key
import com.poseidon.ad.mediation.MediationDef
import com.poseidon.ad.mediation.getMediationNameById
import com.poseidon.ad.mediation.matchMediationId

/**
 * @param revenue revenue
 * @param eCPM eCPM
 * @param currencyCode currency code
 * @param precision precision of currency
 * @param networkId the id of ad network used
 * @param adSourceId the id of ad source filled
 * @param adSourcePlacementId the placement id of ad source filled
 * @param adUnitId the id of ad unit
 */
data class AdPaidInfo(
    val revenue: Double,
    val eCPM: Double,
    val currencyCode: String,
    val precision: Precision,
    @MediationDef val networkId: Int,
    @AdSourceIdDef val adSourceId: Int,
    val adSourcePlacementId: String,
    val adUnitId: String,
    val adSourceName: String? = null,
)

/*
   Based on the ad source ID standard defined by TopOn
   https://help.toponad.com/cn/docs/2KR6QU#TopOn_Supported_Integrated_Ad_Platforms
 */
const val AD_SOURCE_ID_Unknown = -1
const val AD_SOURCE_ID_Facebook = 1
const val AD_SOURCE_ID_Admob = 2
const val AD_SOURCE_ID_Applovin = 5
const val AD_SOURCE_ID_Mintegral = 6
const val AD_SOURCE_ID_UnityAds = 12
const val AD_SOURCE_ID_Vungle = 13
const val AD_SOURCE_ID_Fyber = 37
const val AD_SOURCE_ID_Bigo = 59
const val AD_SOURCE_ID_GoogleAdManager = 33
const val AD_SOURCE_ID_Pangle = 50
const val AD_SOURCE_ID_Bidmachine = 65

@IntDef(
    AD_SOURCE_ID_Unknown,
    AD_SOURCE_ID_Facebook,
    AD_SOURCE_ID_Admob,
    AD_SOURCE_ID_Applovin,
    AD_SOURCE_ID_Mintegral,
    AD_SOURCE_ID_UnityAds,
    AD_SOURCE_ID_Vungle,
    AD_SOURCE_ID_Fyber,
    AD_SOURCE_ID_Bigo,
    AD_SOURCE_ID_GoogleAdManager,
    AD_SOURCE_ID_Pangle,
    AD_SOURCE_ID_Bidmachine
)
annotation class AdSourceIdDef

internal fun priorityByAdSourceId(@AdSourceIdDef adSourceId: Int): Int {
    val defaultPriority : Int = when (adSourceId) {
        AD_SOURCE_ID_Facebook -> 80
        AD_SOURCE_ID_Admob -> 60
        AD_SOURCE_ID_Applovin -> 100
        AD_SOURCE_ID_Mintegral -> 40
        AD_SOURCE_ID_UnityAds -> 40
        AD_SOURCE_ID_Vungle -> 40
        AD_SOURCE_ID_Fyber -> 40
        AD_SOURCE_ID_Bigo -> 40
        AD_SOURCE_ID_GoogleAdManager -> 40
        AD_SOURCE_ID_Unknown -> 40
        else -> 40
    }
    return PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig.getString("poseidon_ad_source_priority_$adSourceId").toIntOrNull() ?: defaultPriority
}

fun AdPaidInfo.trackAdjustAdRevenue() {
    val adjustAdRevenue = AdjustAdRevenue(
        matchMediationId(
            mediationId = networkId,
            topOn = { "topon_sdk" },
            admob = { "admob_sdk" },
            max = { "applovin_max_sdk" },
        )
    )
    adjustAdRevenue.setRevenue(revenue, currencyCode)

    adjustAdRevenue.adRevenueNetwork = adSourceId.toString()
    adjustAdRevenue.adRevenueUnit = adSourcePlacementId
    adjustAdRevenue.adRevenuePlacement = adUnitId

    Adjust.trackAdRevenue(adjustAdRevenue)
}

fun AdPaidInfo.trackFirebaseAdRevenue() {
    PoseidonSetupProcessor.getInstance().analyticsFactory.obtain(by = SupportAnalyticsBackend.Firebase).logEvent(
        name = FirebaseAnalytics.Event.AD_IMPRESSION,
        parameters = mapOf(
            FirebaseAnalytics.Param.AD_PLATFORM to getMediationNameById(
                networkId
            ),
            FirebaseAnalytics.Param.AD_SOURCE to adSourceId,
            FirebaseAnalytics.Param.AD_FORMAT to adSourcePlacementId,
            FirebaseAnalytics.Param.AD_UNIT_NAME to adUnitId,
            FirebaseAnalytics.Param.VALUE to revenue,
            FirebaseAnalytics.Param.CURRENCY to currencyCode,
        )
    )

    PoseidonSetupProcessor.getInstance().analyticsFactory.obtain(by = SupportAnalyticsBackend.Firebase).logEvent(
        name = "Ad_impression_Revenue",
        parameters = mapOf(
            FirebaseAnalytics.Param.AD_PLATFORM to getMediationNameById(
                networkId
            ),
            FirebaseAnalytics.Param.AD_SOURCE to adSourceId,
            FirebaseAnalytics.Param.AD_FORMAT to adSourcePlacementId,
            FirebaseAnalytics.Param.AD_UNIT_NAME to adUnitId,
            FirebaseAnalytics.Param.VALUE to revenue,
            FirebaseAnalytics.Param.CURRENCY to currencyCode,
        )
    )
}

fun AdPaidInfo.trackAdjustEventWhenFullscreenAdImpressed() {
    val fullscreenToken = AdjustEventTokenProvider.fullscreenImpressionToken() ?: return
    val adjustEvent = AdjustEvent(fullscreenToken)
    adjustEvent.setRevenue(revenue, currencyCode)
    Adjust.trackEvent(adjustEvent)
}

fun AdPaidInfo.trackAdjustEventWhenAdImpressed() {
    val token = AdjustEventTokenProvider.adImpressionToken() ?: return
    val adjustEvent = AdjustEvent(token)
    adjustEvent.setRevenue(revenue, currencyCode)
    Adjust.trackEvent(adjustEvent)
}

fun AdPaidInfo.trackAdjustEventWhenHighRevenueFullscreenAdImpressedOnNewUserFirstDayAfterInstalled() {
    if (PoseidonSetupProcessor.getInstance().inNewUserFirstDayAfterInstalled()) {
        val token = AdjustEventTokenProvider.highValueFirstDayToken() ?: return
        val highRevenueInEcpmUnit = highRevenueInEcpmUnit()

        val sharedPreference = PoseidonSetupProcessor.getInstance().sharedPreference
        if (revenue > (highRevenueInEcpmUnit / 1000) && !sharedPreference.getBoolean(
                Key.HighRevenueFullscreenAdImpressedOnNewUserFirstDayAfterInstalledAdjustTracked,
                false
            )
        ) {
            sharedPreference.putBoolean(
                Key.HighRevenueFullscreenAdImpressedOnNewUserFirstDayAfterInstalledAdjustTracked,
                true
            )
            val adjustEvent = AdjustEvent(token)
            adjustEvent.setRevenue(revenue, currencyCode)
            Adjust.trackEvent(adjustEvent)
        }
    }
}

fun AdPaidInfo.trackAdjustEventWhenHighRevenueFullscreenAdImpressedOnNewUserNextDayAfterInstalled() {
    if (PoseidonSetupProcessor.getInstance().inNewUserNextDayAfterInstalled()) {
        val token = AdjustEventTokenProvider.highValueNextDayToken() ?: return
        val highRevenueInEcpmUnit = highRevenueInEcpmUnit()

        val sharedPreference = PoseidonSetupProcessor.getInstance().sharedPreference
        if (revenue > (highRevenueInEcpmUnit / 1000) && !sharedPreference.getBoolean(
                Key.HighRevenueFullscreenAdImpressedOnNewUserNextDayAfterInstalledAdjustTracked,
                false
            )
        ) {
            sharedPreference.putBoolean(
                Key.HighRevenueFullscreenAdImpressedOnNewUserNextDayAfterInstalledAdjustTracked,
                true
            )
            val adjustEvent = AdjustEvent(token)
            adjustEvent.setRevenue(revenue, currencyCode)
            Adjust.trackEvent(adjustEvent)
        }
    }
}

internal suspend fun AdPaidInfo.trackHighRevenueNewUserEventsWhenTimelineReady() {
    val setupProcessor = PoseidonSetupProcessor.getInstance()
    if (setupProcessor.awaitNewUserFirstOpenTime() == null) {
        return
    }
    trackAdjustEventWhenHighRevenueFullscreenAdImpressedOnNewUserFirstDayAfterInstalled()
    trackAdjustEventWhenHighRevenueFullscreenAdImpressedOnNewUserNextDayAfterInstalled()
}

fun AdPaidInfo.traceFirebaseAdImpressionCumulativeRevenueGreatThan0dot01() {
    val sharedPreference = PoseidonSetupProcessor.getInstance().sharedPreference
    var acc = sharedPreference.getDouble(Key.AdImpressionCumulativeRevenueGreatThan0dot01, 0.0)
    acc += revenue
    if (acc >= 0.01) {
        AdRevenueAccumulationTracker(
            revenue = acc,
            currency = currencyCode
        ).analyze()
        sharedPreference.putDouble(Key.AdImpressionCumulativeRevenueGreatThan0dot01, 0.0)
    } else {
        sharedPreference.putDouble(Key.AdImpressionCumulativeRevenueGreatThan0dot01, acc)
    }
}

@Suppress("SpellCheckingInspection")
private fun highRevenueInEcpmUnit(): Double {
    return PoseidonSetupProcessor.getInstance().remoteConfig.getString("high_revenue_in_ecpm_unit")
        .takeUnless(String::isNullOrEmpty)?.toDoubleOrNull() ?: 80.0
}

sealed class Precision(val value: Int)

/**
 * Ad value is unknown. When the lifetime value is pinged but the system doesn't have enough available data.
 */
data object UNKNOWN : Precision(value = 0)

/**
 * Ad value is estimated based on aggregated data.
 */
data object ESTIMATED : Precision(value = 1)

/**
 * The exact value you paid for this ad.
 */
data object PRECISE : Precision(value = 3)

/**
 * Ad value is provided by the publisher, e.g. the manual price for per thousand impressions in a mediated ad group.
 */
@Suppress("ClassName")
data object PUBLISHER_PROVIDED : Precision(value = 4)
