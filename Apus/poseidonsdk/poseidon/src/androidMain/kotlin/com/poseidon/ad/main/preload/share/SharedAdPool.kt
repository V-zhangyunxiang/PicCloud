package com.poseidon.ad.main.preload.share

import androidx.annotation.AnyThread
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.common.storage.sp.Key
import com.poseidon.ad.common.storage.sp.SpBatchEntry
import com.poseidon.ad.common.storage.sp.computeDailyCounterPlan
import com.poseidon.ad.common.toolkit.PoseidonJson
import com.poseidon.ad.common.toolkit.ResultCallback
import com.poseidon.ad.common.toolkit.StringHelper
import com.poseidon.ad.common.toolkit.TimeHelper
import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.common.toolkit.dispatchToBackground
import com.poseidon.ad.common.toolkit.isNetworkAvailable
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.main.adinfo.AD_TYPE_INTERSTITIAL
import com.poseidon.ad.main.adinfo.AD_TYPE_NATIVE
import com.poseidon.ad.main.adinfo.AD_TYPE_REWARDED
import com.poseidon.ad.main.adinfo.AdBase
import com.poseidon.ad.main.adinfo.AdTypeDef
import com.poseidon.ad.main.adinfo.CacheAd
import com.poseidon.ad.main.adinfo.InterstitialAdBase
import com.poseidon.ad.main.adinfo.NativeAdBase
import com.poseidon.ad.main.adinfo.RewardedAdBase
import com.poseidon.ad.main.adinfo.evictCacheAdsBelowEcpm
import com.poseidon.ad.main.adinfo.evictExpiredCacheAds
import com.poseidon.ad.main.adinfo.getAdTypeName
import com.poseidon.ad.main.adinfo.readHighEcpmThreshold
import com.poseidon.ad.main.adinfo.selectTopCacheAd
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.InterstitialParameter
import com.poseidon.ad.main.load.NativeParameter
import com.poseidon.ad.main.load.RewardedParameter
import com.poseidon.ad.main.load.createInterstitialAdLoader
import com.poseidon.ad.main.load.createNativeAdLoader
import com.poseidon.ad.main.load.createRewardedAdLoader
import com.poseidon.ad.main.processor.PlatformInterstitialAdLoadParameter
import com.poseidon.ad.main.processor.PlatformNativeAdLoadParameter
import com.poseidon.ad.main.processor.PlatformRewardedAdLoadParameter
import com.poseidon.ad.main.processor.NightBackgroundPreloadGuard
import com.poseidon.ad.main.processor.PreloadMemoryGuard
import com.poseidon.ad.main.processor.isGlobalSwitchOn
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.main.strategy.common.SharedAdUnitCfg
import com.poseidon.ad.main.strategy.common.SharedCfg
import com.poseidon.ad.main.preload.new.NewUserAdPreloadGate
import com.poseidon.ad.mediation.MEDIATION_MAX
import com.poseidon.ad.restrict.show.AdShowLimit
import com.poseidon.ad.restrict.show.currentDateFormatWithYearMonthDay
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.encodeToString
import kotlin.math.max

internal abstract class SharedAdPool {

    abstract val name: String
    abstract val number: Int

    private val adLoadingAtomic = atomic(false)
    
    private val cacheAds: MutableList<CacheAd<out AdBase>> = mutableListOf()
    private val cacheAdsLock = reentrantLock()
    
    fun addAdToCache(ad: CacheAd<out AdBase>) {
        cacheAdsLock.withLock {
            cacheAds.add(ad)
        }
    }

    fun cacheCount(predicate: (CacheAd<out AdBase>) -> Boolean): Int {
        return cacheAdsLock.withLock {
            cacheAds.evictExpiredCacheAds(reason = "$name cacheCount")
            cacheAds.filter(predicate).size
        }
    }

    fun consumeCacheAd(@AdTypeDef vararg filteredAdTypes: Int): CacheAd<out AdBase>? {
        val threshold = readHighEcpmThreshold()
        return cacheAdsLock.withLock {
            cacheAds.evictExpiredCacheAds(reason = "$name consumeCacheAd")
            val candidates = cacheAds.filter { filteredAdTypes.contains(it.ad.getAdType()) && it.isReady() }
            selectTopCacheAd(candidates, threshold)
                ?.also { ad -> cacheAds.remove(ad) }
        }
    }

    @AnyThread
    fun preload() {
        logInfo(name) {
            "=========>preload<========="
        }
        if (!adLoadingAtomic.value) {
            val r = adLoadingAtomic.compareAndSet(expect = false, update = true)
            if (!r) {
                return
            }

            val globalSwitchOn = isGlobalSwitchOn()
            if (!globalSwitchOn) {
                logInfo(name) {
                    "Global Switch is off!"
                }
                adLoadingAtomic.compareAndSet(expect = true, update = false)
                return
            }

            evictExpiredAds()

            if (NightBackgroundPreloadGuard.shouldSkipPreload()) {
                NightBackgroundPreloadGuard.reportSkip(sceneName = name)
                adLoadingAtomic.compareAndSet(expect = true, update = false)
                return
            }

            if (PreloadMemoryGuard.shouldSkipPreload(PoseidonSetupProcessor.getInstance().application, sceneName = name)) {
                adLoadingAtomic.compareAndSet(expect = true, update = false)
                return
            }

            val networkAvailable = isNetworkAvailable()
            if (!networkAvailable) {
                logInfo(name) {
                    "Network not available!"
                }
                adLoadingAtomic.compareAndSet(expect = true, update = false)
                return
            }

            val canLoadAdByAdImpressionLimit = AdShowLimit.canLoadAd()
            if (!canLoadAdByAdImpressionLimit) {
                logInfo(name) {
                    "Ad Impression Limit!"
                }
                adLoadingAtomic.compareAndSet(expect = true, update = false)
                return
            }

            val sharedConfiguration = sharedConfiguration()
            val poseidonConfigVersion = sharedConfiguration?.first
            val shared = sharedConfiguration?.second
            if (shared == null) {
                logInfo(name) {
                    "Empty Shared Configuration!"
                }
                adLoadingAtomic.compareAndSet(expect = true, update = false)
                return
            }
            if (!shared.isSwitchOn()) {
                logInfo(name) {
                    "Switch is off!"
                }
                adLoadingAtomic.compareAndSet(expect = true, update = false)
                return
            }
            val adUnits = shared.adUnits.filter { it.isSwitchOn() }
            if (adUnits.isEmpty()) {
                logInfo(name) {
                    "Ad Units is empty!"
                }
                adLoadingAtomic.compareAndSet(expect = true, update = false)
                return
            }

            val resolvedCacheKey = shared.resolvedCacheKey
            if (resolvedCacheKey == CacheKey.Any && cacheCount { true } > 0) {
                logInfo(name) {
                    "[CacheKey=$resolvedCacheKey] Has Cache Ad!"
                }
                adLoadingAtomic.compareAndSet(expect = true, update = false)
                return
            }

            val undoneSize = atomic(adUnits.size)
            for (adUnit in adUnits) {
                if (resolvedCacheKey == CacheKey.AdType && cacheCount { it.ad.getAdType() == adUnit.adType } > 0) {
                    if (undoneSize.decrementAndGet() == 0) {
                        logInfo(name) {
                            "[CacheKey=$resolvedCacheKey] All ad unit load complete!"
                        }
                        adLoadingAtomic.compareAndSet(expect = true, update = false)
                    }
                    continue
                }

                if (resolvedCacheKey == CacheKey.AdUnitId && cacheCount { it.adUnitId == adUnit.id } > 0) {
                    if (undoneSize.decrementAndGet() == 0) {
                        logInfo(name) {
                            "[CacheKey=$resolvedCacheKey] All ad unit load complete!"
                        }
                        adLoadingAtomic.compareAndSet(expect = true, update = false)
                    }
                    continue
                }

                if (getAdConsecutiveLoadFailuresCount(adUnitId = adUnit.id) >= getMaxAdConsecutiveLoadFailuresCountFromRemote()) {
                    if (undoneSize.decrementAndGet() == 0) {
                        logInfo(name) {
                            "[CacheKey=$resolvedCacheKey] Ad(adUnitId=${adUnit.id}) has reach max consecutive load failures count!"
                        }
                        adLoadingAtomic.compareAndSet(expect = true, update = false)
                    }
                    continue
                }

                val maxRequestCountByIdDailyFromRemote = getMaxRequestCountByIdDailyFromRemote(adUnitId = adUnit.id)
                val requestCountById = getRequestCountById(adUnitId = adUnit.id)
                if (maxRequestCountByIdDailyFromRemote != null && maxRequestCountByIdDailyFromRemote > 0 && requestCountById >= maxRequestCountByIdDailyFromRemote) {
                    if (undoneSize.decrementAndGet() == 0) {
                        logInfo(name) {
                            "[CacheKey=$resolvedCacheKey] Ad(adUnitId=${adUnit.id}) has reach max request count($maxRequestCountByIdDailyFromRemote)!"
                        }
                        adLoadingAtomic.compareAndSet(expect = true, update = false)
                    }
                    continue
                }

                val sceneName = "${name}_${getAdTypeName(adUnit.adType)}"
                val reportSceneName = "${sceneName}_${adUnit.id}"
                if (PreloadMemoryGuard.shouldSkipPreload(PoseidonSetupProcessor.getInstance().application, sceneName = reportSceneName)) {
                    logInfo(name) {
                        "Skip shared ad unit preload by memory guard: sceneName=$reportSceneName, adUnit=$adUnit"
                    }
                    if (undoneSize.decrementAndGet() == 0) {
                        logInfo(name) {
                            "[CacheKey=$resolvedCacheKey] All ad unit load complete!"
                        }
                        adLoadingAtomic.compareAndSet(expect = true, update = false)
                    }
                    continue
                }
                loadSharedAdUnit(loadParameter = adUnit.asPlatformAdLoadParameter(sceneName), sharedAdUnit = adUnit, poseidonConfigVersion = poseidonConfigVersion) { isSuccessful ->
                    if (isSuccessful) {
                        setAdConsecutiveLoadFailuresCount(adUnitId = adUnit.id, count = 0)
                    } else {
                        increaseAdConsecutiveLoadFailuresCount(adUnitId = adUnit.id)
                    }
                    if (undoneSize.decrementAndGet() == 0) {
                        logInfo(name) {
                            "[CacheKey=$resolvedCacheKey] All ad unit load complete!"
                        }
                        adLoadingAtomic.compareAndSet(expect = true, update = false)
                    }
                }
            }
        } else {
            logInfo(name) {
                "Load is Running!"
            }
        }
    }

    private fun evictExpiredAds() {
        cacheAdsLock.withLock {
            cacheAds.evictExpiredCacheAds(reason = "$name preload")
        }
    }

    internal fun scavengeBelowEcpm(minEcpmToKeep: Double, reason: String): Int {
        return cacheAdsLock.withLock {
            cacheAds.evictCacheAdsBelowEcpm(minEcpmToKeep, reason = "$name $reason").size
        }
    }

    internal fun evictExpiredAdsNow(reason: String) {
        cacheAdsLock.withLock {
            cacheAds.evictExpiredCacheAds(reason = "$name $reason")
        }
    }

    fun isSharedAdScene(targetSceneName: String): Boolean {
        return targetSceneName.startsWith("${name}_")
    }

    private fun loadSharedAdUnit(
        loadParameter: Any,
        sharedAdUnit: SharedAdUnitCfg,
        poseidonConfigVersion: VersionHelper?,
        onComplete: (isSuccessful: Boolean) -> Unit
    ) {
        logInfo(name) {
            ">>>>>loadSharedAdUnit: loadParameter=$loadParameter, sharedAdUnit=$sharedAdUnit"
        }
        val adType = sharedAdUnit.adType
        when (adType) {
            AD_TYPE_INTERSTITIAL -> {
                val lp = loadParameter as PlatformInterstitialAdLoadParameter
                val network = sharedAdUnit.mediation
                val parameter = InterstitialParameter(
                    adUnitId = sharedAdUnit.id,
                    startLoadTimeInMillis = TimeHelper.currentTimeInMillis(),
                    network = network,
                    sessionId = StringHelper.newSessionId(),
                    sceneName = lp.sceneName,
                    poseidonConfigVersion = poseidonConfigVersion,
                )
                when (val result =
                    createInterstitialAdLoader(
                        network = network,
                        parameter = parameter
                    )) {
                    is ResultCallback.Failure -> {
                        onComplete(false)
                    }

                    is ResultCallback.Success -> {
                        val callback = object :
                            InnerAdLoadCallback<InterstitialAdBase> {
                            override fun onAdLoadFailed(code: Int, message: String) {
                                if (message.contains("No Fill") && network == MEDIATION_MAX) {
                                    decreaseRequestCountById(adUnitId = sharedAdUnit.id)
                                }
                                onComplete(false)
                            }

                            override fun onAdLoadSuccess(ad: InterstitialAdBase) {
                                val cacheAd = CacheAd(
                                    ad = ad,
                                    adUnitId = parameter.adUnitId,
                                    loadSuccessTimeInMillis = TimeHelper.currentTimeInMillis(),
                                    number = number,
                                )
                                addAdToCache(cacheAd)
                                onComplete(true)
                            }
                        }
                        val adLoader = result.value
                        increaseRequestCountById(adUnitId = sharedAdUnit.id)
                        adLoader.load(loadParameter = lp, loadCallback = callback)
                    }
                }
            }

            AD_TYPE_REWARDED -> {
                val lp = loadParameter as PlatformRewardedAdLoadParameter
                val network = sharedAdUnit.mediation
                val parameter = RewardedParameter(
                    adUnitId = sharedAdUnit.id,
                    startLoadTimeInMillis = TimeHelper.currentTimeInMillis(),
                    network = network,
                    sessionId = StringHelper.newSessionId(),
                    sceneName = lp.sceneName,
                    poseidonConfigVersion = poseidonConfigVersion,
                )
                when (val result =
                    createRewardedAdLoader(
                        network = network,
                        parameter = parameter
                    )) {
                    is ResultCallback.Failure -> {
                        onComplete(false)
                    }

                    is ResultCallback.Success -> {
                        val callback = object :
                            InnerAdLoadCallback<RewardedAdBase> {
                            override fun onAdLoadFailed(code: Int, message: String) {
                                if (message.contains("No Fill") && network == MEDIATION_MAX) {
                                    decreaseRequestCountById(adUnitId = sharedAdUnit.id)
                                }
                                onComplete(false)
                            }

                            override fun onAdLoadSuccess(ad: RewardedAdBase) {
                                val cacheAd = CacheAd(
                                    ad = ad,
                                    adUnitId = parameter.adUnitId,
                                    loadSuccessTimeInMillis = TimeHelper.currentTimeInMillis(),
                                    number = number,
                                )
                                addAdToCache(cacheAd)
                                onComplete(true)
                            }
                        }
                        val adLoader = result.value
                        increaseRequestCountById(adUnitId = sharedAdUnit.id)
                        adLoader.load(loadParameter = lp, loadCallback = callback)
                    }
                }
            }

            AD_TYPE_NATIVE -> {
                val lp = loadParameter as PlatformNativeAdLoadParameter
                val network = sharedAdUnit.mediation
                val parameter = NativeParameter(
                    adUnitId = sharedAdUnit.id,
                    startLoadTimeInMillis = TimeHelper.currentTimeInMillis(),
                    network = network,
                    sessionId = StringHelper.newSessionId(),
                    sceneName = lp.sceneName,
                    poseidonConfigVersion = poseidonConfigVersion,
                )
                when (val result = createNativeAdLoader(
                    network = network,
                    parameter = parameter
                )) {
                    is ResultCallback.Failure -> {
                        onComplete(false)
                    }
                    is ResultCallback.Success -> {
                        val callback = object :
                            InnerAdLoadCallback<NativeAdBase> {
                            override fun onAdLoadFailed(code: Int, message: String) {
                                if (message.contains("No Fill") && network == MEDIATION_MAX) {
                                    decreaseRequestCountById(adUnitId = sharedAdUnit.id)
                                }
                                onComplete(false)
                            }

                            override fun onAdLoadSuccess(ad: NativeAdBase) {
                                val cacheAd = CacheAd(
                                    ad = ad,
                                    adUnitId = parameter.adUnitId,
                                    loadSuccessTimeInMillis = TimeHelper.currentTimeInMillis(),
                                    number = number,
                                )
                                addAdToCache(cacheAd)
                                onComplete(true)
                            }
                        }
                        val adLoader = result.value
                        increaseRequestCountById(adUnitId = sharedAdUnit.id)
                        adLoader.load(loadParameter = lp, loadCallback = callback)
                    }
                }
            }

            else -> {
                // Not Support yet!
                onComplete(false)
            }
        }
    }

    private fun setAdConsecutiveLoadFailuresCount(adUnitId: String, count: Int) {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val json = sharedPreference.getString(Key.SharedAdConsecutiveLoadFailuresCountByAdUnitId, null)
        val m: MutableMap<String, Int> = if (json.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            runCatching { PoseidonJson.decodeFromString<MutableMap<String, Int>>(json) }
                .getOrDefault(mutableMapOf())
        }
        m[adUnitId] = count
        sharedPreference.putString(
            key = Key.SharedAdConsecutiveLoadFailuresCountByAdUnitId,
            value = runCatching { PoseidonJson.encodeToString(m) }.getOrNull())
    }

    private fun increaseAdConsecutiveLoadFailuresCount(adUnitId: String) {
        resetAdConsecutiveLoadFailuresCountIfNewDay(adUnitId = adUnitId)

        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val json = sharedPreference.getString(Key.SharedAdConsecutiveLoadFailuresCountByAdUnitId, null)
        val m: MutableMap<String, Int> = if (json.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            runCatching { PoseidonJson.decodeFromString<MutableMap<String, Int>>(json) }
                .getOrDefault(mutableMapOf())
        }
        m[adUnitId] = (m[adUnitId] ?: 0) + 1
        sharedPreference.putString(
            key = Key.SharedAdConsecutiveLoadFailuresCountByAdUnitId,
            value = runCatching { PoseidonJson.encodeToString(m) }.getOrNull())
    }

    private fun getAdConsecutiveLoadFailuresCount(adUnitId: String): Int {
        resetAdConsecutiveLoadFailuresCountIfNewDay(adUnitId = adUnitId)

        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val json = sharedPreference.getString(Key.SharedAdConsecutiveLoadFailuresCountByAdUnitId, null)
        val m: MutableMap<String, Int> = if (json.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            runCatching { PoseidonJson.decodeFromString<MutableMap<String, Int>>(json) }
                .getOrDefault(mutableMapOf())
        }
        return m[adUnitId] ?: 0
    }

    private fun resetAdConsecutiveLoadFailuresCountIfNewDay(adUnitId: String) {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val plan = computeDailyCounterPlan(
            savedDate = sharedPreference.getString(Key.SharedAdConsecutiveLoadFailuresCountByAdUnitIdLastDate, null),
            currentDate = currentDateFormatWithYearMonthDay(),
            savedCounter = 0,
            applyIncrement = false,
        )
        if (plan.needsPersistDate) {
            val resetJson = runCatching {
                val m: MutableMap<String, Int> = mutableMapOf()
                m[adUnitId] = 0
                PoseidonJson.encodeToString(m)
            }.getOrNull()
            sharedPreference.writeBatch(listOf(
                SpBatchEntry.StringValue(key = Key.SharedAdConsecutiveLoadFailuresCountByAdUnitIdLastDate, value = plan.dateValue),
                SpBatchEntry.StringValue(key = Key.SharedAdConsecutiveLoadFailuresCountByAdUnitId, value = resetJson),
            ))
        }
    }

    private fun getMaxAdConsecutiveLoadFailuresCountFromRemote(): Int {
        return PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig
            .getString("${name}_max_consecutive_load_failures_count")
            .toIntOrNull() ?: 6
    }

    private fun getMaxRequestCountByIdDailyFromRemote(adUnitId: String): Int? {
        return PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig
            .getString("shared_max_request_count_daily_of_$adUnitId")
            .toIntOrNull()
    }

    private fun increaseRequestCountById(adUnitId: String) {
        resetRequestCountById(adUnitId = adUnitId)

        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val json = sharedPreference.getString(Key.SharedAdRequestCountByAdUnitId, null)
        val m: MutableMap<String, Int> = if (json.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            runCatching { PoseidonJson.decodeFromString<MutableMap<String, Int>>(json) }
                .getOrDefault(mutableMapOf())
        }
        m[adUnitId] = (m[adUnitId] ?: 0) + 1
        sharedPreference.putString(
            key = Key.SharedAdRequestCountByAdUnitId,
            value = runCatching { PoseidonJson.encodeToString(m) }.getOrNull())
    }

    private fun decreaseRequestCountById(adUnitId: String) {
        resetRequestCountById(adUnitId = adUnitId)

        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val json = sharedPreference.getString(Key.SharedAdRequestCountByAdUnitId, null)
        val m: MutableMap<String, Int> = if (json.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            runCatching { PoseidonJson.decodeFromString<MutableMap<String, Int>>(json) }
                .getOrDefault(mutableMapOf())
        }
        m[adUnitId] = max(0, (m[adUnitId] ?: 0) - 1)
        sharedPreference.putString(
            key = Key.SharedAdRequestCountByAdUnitId,
            value = runCatching { PoseidonJson.encodeToString(m) }.getOrNull())
    }

    private fun setRequestCountById(adUnitId: String, count: Int) {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val json = sharedPreference.getString(Key.SharedAdRequestCountByAdUnitId, null)
        val m: MutableMap<String, Int> = if (json.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            runCatching { PoseidonJson.decodeFromString<MutableMap<String, Int>>(json) }
                .getOrDefault(mutableMapOf())
        }
        m[adUnitId] = count
        sharedPreference.putString(
            key = Key.SharedAdRequestCountByAdUnitId,
            value = runCatching { PoseidonJson.encodeToString(m) }.getOrNull())
    }

    private fun resetRequestCountById(adUnitId: String) {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val plan = computeDailyCounterPlan(
            savedDate = sharedPreference.getString(Key.SharedAdRequestCountByAdUnitIdLastDate, null),
            currentDate = currentDateFormatWithYearMonthDay(),
            savedCounter = 0,
            applyIncrement = false,
        )
        if (plan.needsPersistDate) {
            val resetJson = runCatching {
                val m: MutableMap<String, Int> = mutableMapOf()
                m[adUnitId] = 0
                PoseidonJson.encodeToString(m)
            }.getOrNull()
            sharedPreference.writeBatch(listOf(
                SpBatchEntry.StringValue(key = Key.SharedAdRequestCountByAdUnitIdLastDate, value = plan.dateValue),
                SpBatchEntry.StringValue(key = Key.SharedAdRequestCountByAdUnitId, value = resetJson),
            ))
        }
    }

    private fun getRequestCountById(adUnitId: String): Int {
        resetRequestCountById(adUnitId = adUnitId)

        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val json = sharedPreference.getString(Key.SharedAdRequestCountByAdUnitId, null)
        val m: MutableMap<String, Int> = if (json.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            runCatching { PoseidonJson.decodeFromString<MutableMap<String, Int>>(json) }
                .getOrDefault(mutableMapOf())
        }
        return m[adUnitId] ?: 0
    }

    protected abstract fun sharedConfiguration(): Pair<VersionHelper?, SharedCfg?>?
}

internal object SharedFloorAdPool : SharedAdPool() {
    override val name: String = "SharedFloorAdPool"
    override val number: Int = 1

    override fun sharedConfiguration(): Pair<VersionHelper?, SharedCfg?>? = PoseidonConfig.Companion.get()?.run { version to sharedFloor }
}

internal object SharedCeilAdPool : SharedAdPool() {
    override val name: String = "SharedCeilAdPool"
    override val number: Int = 3

    override fun sharedConfiguration(): Pair<VersionHelper?, SharedCfg?>? = PoseidonConfig.Companion.get()?.run { version to sharedCeil }
}

internal fun SharedAdUnitCfg.asPlatformAdLoadParameter(sceneName: String): Any {
    return when (adType) {
        AD_TYPE_INTERSTITIAL -> PlatformInterstitialAdLoadParameter(
            context = PoseidonSetupProcessor.getInstance().application,
            sceneName = sceneName
        )

        AD_TYPE_NATIVE -> PlatformNativeAdLoadParameter(
            context = PoseidonSetupProcessor.getInstance().application,
            sceneName = sceneName
        )

        AD_TYPE_REWARDED -> PlatformRewardedAdLoadParameter(
            context = PoseidonSetupProcessor.getInstance().application,
            sceneName = sceneName
        )

        else -> error("Unknown adType: $adType")
    }
}


internal enum class CacheKey(val id: Int) {
    Any(id = 1),
    AdType(id = 2),
    AdUnitId(id = 3)
}

private const val TAG = "SharedAdPool"

internal fun tryPreloadSharedAdWhenAdResponse(sceneName: String) {
    logInfo(TAG) {
        "tryPreloadSharedAdWhenAdResponse: sceneName=$sceneName"
    }
    if (NewUserAdPreloadGate.isOrdinaryPreloadBlocked()) {
        logInfo(TAG) { "Skip shared preload on ad response because ordinary preload is still gated" }
        return
    }
    dispatchToBackground {
        if (!SharedCeilAdPool.isSharedAdScene(targetSceneName = sceneName)) {
            SharedCeilAdPool.preload()
        }
        if (!SharedFloorAdPool.isSharedAdScene(targetSceneName = sceneName)) {
            SharedFloorAdPool.preload()
        }
    }
}

internal fun tryPreloadSharedAdCallShow(sceneName: String) {
    logInfo(TAG) {
        "tryPreloadSharedAdCallShow: sceneName=$sceneName"
    }
    if (NewUserAdPreloadGate.isOrdinaryPreloadBlocked()) {
        logInfo(TAG) { "Skip shared preload on show call because ordinary preload is still gated" }
        return
    }
    dispatchToBackground {
        SharedCeilAdPool.preload()
        SharedFloorAdPool.preload()
    }
}

internal fun tryPreloadSharedAdWhenAdImpression(sceneName: String) {
    logInfo(TAG) {
        "tryPreloadSharedAdWhenAdImpression: sceneName=$sceneName"
    }
    if (NewUserAdPreloadGate.isOrdinaryPreloadBlocked()) {
        logInfo(TAG) { "Skip shared preload on impression because ordinary preload is still gated" }
        return
    }
    dispatchToBackground {
        SharedCeilAdPool.preload()
        SharedFloorAdPool.preload()
    }
}

internal fun tryPreloadSharedAdWhenAdFailedToShow(sceneName: String) {
    logInfo(TAG) {
        "tryPreloadSharedAdWhenAdFailedToShow: sceneName=$sceneName"
    }
    if (NewUserAdPreloadGate.isOrdinaryPreloadBlocked()) {
        logInfo(TAG) { "Skip shared preload on failed show because ordinary preload is still gated" }
        return
    }
    dispatchToBackground {
        SharedCeilAdPool.preload()
        SharedFloorAdPool.preload()
    }
}

internal fun tryPreloadSharedAdWhenAdDismissed(sceneName: String) {
    logInfo(TAG) {
        "tryPreloadSharedAdWhenAdDismissed: sceneName=$sceneName"
    }
    if (NewUserAdPreloadGate.isOrdinaryPreloadBlocked()) {
        logInfo(TAG) { "Skip shared preload on dismiss because ordinary preload is still gated" }
        return
    }
    dispatchToBackground {
        SharedCeilAdPool.preload()
        SharedFloorAdPool.preload()
    }
}

internal fun tryPreloadSharedAdWhenAppStartup() {
    logInfo(TAG) {
        "tryPreloadSharedAdWhenAppStartup"
    }
    if (NewUserAdPreloadGate.isOrdinaryPreloadBlocked()) {
        logInfo(TAG) { "Skip shared preload on app startup because ordinary preload is still gated" }
        return
    }
    dispatchToBackground {
        SharedCeilAdPool.preload()
        SharedFloorAdPool.preload()
    }
}

internal fun tryPreloadSharedAdWhenNotificationShownOrClick() {
    logInfo(TAG) {
        "tryPreloadSharedAdWhenNotificationShownOrClick"
    }
    if (NewUserAdPreloadGate.isOrdinaryPreloadBlocked()) {
        logInfo(TAG) { "Skip shared preload on notification because ordinary preload is still gated" }
        return
    }
    dispatchToBackground {
        SharedCeilAdPool.preload()
        SharedFloorAdPool.preload()
    }
}

internal fun tryPreloadSharedAdWhenConnectivityChanged() {
    logInfo(TAG) {
        "tryPreloadSharedAdWhenConnectivityChanged"
    }
    if (NewUserAdPreloadGate.isOrdinaryPreloadBlocked()) {
        logInfo(TAG) { "Skip shared preload on connectivity change because ordinary preload is still gated" }
        return
    }
    dispatchToBackground {
        SharedCeilAdPool.preload()
        SharedFloorAdPool.preload()
    }
}
