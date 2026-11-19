package com.poseidon.ad.mediation.topon

import android.util.Base64
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn
import com.poseidon.ad.main.price.AD_SOURCE_ID_Admob
import com.thinkup.core.api.TUAdInfo
import com.thinkup.interstitial.api.TUInterstitial
import com.thinkup.rewardvideo.api.TURewardVideoAd
import com.thinkup.splashad.api.TUSplashAd
import org.json.JSONObject
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TAG = "TopOnAdmobExtractor"
private const val TRACKING_INFO_QUERY_ARG = 1

private val BASE_ADAPTER_FIELD_NAME: String by lazy {
    String(Base64.decode("bUJhc2VBZGFwdGVy", Base64.NO_WRAP))
}
private val TRACKING_INFO_FIELD_NAME: String by lazy {
    String(Base64.decode("bVRyYWNraW5nSW5mbw==", Base64.NO_WRAP))
}
private val BID_PRICE_KEY: String by lazy {
    String(Base64.decode("YmlkcHJpY2U=", Base64.NO_WRAP))
}

/**
 * Extracts the AdMob bid price hidden in TopOn's valid-ad cache.
 *
 * TopOn may keep more than one AdMob cache entry. Every matching entry is
 * inspected and the highest bid is selected, matching the cache-selection
 * behavior used by the source integration. Reflection must stay off the main
 * thread; callers are responsible for dispatching it to a worker thread.
 */
internal object TopOnAdmobExtractor {

    enum class BidSelectionPolicy {
        MAX_CACHE_BIDPRICE,
        NO_BID_FOUND
    }

    private data class CacheBidCandidate(
        val index: Int,
        val bidPrice: Double
    )

    private data class TrackingBidInspection(
        val methodName: String,
        val rawBidPrice: String,
        val revenuePerImpression: Double,
        val ecpm: Double
    )

    private data class CacheBidInspection(
        val index: Int,
        val descriptor: String,
        val status: String,
        val reason: String? = null,
        val tracking: TrackingBidInspection? = null
    )

    data class BidExtractionResult(
        val winnerBidPrice: Double?,
        val winnerEcpm: Double?,
        val winnerIndex: Int?,
        val totalCaches: Int,
        val matchingCaches: Int,
        val allInspections: List<Map<String, Any?>>
    )

    fun extractAdmobBidPriceWithDetails(topOnInterstitial: TUInterstitial): BidExtractionResult {
        return extractBidPriceFromCachesWithDetails(
            caches = topOnInterstitial.checkValidAdCaches(),
            firmId = AD_SOURCE_ID_Admob,
            networkFirmIdOf = { (it as TUAdInfo).networkFirmId }
        )
    }

    fun extractAdmobBidPriceWithDetails(topOnRewardedAd: TURewardVideoAd): BidExtractionResult {
        return extractBidPriceFromCachesWithDetails(
            caches = topOnRewardedAd.checkValidAdCaches(),
            firmId = AD_SOURCE_ID_Admob,
            networkFirmIdOf = { (it as TUAdInfo).networkFirmId }
        )
    }

    fun extractAdmobBidPriceWithDetails(topOnOpenAd: TUSplashAd): BidExtractionResult {
        return extractBidPriceFromCachesWithDetails(
            caches = topOnOpenAd.checkValidAdCaches(),
            firmId = AD_SOURCE_ID_Admob,
            networkFirmIdOf = { (it as TUAdInfo).networkFirmId }
        )
    }

    internal fun extractBidPriceFromCachesWithDetails(
        caches: List<*>?,
        firmId: Int,
        networkFirmIdOf: (Any) -> Int
    ): BidExtractionResult {
        return runCatching {
            val allCaches = caches.orEmpty()
            val inspections = mutableListOf<CacheBidInspection>()
            val candidates = mutableListOf<CacheBidCandidate>()

            allCaches.forEachIndexed { index, cacheInfo ->
                if (cacheInfo == null) {
                    inspections += CacheBidInspection(
                        index = index,
                        descriptor = "cacheIndex=$index, cacheClass=null",
                        status = "skipped",
                        reason = "cache entry is null"
                    )
                    return@forEachIndexed
                }

                val descriptor = describeCache(index = index, cacheInfo = cacheInfo)
                if (networkFirmIdOf(cacheInfo) != firmId) {
                    inspections += CacheBidInspection(
                        index = index,
                        descriptor = descriptor,
                        status = "filtered_out",
                        reason = "networkFirmId != $firmId"
                    )
                    return@forEachIndexed
                }

                val adapter = resolveAdapter(cacheInfo)
                if (adapter == null) {
                    inspections += CacheBidInspection(
                        index = index,
                        descriptor = descriptor,
                        status = "skipped",
                        reason = "adapter not found"
                    )
                    return@forEachIndexed
                }

                val tracking = inspectBidPriceFromAdapter(adapter)
                if (tracking == null) {
                    inspections += CacheBidInspection(
                        index = index,
                        descriptor = descriptor,
                        status = "skipped",
                        reason = "tracking bidprice missing"
                    )
                    return@forEachIndexed
                }

                inspections += CacheBidInspection(
                    index = index,
                    descriptor = descriptor,
                    status = "candidate",
                    tracking = tracking
                )
                candidates += CacheBidCandidate(index = index, bidPrice = tracking.revenuePerImpression)
            }

            val winner = candidates.maxByOrNull { it.bidPrice }
            inspections.forEach(::logInspection)
            logInfo(TAG) {
                buildString {
                    append("AdMob cache bid extraction summary: totalCaches=${allCaches.size}, ")
                    append("matchingCandidates=${candidates.size}")
                    if (winner == null) {
                        append(", selectedCacheIndex=null, selectedBidPrice=null, selectedEcpm=null")
                    } else {
                        append(", selectedCacheIndex=${winner.index}")
                        append(", selectedBidPrice=${winner.bidPrice}")
                        append(", selectedEcpm=${winner.bidPrice * 1000.0}")
                    }
                }
            }

            BidExtractionResult(
                winnerBidPrice = winner?.bidPrice,
                winnerEcpm = winner?.bidPrice?.times(1000.0),
                winnerIndex = winner?.index,
                totalCaches = allCaches.size,
                matchingCaches = candidates.size,
                allInspections = inspections.map { inspection ->
                    mapOf(
                        "index" to inspection.index,
                        "status" to inspection.status,
                        "reason" to inspection.reason,
                        "method" to inspection.tracking?.methodName,
                        "rawBidPrice" to inspection.tracking?.rawBidPrice,
                        "revenuePerImpression" to inspection.tracking?.revenuePerImpression,
                        "ecpm" to inspection.tracking?.ecpm,
                        "descriptor" to inspection.descriptor
                    )
                }
            )
        }.onFailure { throwable ->
            logWarn(throwable = throwable, tag = TAG) {
                "Failed to extract bidprice from TopOn cache, reason=${throwable.message}"
            }
        }.getOrElse {
            BidExtractionResult(
                winnerBidPrice = null,
                winnerEcpm = null,
                winnerIndex = null,
                totalCaches = 0,
                matchingCaches = 0,
                allInspections = emptyList()
            )
        }
    }

    private fun resolveAdapter(cacheInfo: Any): Any? {
        findFieldByName(cacheInfo.javaClass, BASE_ADAPTER_FIELD_NAME)?.let { adapterField ->
            adapterField.isAccessible = true
            adapterField.get(cacheInfo)?.let { adapter ->
                if (isLikelyAdapterCandidate(adapter)) return adapter
            }
        }
        return scanObjectFields(cacheInfo.javaClass, cacheInfo)
            .firstOrNull(::isLikelyAdapterCandidate)
    }

    private fun isLikelyAdapterCandidate(candidate: Any): Boolean {
        return findFieldByName(candidate.javaClass, TRACKING_INFO_FIELD_NAME) != null ||
            containsJsonTrackingMethod(candidate)
    }

    private fun inspectBidPriceFromAdapter(adapter: Any): TrackingBidInspection? {
        val trackingInfo = findFieldByName(adapter.javaClass, TRACKING_INFO_FIELD_NAME)?.let { field ->
            field.isAccessible = true
            field.get(adapter)
        } ?: return null
        return inspectBidPriceFromTrackingInfo(trackingInfo)
    }

    private fun inspectBidPriceFromTrackingInfo(trackingInfo: Any): TrackingBidInspection? {
        for (method in declaredMethodsOf(trackingInfo.javaClass)) {
            if (method.returnType != JSONObject::class.java) continue
            val inspection = runCatching {
                method.isAccessible = true
                val result = when (method.parameterTypes.size) {
                    0 -> method.invoke(trackingInfo)
                    1 -> method.invoke(trackingInfo, TRACKING_INFO_QUERY_ARG)
                    else -> null
                } as? JSONObject
                val rawBidPrice = result?.optString(BID_PRICE_KEY)
                    ?.takeIf(String::isNotEmpty)
                    ?: return@runCatching null
                val ecpm = rawBidPrice.toDoubleOrNull() ?: return@runCatching null
                TrackingBidInspection(
                    methodName = method.name,
                    rawBidPrice = rawBidPrice,
                    revenuePerImpression = ecpm / 1000.0,
                    ecpm = ecpm
                )
            }.getOrNull()
            if (inspection != null) return inspection
        }
        return null
    }

    private fun describeCache(index: Int, cacheInfo: Any): String {
        val adInfo = cacheInfo as? TUAdInfo
        return if (adInfo == null) {
            "cacheIndex=$index, cacheClass=${cacheInfo.javaClass.name}"
        } else {
            "networkFirmId=${adInfo.networkFirmId}, networkName=${adInfo.networkName}, " +
                "networkPlacementId=${adInfo.networkPlacementId}, ecpmPrecision=${adInfo.ecpmPrecision}, " +
                "publisherRevenue=${adInfo.publisherRevenue}, cacheClass=${cacheInfo.javaClass.name}"
        }
    }

    private fun logInspection(inspection: CacheBidInspection) {
        logInfo(TAG) {
            buildString {
                append("AdMob cache[${inspection.index}] inspection: status=${inspection.status}")
                inspection.reason?.let { append(", reason=$it") }
                inspection.tracking?.let { tracking ->
                    append(", method=${tracking.methodName}")
                    append(", rawBidPrice=${tracking.rawBidPrice}")
                    append(", revenuePerImpression=${tracking.revenuePerImpression}")
                    append(", ecpm=${tracking.ecpm}")
                }
                append(", ${inspection.descriptor}")
            }
        }
    }

    private fun containsJsonTrackingMethod(candidate: Any): Boolean {
        return declaredMethodsOf(candidate.javaClass).any { method ->
            method.returnType == JSONObject::class.java && method.parameterTypes.size <= 1
        }
    }

    private fun declaredMethodsOf(ownerClass: Class<*>): Sequence<Method> = sequence {
        var currentClass: Class<*>? = ownerClass
        while (currentClass != null && currentClass != Any::class.java) {
            yieldAll(currentClass.declaredMethods.asSequence())
            currentClass = currentClass.superclass
        }
    }

    private fun scanObjectFields(ownerClass: Class<*>, host: Any): Sequence<Any> = sequence {
        var currentClass: Class<*>? = ownerClass
        while (currentClass != null && currentClass != Any::class.java) {
            for (field in currentClass.declaredFields) {
                if (field.type.isPrimitive || field.type == String::class.java) continue
                runCatching {
                    field.isAccessible = true
                    field.get(host)
                }.getOrNull()?.let { yield(it) }
            }
            currentClass = currentClass.superclass
        }
    }

    private fun findFieldByName(ownerClass: Class<*>, fieldName: String): Field? {
        var currentClass: Class<*>? = ownerClass
        while (currentClass != null && currentClass != Any::class.java) {
            runCatching { currentClass.getDeclaredField(fieldName) }.getOrNull()?.let { return it }
            currentClass = currentClass.superclass
        }
        return null
    }
}
