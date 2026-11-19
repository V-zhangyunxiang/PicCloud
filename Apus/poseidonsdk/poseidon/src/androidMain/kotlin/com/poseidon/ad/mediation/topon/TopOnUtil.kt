package com.poseidon.ad.mediation.topon

import com.thinkup.core.api.TUAdInfo
import com.poseidon.ad.common.toolkit.logWarn
import com.poseidon.ad.main.adinfo.AdSourceInfo
import com.poseidon.ad.main.adinfo.normalizePlacementIdByAdSource
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Admob
import com.poseidon.ad.main.price.AdPaidInfo
import com.poseidon.ad.main.price.ESTIMATED
import com.poseidon.ad.main.price.PRECISE
import com.poseidon.ad.main.price.PUBLISHER_PROVIDED
import com.poseidon.ad.main.price.Precision
import com.poseidon.ad.main.price.UNKNOWN
import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import org.json.JSONObject
import java.lang.reflect.Field
import java.math.BigDecimal
import java.math.RoundingMode

private const val precision_type = "precision_type"
private const val currency_code = "currency_code"
private const val value_micros = "value_micros"

private const val TAG = "TopOnUtil"

internal fun TUAdInfo.toAdSourceInfo(): AdSourceInfo {
    return AdSourceInfo(
        adSourceId = networkFirmId,
        adSourcePlacementId = normalizePlacementIdByAdSource(
            adSourceId = networkFirmId,
            placementId = networkPlacementId
        ),
        adSourceName = networkName
    )
}

internal fun resolveAdPaidInfo(adInfo: TUAdInfo, loadParameter: Parameter): AdPaidInfo {
    val networkFirmId = adInfo.networkFirmId
    val networkName = adInfo.networkName
    var revenue = adInfo.resolveRevenue()
    var currency = adInfo.currency
    val networkPlacementId = normalizePlacementIdByAdSource(
        adSourceId = networkFirmId,
        placementId = adInfo.networkPlacementId
    )
    var eCPM: Double = revenue * 1000
    var precision: Precision = when (adInfo.ecpmPrecision) {
        "publisher_defined" -> PUBLISHER_PROVIDED
        "estimated" -> ESTIMATED
        "exact" -> PRECISE
        "ecpm_api" -> ESTIMATED
        else -> UNKNOWN
    }
    if (networkFirmId == AD_SOURCE_ID_Admob) {
        val extInfoMap = adInfo.extInfoMap
        extInfoMap?.takeIf { it.isNotEmpty() }?.takeIf {
            it.containsKey(precision_type)
                    && it.containsKey(currency_code)
                    && it.containsKey(value_micros)
        }?.runCatching {
            val admobPrecision = when (val precisionType = this[precision_type].toString().toIntOrNull()) {
                0 -> UNKNOWN
                1 -> ESTIMATED
                2 -> PUBLISHER_PROVIDED
                3 -> PRECISE
                else -> error("Unknown precision type: $precisionType")
            }
            val admobCurrency = this[currency_code].toString()
            val admobRevenue = BigDecimal(this[value_micros].toString().toLong())
                .divide(BigDecimal(1_000_000), 6, RoundingMode.HALF_UP)
                .toDouble()

            precision = admobPrecision
            currency = admobCurrency
            revenue = admobRevenue
            eCPM = revenue * 1000
        }?.onFailure { t ->
            logWarn(throwable = t, tag = TAG) {
                "Failed to parse admob extInfoMap: $extInfoMap, reason=${t.message}."
            }
            ErrorDetectionReporter(
                name = "ParseTopOnAdmobExtInfoMapFailed",
                extra = t.message ?: t.stackTrace.contentToString()
            ).analyze()
        }
    }
    return AdPaidInfo(
        revenue = revenue,
        eCPM = eCPM,
        currencyCode = currency,
        precision = precision,
        networkId = loadParameter.network,
        adSourceId = networkFirmId,
        adSourcePlacementId = networkPlacementId,
        adUnitId = loadParameter.adUnitId,
        adSourceName = networkName
    )
}

private fun TUAdInfo.resolveRevenue(): Double {
    fun fallback(atAdInfo: TUAdInfo): Double {
        try {
            // https://nowiki.apuscn.com/pages/viewpage.action?pageId=104666230
            for (field in atAdInfo.javaClass.declaredFields) {
                field.isAccessible = true
                val obj2 = field.get(atAdInfo)
                if (field.genericType.toString().contains("ATBaseAdAdapter")) {
                    var cls: Class<in Any>? = obj2?.javaClass
                    var field2: Field? = null
                    while (field2 == null) {
                        field2 = runCatching { cls?.getDeclaredField("mTrackingInfo") }.getOrNull()
                        if (field2 == null) {
                            cls = cls?.superclass
                        }
                        if (Any::class.java == cls) {
                            break
                        }
                    }
                    if (field2 != null) {
                        field2.isAccessible = true
                        val obj3 = field2.get(obj2)
                        for (method in obj3.javaClass.methods) {
                            method.isAccessible = true
                            if (JSONObject::class.java == method.returnType) {
                                try {
                                    val eCPM = (method.invoke(obj3, 1) as JSONObject).optString("bidprice")
                                    if (!eCPM.isNullOrEmpty()) {
                                        return eCPM.toDoubleOrNull()?.div(1000.0) ?: 0.0
                                    }
                                } catch (ignored: Throwable) {
                                }
                            }
                        }
                    }
                }
            }
            return 0.0
        } catch (ignored: Throwable) {
            return 0.0
        }
    }
    // TODO fallback has ANR
//    return publisherRevenue.takeIf { it > 0.0 } ?: fallback(atAdInfo = this)
    return publisherRevenue
}
