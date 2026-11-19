package com.poseidon.ad.main.preload.special

import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.serialization.encodeToString
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.main.processor.PlatformInterstitialAdLoadParameter
import com.poseidon.ad.main.processor.NightBackgroundPreloadGuard
import com.poseidon.ad.main.processor.PreloadMemoryGuard
import com.poseidon.ad.restrict.show.currentDateFormatWithYearMonthDay
import com.poseidon.ad.common.storage.sp.Key
import com.poseidon.ad.common.storage.sp.SpBatchEntry
import com.poseidon.ad.common.storage.sp.computeDailyCounterPlan
import com.poseidon.ad.main.adinfo.AdBase
import com.poseidon.ad.main.adinfo.CacheAd
import com.poseidon.ad.main.adinfo.evictCacheAdsBelowEcpm
import com.poseidon.ad.main.adinfo.evictExpiredCacheAds
import com.poseidon.ad.mediation.MEDIATION_ADMOB
import com.poseidon.ad.mediation.MEDIATION_MAX
import com.poseidon.ad.mediation.MEDIATION_TOPON
import com.poseidon.ad.main.adinfo.InterstitialAdBase
import com.poseidon.ad.main.adinfo.readHighEcpmThreshold
import com.poseidon.ad.main.adinfo.selectTopCacheAd
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.main.preload.new.NewUserAdPreloadGate
import com.poseidon.ad.main.strategy.common.SpecificScenePlacementCfg
import com.poseidon.ad.common.toolkit.PoseidonJson
import com.poseidon.ad.common.toolkit.StringHelper
import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.common.toolkit.ResultCallback
import com.poseidon.ad.common.toolkit.TimeHelper
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.runOnMainThread
import com.poseidon.ad.common.toolkit.schedule
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.InterstitialParameter
import com.poseidon.ad.main.load.createInterstitialAdLoader
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

internal object SpecificAdSceneCacheBackup {

    private const val TAG = "SpecificAdSceneCacheBackup"

    private val startedAtomic = atomic(false)

    private val cacheAds: MutableList<CacheAd<out AdBase>> = mutableListOf()
    private val cacheAdsLock = reentrantLock()

    private val isLoadingByAdUnitId: MutableMap<String, AtomicBoolean> = mutableMapOf()

    fun addAdToCache(ad: CacheAd<out AdBase>) {
        cacheAdsLock.withLock {
            increaseAdFilledUnusedCount(adUnitId = ad.adUnitId)
            cacheAds.add(ad)
        }
    }

    fun hasCacheAd(sceneName: String): Boolean {
        if (!supportSceneNames().contains(sceneName)) {
            return false
        }
        return cacheAdsLock.withLock {
            cacheAds.evictExpiredCacheAds(reason = "$TAG hasCacheAd(sceneName=$sceneName)")
                .forEach { decreaseAdFilledUnusedCount(adUnitId = it.adUnitId) }
            cacheAds.size > 0
        }
    }

    fun consumeCacheAd(sceneName: String): CacheAd<out AdBase>? {
        if (!supportSceneNames().contains(sceneName)) {
            return null
        }
        val threshold = readHighEcpmThreshold()
        val consumed = cacheAdsLock.withLock {
            cacheAds.evictExpiredCacheAds(reason = "$TAG consumeCacheAd(sceneName=$sceneName)")
                .forEach { decreaseAdFilledUnusedCount(adUnitId = it.adUnitId) }
            val candidates = cacheAds.filter { it.isReady() }
            selectTopCacheAd(candidates, threshold)
                ?.also { ad -> cacheAds.remove(ad) }
        }
        if (consumed != null) {
            decreaseAdFilledUnusedCount(adUnitId = consumed.adUnitId)
        }
        return consumed
    }

    private fun supportSceneNames(): List<String> {
        val configuration = PoseidonConfig.Companion.get()?.specificAdSceneCacheBackupConfiguration ?: return emptyList()
        return configuration.sceneNames
    }

    @AnyThread
    fun preload() {
        if (startedAtomic.compareAndSet(expect = false, update = true)) {
            schedule(delay = 0L, period = 5.seconds.inWholeMilliseconds) {
                if (hasEnabledPreloadConfiguration()) {
                    runOnMainThread { load() }
                }
            }
        }
    }

    private fun hasEnabledPreloadConfiguration(): Boolean {
        val rootConfiguration = PoseidonConfig.Companion.get() ?: return false
        if (!rootConfiguration.isSwitchOn()) return false
        val configuration = rootConfiguration.specificAdSceneCacheBackupConfiguration ?: return false
        if (!configuration.isSwitchOn() || configuration.sceneNames.isEmpty()) return false
        return configuration.adUnits.any { adUnit ->
            adUnit.isSwitchOn() && adUnit.id.isNotEmpty() &&
                (adUnit.network == MEDIATION_TOPON ||
                    adUnit.network == MEDIATION_MAX ||
                    adUnit.network == MEDIATION_ADMOB)
        }
    }

    @MainThread
    fun load() {
        if (NewUserAdPreloadGate.isOrdinaryPreloadBlocked()) {
            com.poseidon.ad.common.toolkit.logInfo(TAG) {
                "Skip load, ordinary preload is still gated"
            }
            return
        }
        val rootConfiguration = PoseidonConfig.Companion.get()
        if (rootConfiguration == null) {
            logInfo(TAG) { "Skip load, rootConfiguration is null" }
            return
        }

        if (!rootConfiguration.isSwitchOn()) {
            logInfo(TAG) { "Skip load, global switch is off" }
            return
        }

        val configuration = rootConfiguration.specificAdSceneCacheBackupConfiguration
        if (configuration == null) {
            logInfo(TAG) { "Skip load, specificAdSceneCacheBackupConfiguration is null" }
            return
        }

        if (!configuration.isSwitchOn()) {
            logInfo(TAG) { "Skip load, specificAdSceneCacheBackupConfiguration switch is off" }
            return
        }
        if (configuration.sceneNames.isEmpty()) {
            logInfo(TAG) { "Skip load, specificAdSceneCacheBackupConfiguration sceneNames is empty" }
            return
        }
        val adUnits = configuration.adUnits.asSequence().filter { it.isSwitchOn() }.filter {
            it.id.isNotEmpty() && (it.network == MEDIATION_TOPON || it.network == MEDIATION_MAX || it.network == MEDIATION_ADMOB)
        }.toList()
        if (adUnits.isEmpty()) {
            logInfo(TAG) { "Skip load, specificAdSceneCacheBackupConfiguration adUnits is empty after filter" }
            return
        }

        evictExpiredAds()

        if (NightBackgroundPreloadGuard.shouldSkipPreload()) {
            NightBackgroundPreloadGuard.reportSkip(sceneName = TAG)
            return
        }

        if (PreloadMemoryGuard.shouldSkipPreload(PoseidonSetupProcessor.getInstance().application, sceneName = TAG)) {
            return
        }

        for (adUnit in adUnits) {
            val adUnitId = adUnit.id

            val hasCache = cacheAdsLock.withLock {
                cacheAds.evictExpiredCacheAds(reason = "$TAG load(adUnitId=$adUnitId)")
                    .forEach { decreaseAdFilledUnusedCount(adUnitId = it.adUnitId) }
                cacheAds.any { it.adUnitId == adUnitId }
            }

            if (hasCache) {
                logInfo(TAG) { "Skip load ${adUnitId}, cache exists" }
                continue
            }

            val currentConsecutiveLoadFailuresCount =
                getAdConsecutiveLoadFailuresCount(adUnitId = adUnit.id)
            val maxAdConsecutiveLoadFailuresCountFromRemote =
                getMaxAdConsecutiveLoadFailuresCountFromRemote()
            if (currentConsecutiveLoadFailuresCount >= maxAdConsecutiveLoadFailuresCountFromRemote) {
                logInfo(TAG) { "Skip load ${adUnitId}, reach max consecutive(${maxAdConsecutiveLoadFailuresCountFromRemote}) load failures count${currentConsecutiveLoadFailuresCount}!" }
                continue
            }

            val adFilledUnusedCount = getAdFilledUnusedCount(adUnitId = adUnit.id)
            val maxAdFilledUnusedCountFromRemote = getMaxAdFilledUnusedCountFromRemote()
            if (adFilledUnusedCount >= maxAdFilledUnusedCountFromRemote) {
                logInfo(TAG) { "Skip load ${adUnitId}, reach max filled(${maxAdFilledUnusedCountFromRemote}) unused count${adFilledUnusedCount}!" }
                continue
            }

            val isLoadingAtomic = isLoadingByAdUnitId.getOrPut(adUnitId) { atomic(false) }
            if (!isLoadingAtomic.value) {
                if (isLoadingAtomic.compareAndSet(expect = false, update = true)) {
                    logInfo(TAG) { "Start load $adUnitId" }
                    loadInterstitialAd(
                        loadParameter = adUnit.asPlatformInterstitialAdLoadParameter(sceneName = "SpecificAdSceneCacheBackup"),
                        adUnit = adUnit,
                        poseidonConfigVersion = rootConfiguration.version,
                        onComplete = { isSuccessful ->
                            logInfo(TAG) { "Ad load completed ${adUnitId}, isSuccessful=$isSuccessful!" }
                            isLoadingAtomic.compareAndSet(expect = true, update = false)
                            if (isSuccessful) {
                                setAdConsecutiveLoadFailuresCount(adUnitId = adUnit.id, count = 0)
                            } else {
                                increaseAdConsecutiveLoadFailuresCount(adUnitId = adUnit.id)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun evictExpiredAds() {
        cacheAdsLock.withLock {
            cacheAds.evictExpiredCacheAds(reason = "$TAG preload")
                .forEach { decreaseAdFilledUnusedCount(adUnitId = it.adUnitId) }
        }
    }

    internal fun scavengeBelowEcpm(minEcpmToKeep: Double, reason: String): Int {
        return cacheAdsLock.withLock {
            val evicted = cacheAds.evictCacheAdsBelowEcpm(minEcpmToKeep, reason = "$TAG $reason")
            evicted.forEach { decreaseAdFilledUnusedCount(adUnitId = it.adUnitId) }
            evicted.size
        }
    }

    internal fun evictExpiredAdsNow(reason: String) {
        cacheAdsLock.withLock {
            cacheAds.evictExpiredCacheAds(reason = "$TAG $reason")
                .forEach { decreaseAdFilledUnusedCount(adUnitId = it.adUnitId) }
        }
    }

    @MainThread
    private fun loadInterstitialAd(
        loadParameter: PlatformInterstitialAdLoadParameter,
        adUnit: SpecificScenePlacementCfg,
        poseidonConfigVersion: VersionHelper?,
        onComplete: (isSuccessful: Boolean) -> Unit
    ) {
        val network = adUnit.network
        val parameter = InterstitialParameter(
            adUnitId = adUnit.id,
            startLoadTimeInMillis = TimeHelper.currentTimeInMillis(),
            network = network,
            sessionId = StringHelper.newSessionId(),
            sceneName = loadParameter.sceneName,
            poseidonConfigVersion = poseidonConfigVersion,
        )
        when (val result =
            createInterstitialAdLoader(network = network, parameter = parameter)) {
            is ResultCallback.Failure -> {
                onComplete(false)
            }

            is ResultCallback.Success -> {
                try {
                    val callback = object : InnerAdLoadCallback<InterstitialAdBase> {
                        override fun onAdLoadFailed(code: Int, message: String) {
                            onComplete(false)
                        }

                        override fun onAdLoadSuccess(ad: InterstitialAdBase) {
                            val cacheAd = CacheAd(
                                ad = ad,
                                adUnitId = parameter.adUnitId,
                                loadSuccessTimeInMillis = TimeHelper.currentTimeInMillis(),
                                number = 1,
                            )
                            addAdToCache(cacheAd)
                            onComplete(true)
                        }
                    }
                    result.value.load(loadParameter = loadParameter, loadCallback = callback)
                } catch (t: Throwable) {
                    onComplete(false)
                }
            }
        }
    }

    private fun setAdConsecutiveLoadFailuresCount(adUnitId: String, count: Int) {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val json = sharedPreference.getString(Key.SpecificAdSceneCacheBackupAdConsecutiveLoadFailuresCountByAdUnitId, null)
        val m: MutableMap<String, Int> = if (json.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            runCatching { PoseidonJson.decodeFromString<MutableMap<String, Int>>(json) }
                .getOrDefault(mutableMapOf())
        }
        m[adUnitId] = count
        sharedPreference.putString(
            key = Key.SpecificAdSceneCacheBackupAdConsecutiveLoadFailuresCountByAdUnitId,
            value = runCatching { PoseidonJson.encodeToString(m) }.getOrNull())
    }

    private fun increaseAdConsecutiveLoadFailuresCount(adUnitId: String) {
        resetAdConsecutiveLoadFailuresCountIfNewDay(adUnitId = adUnitId)

        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val json = sharedPreference.getString(Key.SpecificAdSceneCacheBackupAdConsecutiveLoadFailuresCountByAdUnitId, null)
        val m: MutableMap<String, Int> = if (json.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            runCatching { PoseidonJson.decodeFromString<MutableMap<String, Int>>(json) }
                .getOrDefault(mutableMapOf())
        }
        m[adUnitId] = (m[adUnitId] ?: 0) + 1
        sharedPreference.putString(
            key = Key.SpecificAdSceneCacheBackupAdConsecutiveLoadFailuresCountByAdUnitId,
            value = runCatching { PoseidonJson.encodeToString(m) }.getOrNull())
    }

    private fun getAdConsecutiveLoadFailuresCount(adUnitId: String): Int {
        resetAdConsecutiveLoadFailuresCountIfNewDay(adUnitId = adUnitId)

        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val json = sharedPreference.getString(Key.SpecificAdSceneCacheBackupAdConsecutiveLoadFailuresCountByAdUnitId, null)
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
            savedDate = sharedPreference.getString(Key.SpecificAdSceneCacheBackupAdConsecutiveLoadFailuresCountByAdUnitIdLastDate, null),
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
                SpBatchEntry.StringValue(key = Key.SpecificAdSceneCacheBackupAdConsecutiveLoadFailuresCountByAdUnitIdLastDate, value = plan.dateValue),
                SpBatchEntry.StringValue(key = Key.SpecificAdSceneCacheBackupAdConsecutiveLoadFailuresCountByAdUnitId, value = resetJson),
            ))
        }
    }

    private fun getMaxAdConsecutiveLoadFailuresCountFromRemote(): Int {
        return PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig
            .getString("specific_backup_max_consecutive_load_failures_count")
            .toIntOrNull() ?: 6
    }

    ///

    private fun setAdFilledUnusedCount(adUnitId: String, count: Int) {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val json = sharedPreference.getString(Key.SpecificAdSceneCacheBackupAdFilledUnusedCountByAdUnitId, null)
        val m: MutableMap<String, Int> = if (json.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            runCatching { PoseidonJson.decodeFromString<MutableMap<String, Int>>(json) }
                .getOrDefault(mutableMapOf())
        }
        m[adUnitId] = count
        sharedPreference.putString(
            key = Key.SpecificAdSceneCacheBackupAdFilledUnusedCountByAdUnitId,
            value = runCatching { PoseidonJson.encodeToString(m) }.getOrNull())
    }

    private fun increaseAdFilledUnusedCount(adUnitId: String) {
        resetAdFilledUnusedCountIfNewDay(adUnitId = adUnitId)

        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val json = sharedPreference.getString(Key.SpecificAdSceneCacheBackupAdFilledUnusedCountByAdUnitId, null)
        val m: MutableMap<String, Int> = if (json.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            runCatching { PoseidonJson.decodeFromString<MutableMap<String, Int>>(json) }
                .getOrDefault(mutableMapOf())
        }
        m[adUnitId] = (m[adUnitId] ?: 0) + 1
        sharedPreference.putString(
            key = Key.SpecificAdSceneCacheBackupAdFilledUnusedCountByAdUnitId,
            value = runCatching { PoseidonJson.encodeToString(m) }.getOrNull())
    }

    private fun decreaseAdFilledUnusedCount(adUnitId: String) {
        resetAdFilledUnusedCountIfNewDay(adUnitId = adUnitId)

        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val json = sharedPreference.getString(Key.SpecificAdSceneCacheBackupAdFilledUnusedCountByAdUnitId, null)
        val m: MutableMap<String, Int> = if (json.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            runCatching { PoseidonJson.decodeFromString<MutableMap<String, Int>>(json) }
                .getOrDefault(mutableMapOf())
        }
        m[adUnitId] = max(((m[adUnitId] ?: 0) - 1), 0)
        sharedPreference.putString(
            key = Key.SpecificAdSceneCacheBackupAdFilledUnusedCountByAdUnitId,
            value = runCatching { PoseidonJson.encodeToString(m) }.getOrNull())
    }

    private fun getAdFilledUnusedCount(adUnitId: String): Int {
        resetAdFilledUnusedCountIfNewDay(adUnitId = adUnitId)

        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val json = sharedPreference.getString(Key.SpecificAdSceneCacheBackupAdFilledUnusedCountByAdUnitId, null)
        val m: MutableMap<String, Int> = if (json.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            runCatching { PoseidonJson.decodeFromString<MutableMap<String, Int>>(json) }
                .getOrDefault(mutableMapOf())
        }
        return m[adUnitId] ?: 0
    }

    private fun resetAdFilledUnusedCountIfNewDay(adUnitId: String) {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val plan = computeDailyCounterPlan(
            savedDate = sharedPreference.getString(Key.SpecificAdSceneCacheBackupAdFilledUnusedCountByAdUnitIdLastDate, null),
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
                SpBatchEntry.StringValue(key = Key.SpecificAdSceneCacheBackupAdFilledUnusedCountByAdUnitIdLastDate, value = plan.dateValue),
                SpBatchEntry.StringValue(key = Key.SpecificAdSceneCacheBackupAdFilledUnusedCountByAdUnitId, value = resetJson),
            ))
        }
    }

    private fun getMaxAdFilledUnusedCountFromRemote(): Int {
        return PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig
            .getString("specific_backup_filled_unused_count")
            .toIntOrNull() ?: 10
    }
}

internal fun SpecificScenePlacementCfg.asPlatformInterstitialAdLoadParameter(
    sceneName: String
): PlatformInterstitialAdLoadParameter {
    return PlatformInterstitialAdLoadParameter(
        context = PoseidonSetupProcessor.getInstance().application,
        sceneName = sceneName
    )
}
