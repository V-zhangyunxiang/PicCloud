package com.poseidon.ad.mediation.admob

import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo
import com.google.common.base.MoreObjects
import com.poseidon.ad.main.adinfo.AdSourceInfo
import com.poseidon.ad.main.adinfo.normalizeAdmobPlacementId
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Admob
import com.poseidon.ad.main.price.AD_SOURCE_ID_Applovin
import com.poseidon.ad.main.price.AD_SOURCE_ID_Bidmachine
import com.poseidon.ad.main.price.AD_SOURCE_ID_Bigo
import com.poseidon.ad.main.price.AD_SOURCE_ID_Facebook
import com.poseidon.ad.main.price.AD_SOURCE_ID_Fyber
import com.poseidon.ad.main.price.AD_SOURCE_ID_Mintegral
import com.poseidon.ad.main.price.AD_SOURCE_ID_Pangle
import com.poseidon.ad.main.price.AD_SOURCE_ID_UnityAds
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AD_SOURCE_ID_Vungle
import com.poseidon.ad.main.price.AdPaidInfo
import com.poseidon.ad.main.price.ESTIMATED
import com.poseidon.ad.main.price.PRECISE
import com.poseidon.ad.main.price.PUBLISHER_PROVIDED
import com.poseidon.ad.main.price.UNKNOWN
import java.math.BigDecimal
import java.math.RoundingMode

internal fun AdValue.contentToString(): String {
    return MoreObjects.toStringHelper(this)
        .add("valueMicros", valueMicros)
        .add("precisionType", precisionType)
        .add("currencyCode", currencyCode)
        .toString()
}

internal fun resolveAdPaidInfo(
    responseInfo: ResponseInfo?,
    adValue: AdValue,
    loadParameter: Parameter
): AdPaidInfo {
    // Extract the impression-level ad revenue data.
    val valueMicros = adValue.valueMicros
    val currencyCode = adValue.currencyCode
    val precision = adValue.precisionType
    val adSourceInfo = responseInfo.resolveAdSourceInfo()
    val revenue = BigDecimal(valueMicros)
        .divide(BigDecimal(1_000_000), 6, RoundingMode.HALF_UP)
        .toDouble()
    return AdPaidInfo(
        revenue = revenue,
        eCPM = revenue * 1000,
        currencyCode = currencyCode,
        precision = when (precision) {
            AdValue.PrecisionType.ESTIMATED -> ESTIMATED
            AdValue.PrecisionType.PUBLISHER_PROVIDED -> PUBLISHER_PROVIDED
            AdValue.PrecisionType.PRECISE -> PRECISE
            AdValue.PrecisionType.UNKNOWN -> UNKNOWN
            else -> UNKNOWN
        },
        networkId = loadParameter.network,
        adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
        adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
        adUnitId = loadParameter.adUnitId,
        adSourceName = adSourceInfo?.adSourceName
    )
}

fun ResponseInfo?.resolveAdSourceInfo(): AdSourceInfo? {
    if (this == null) return null
    val loadedAdapterResponseInfo = loadedAdapterResponseInfo ?: return null
    val adSourceId = loadedAdapterResponseInfo.adSourceId
    if (adSourceId.isEmpty()) return null
    val adSourceInstanceId = loadedAdapterResponseInfo.adSourceInstanceId
    val credentials = loadedAdapterResponseInfo.credentials
    val adSourceName = loadedAdapterResponseInfo.adSourceName
    return when (adSourceId) {
        "1063618907739174004"/*Applovin*/, "1328079684332308356"/*Applovin (bidding)*/ -> {
            AdSourceInfo(
                adSourceId = AD_SOURCE_ID_Applovin,
                adSourcePlacementId = credentials.getString("zone_id").orEmpty(),
                adSourceName = adSourceName,
                rawSourceId = adSourceId,
                rawSourceInstanceId = adSourceInstanceId
            )
        }

        "1953547073528090325"/*Liftoff Monetize*/, "4692500501762622185"/*Liftoff Monetize (bidding)*/ -> {
            AdSourceInfo(
                adSourceId = AD_SOURCE_ID_Vungle,
                adSourcePlacementId = credentials.getString("placementID").orEmpty(),
                adSourceName = adSourceName,
                rawSourceId = adSourceId,
                rawSourceInstanceId = adSourceInstanceId
            )
        }

        "10568273599589928883"/*Meta Audience Network*/, "11198165126854996598"/*Meta Audience Network (bidding)*/ -> {
            AdSourceInfo(
                adSourceId = AD_SOURCE_ID_Facebook,
                adSourcePlacementId = credentials.getString("placement_id").orEmpty(),
                adSourceName = adSourceName,
                rawSourceId = adSourceId,
                rawSourceInstanceId = adSourceInstanceId
            )
        }

        "5450213213286189855"/*AdMob Network*/, "1215381445328257950"/*AdMob Network Waterfall*/, "7068401028668408324"/*Reservation campaign, admob test ID use it*/ -> {
            AdSourceInfo(
                adSourceId = AD_SOURCE_ID_Admob,
                adSourcePlacementId = normalizeAdmobPlacementId(credentials.getString("pubid")),
                adSourceName = adSourceName,
                rawSourceId = adSourceId,
                rawSourceInstanceId = adSourceInstanceId
            )
        }

        "1357746574408896200"/*Mintegral*/, "6250601289653372374"/*Mintegral (bidding)*/ -> {
            // TODO need to confirm adSourcePlacementId
            AdSourceInfo(
                adSourceId = AD_SOURCE_ID_Mintegral,
                adSourcePlacementId = credentials.toString(),
                adSourceName = adSourceName,
                rawSourceId = adSourceId,
                rawSourceInstanceId = adSourceInstanceId
            )
        }

        "4970775877303683148"/*Unity Ads*/, "7069338991535737586"/*Unity Ads (bidding)*/ -> {
            AdSourceInfo(
                adSourceId = AD_SOURCE_ID_UnityAds,
                adSourcePlacementId = credentials.getString("zoneId").orEmpty(),
                adSourceName = adSourceName,
                rawSourceId = adSourceId,
                rawSourceInstanceId = adSourceInstanceId
            )
        }

        "2179455223494392917"/*DT Exchange*/, "8189833498765234879"/*Fyber*/, "4839637394546996422"/*Fyber*/ -> {
            // TODO need to confirm adSourcePlacementId
            AdSourceInfo(
                adSourceId = AD_SOURCE_ID_Fyber,
                adSourcePlacementId = credentials.toString(),
                adSourceName = adSourceName,
                rawSourceId = adSourceId,
                rawSourceInstanceId = adSourceInstanceId
            )
        }

        "504039719704837539"/*Bigo Ads SDK (bidding)*/ -> {
            AdSourceInfo(
                adSourceId = AD_SOURCE_ID_Bigo,
                adSourcePlacementId = credentials.getString("slot_id").orEmpty(),
                adSourceName = adSourceName,
                rawSourceId = adSourceId,
                rawSourceInstanceId = adSourceInstanceId
            )
        }

        "7943972370566394673"/*BidMachine (bidding)*/ -> {
            AdSourceInfo(
                adSourceId = AD_SOURCE_ID_Bidmachine,
                adSourcePlacementId = credentials.getString("placement_id").orEmpty(),
                adSourceName = adSourceName,
                rawSourceId = adSourceId,
                rawSourceInstanceId = adSourceInstanceId
            )
        }

        "4069896914521993236", "12171279046073404914", "3525379893916449117", "15999446638585856012"/*Pangle*/ -> {
            AdSourceInfo(
                adSourceId = AD_SOURCE_ID_Pangle,
                adSourcePlacementId = credentials.getString("placementid").orEmpty(),
                adSourceName = adSourceName,
                rawSourceId = adSourceId,
                rawSourceInstanceId = adSourceInstanceId
            )
        }

        else -> {
//            credentials.keySet().forEach { key ->
//                println("AdmobUtil =>$key = ${credentials.get(key)}")
//            }
//            Log.d("AdmobUtil", "resolveAdSourceInfo failed: $credentials $adSourceName $adSourceId $adSourceInstanceId")
            null
        }
    }
}
