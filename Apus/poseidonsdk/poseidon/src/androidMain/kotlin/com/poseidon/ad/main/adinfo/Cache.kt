package com.poseidon.ad.main.adinfo

import androidx.collection.MutableIntObjectMap
import com.poseidon.ad.common.toolkit.TimeHelper
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.logWarn
import com.poseidon.ad.common.toolkit.runOnMainThread
import com.poseidon.ad.main.strategy.common.PlacementCfg
import com.poseidon.ad.main.strategy.common.StrategyCfg
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

private val adUnitIdToCacheAdsMap: MutableMap<String, MutableList<CacheAd<*>>> = hashMapOf()
private val adUnitIdToCacheAdsMapLock: ReentrantLock = ReentrantLock()
private val strategyIdToAdUnitsMap: MutableIntObjectMap<List<PlacementCfg>> = MutableIntObjectMap()
private val strategyIdToAdUnitsMapLock: ReentrantLock = ReentrantLock()

private const val TAG = "Cache"

internal fun callOnAdLoadStart(strategy: StrategyCfg) {
    strategyIdToAdUnitsMapLock.withLock {
        strategyIdToAdUnitsMap.put(strategy.id, strategy.placements)
    }
}

internal fun hasCacheAd(adUnitId: String, number: Int?): Boolean {
    return adUnitIdToCacheAdsMapLock.withLock {
        val cacheAds = adUnitIdToCacheAdsMap[adUnitId] ?: return@withLock false
        cacheAds.evictExpiredCacheAds(reason = "strategy cache hasCacheAd(adUnitId=$adUnitId)")
        if (cacheAds.isEmpty()) {
            adUnitIdToCacheAdsMap.remove(adUnitId)
            return@withLock false
        }
        cacheAds.any { number == null || it.number == number }
    }
}

internal fun hasCacheAd(strategyId: Int, number: Int?): Boolean {
    val adUnits = strategyIdToAdUnitsMapLock.withLock {
        strategyIdToAdUnitsMap[strategyId]
    }

    if (adUnits.isNullOrEmpty()) {
        return false
    }

    return adUnits.asSequence().map(PlacementCfg::id)
        .map { adUnitId ->
            adUnitIdToCacheAdsMapLock.withLock {
                val cacheAds = adUnitIdToCacheAdsMap[adUnitId] ?: return@withLock null
                cacheAds.evictExpiredCacheAds(reason = "strategy cache hasCacheAd(strategyId=$strategyId)")
                if (cacheAds.isEmpty()) {
                    adUnitIdToCacheAdsMap.remove(adUnitId)
                    null
                } else {
                    cacheAds.toList()
                }
            }
        }.filterNotNull()
        .flatten()
        .any { number == null || it.number == number }

}

internal fun <T : AdBase> addCacheAd(cacheAd: CacheAd<out T>) {
    adUnitIdToCacheAdsMapLock.withLock {
        adUnitIdToCacheAdsMap.getOrPut(cacheAd.adUnitId) { arrayListOf() }
            .add(cacheAd)
    }
}

internal fun consumeCacheAd(strategyId: Int): CacheAd<out AdBase>? {
    val adUnits = strategyIdToAdUnitsMapLock.withLock {
        strategyIdToAdUnitsMap[strategyId]
    }
    if (adUnits.isNullOrEmpty()) {
        return null
    }

    return adUnitIdToCacheAdsMapLock.withLock {
        val readyCandidates = adUnits.asSequence()
            .map(PlacementCfg::id)
            .mapNotNull { adUnitId ->
                val cacheAds = adUnitIdToCacheAdsMap[adUnitId] ?: return@mapNotNull null
                cacheAds.evictExpiredCacheAds(reason = "strategy cache consumeCacheAd(strategyId=$strategyId)")
                if (cacheAds.isEmpty()) {
                    adUnitIdToCacheAdsMap.remove(adUnitId)
                    null
                } else {
                    cacheAds
                }
            }
            .flatten()
            .filter { it.isReady() }
            .toList()

        val threshold = readHighEcpmThreshold()
        val winner = selectTopCacheAd(readyCandidates, threshold)

        logInfo(TAG) {
            "consumeCacheAd: strategyId=$strategyId, adUnits=$adUnits, candidates=${
                readyCandidates.map { "${it}:${it.ad.eCPM()}" }.joinToString(",")
            }, winner=${winner?.let { "${it}:${it.ad.eCPM()}" }}"
        }

        winner?.also { cacheAd ->
            adUnitIdToCacheAdsMap[cacheAd.adUnitId]?.remove(cacheAd)
        }
    }
}


internal data class CacheAd<T>(
    val ad: T,
    val adUnitId: String,
    val loadSuccessTimeInMillis: Long,
    val number: Int
) : Comparable<CacheAd<out AdBase>> where T : AdBase {

    val cachedEcpm: Double? = ad.eCPM()
    @AdTypeDef val cachedAdType: Int = ad.getAdType()
    val cachedAdSourceId: Int = ad.getAdSourceId()

    fun isReady(): Boolean {
        return ad.isReady()
    }

    override fun compareTo(other: CacheAd<out AdBase>): Int {
        return compareCacheAdRank(
            leftEcpm = cachedEcpm,
            rightEcpm = other.cachedEcpm,
            leftNumber = number,
            rightNumber = other.number,
            leftAdType = cachedAdType,
            rightAdType = other.cachedAdType,
            leftAdSourceId = cachedAdSourceId,
            rightAdSourceId = other.cachedAdSourceId,
            threshold = readHighEcpmThreshold(),
        )
    }
}

internal fun CacheAd<*>.isExpired(): Boolean {
    val currentTimeInMillis = TimeHelper.currentTimeInMillis()
    return currentTimeInMillis - loadSuccessTimeInMillis > 3000000L // 50 min
}

internal fun <T : CacheAd<*>> MutableList<T>.evictExpiredCacheAds(reason: String): List<T> {
    val expired = mutableListOf<T>()
    val iterator = iterator()
    while (iterator.hasNext()) {
        val cacheAd = iterator.next()
        if (cacheAd.isExpired()) {
            iterator.remove()
            expired.add(cacheAd)
        }
    }
    expired.forEach { cacheAd ->
        cacheAd.destroySafely(reason = reason)
    }
    if (expired.isNotEmpty()) {
        logInfo(TAG) { "Evicted ${expired.size} expired cache ad(s): reason=$reason" }
    }
    return expired
}

internal fun CacheAd<*>.destroySafely(reason: String) {
    runOnMainThread {
        runCatching {
            ad.destroy()
        }.onFailure { throwable ->
            logWarn(throwable = throwable, tag = TAG) {
                "Failed to destroy cache ad during $reason: adUnitId=$adUnitId, number=$number, adType=$cachedAdType, adSourceId=$cachedAdSourceId"
            }
        }
    }
}

internal fun isBelowEcpmBar(cachedEcpm: Double?, minEcpmToKeep: Double): Boolean {
    return cachedEcpm == null || cachedEcpm < minEcpmToKeep
}

internal fun <T : CacheAd<*>> MutableList<T>.evictCacheAdsBelowEcpm(
    minEcpmToKeep: Double,
    reason: String,
): List<T> {
    val evicted = mutableListOf<T>()
    val iterator = iterator()
    while (iterator.hasNext()) {
        val cacheAd = iterator.next()
        if (isBelowEcpmBar(cacheAd.cachedEcpm, minEcpmToKeep)) {
            iterator.remove()
            evicted.add(cacheAd)
        }
    }
    evicted.forEach { cacheAd ->
        cacheAd.destroySafely(reason = reason)
    }
    if (evicted.isNotEmpty()) {
        logInfo(TAG) { "Evicted ${evicted.size} cache ad(s) below eCPM bar $minEcpmToKeep: reason=$reason" }
    }
    return evicted
}

internal fun evictAllStrategyCacheAdsBelowEcpm(minEcpmToKeep: Double, reason: String): Int {
    return adUnitIdToCacheAdsMapLock.withLock {
        var destroyed = 0
        val iterator = adUnitIdToCacheAdsMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            destroyed += entry.value.evictCacheAdsBelowEcpm(minEcpmToKeep, reason).size
            if (entry.value.isEmpty()) {
                iterator.remove()
            }
        }
        destroyed
    }
}

internal fun evictAllExpiredStrategyCacheAds(reason: String) {
    adUnitIdToCacheAdsMapLock.withLock {
        val iterator = adUnitIdToCacheAdsMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.evictExpiredCacheAds(reason = reason)
            if (entry.value.isEmpty()) {
                iterator.remove()
            }
        }
    }
}
