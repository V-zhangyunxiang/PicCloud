package com.poseidon.ad.main.preload.share

import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.common.storage.sp.Key
import com.poseidon.ad.common.storage.sp.KeyProvider
import com.poseidon.ad.common.storage.sp.SpBatchEntry
import com.poseidon.ad.common.storage.sp.computeDailyCounterPlan
import com.poseidon.ad.common.toolkit.DATE_SUFFIX
import com.poseidon.ad.common.toolkit.ResultCallback
import com.poseidon.ad.common.toolkit.StringHelper
import com.poseidon.ad.common.toolkit.TimeHelper
import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.common.toolkit.dispatchToBackground
import com.poseidon.ad.common.toolkit.postDelayedToBackground
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
import com.poseidon.ad.main.adinfo.readHighEcpmThreshold
import com.poseidon.ad.main.adinfo.selectTopCacheAd
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.InterstitialParameter
import com.poseidon.ad.main.load.NativeParameter
import com.poseidon.ad.main.load.RewardedParameter
import com.poseidon.ad.main.load.createInterstitialAdLoader
import com.poseidon.ad.main.load.createNativeAdLoader
import com.poseidon.ad.main.load.createRewardedAdLoader
import com.poseidon.ad.main.processor.NightBackgroundPreloadGuard
import com.poseidon.ad.main.processor.PlatformInterstitialAdLoadParameter
import com.poseidon.ad.main.processor.PlatformNativeAdLoadParameter
import com.poseidon.ad.main.processor.PlatformRewardedAdLoadParameter
import com.poseidon.ad.main.processor.PreloadMemoryGuard
import com.poseidon.ad.main.strategy.common.AutomationCfg
import com.poseidon.ad.main.preload.new.NewUserAdPreloadGate
import com.poseidon.ad.main.strategy.common.AutomationItemCfg
import com.poseidon.ad.main.strategy.common.PlacementCfg
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.restrict.show.currentDateFormatWithYearMonthDay
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

internal abstract class Automation {

    private val startedAtomic = atomic(false)

    private fun logInfo(lazyMessage: () -> String) {
        com.poseidon.ad.common.toolkit.logInfo(
            tag = "${getName()}Automation",
            lazyMessage = lazyMessage
        )
    }

    protected abstract fun defaultInterval(): Long

    fun start() {
        if (startedAtomic.compareAndSet(expect = false, update = true)) {
            logInfo { "started." }
            val recurringListener = object : OnCompleteListener {
                override fun onComplete(msg: String) {
                    val self = this
                    dispatchToBackground {
                        val delayMillis = resolveAutomationPreloadInterval(
                            remoteValue = PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig
                                .getString("poseidon_${getName()}_automation_preload_interval"),
                            defaultIntervalMillis = defaultInterval(),
                        )
                        logInfo { "onComplete: $msg, delayMillis=$delayMillis" }
                        postDelayedToBackground(delayMillis = delayMillis) {
                            preload(self)
                        }
                    }
                }
            }
            dispatchToBackground {
                preload(recurringListener)
            }
        }
    }

    private interface OnCompleteListener {
        fun onComplete(msg: String)
    }

    private fun preload(listener: OnCompleteListener) {
        val sceneName = "automation_${getName()}"
        getAutomationItemConfiguration()
            .onSuccess { (version, automationItemConfiguration) ->
                evictExpiredAds()
                if (NightBackgroundPreloadGuard.shouldSkipPreload()) {
                    NightBackgroundPreloadGuard.reportSkip(sceneName = sceneName)
                    listener.onComplete("Failure: $sceneName skipped by US night background preload guard.")
                    return
                }
                if (PreloadMemoryGuard.shouldSkipPreload(PoseidonSetupProcessor.getInstance().application, sceneName = sceneName)) {
                    listener.onComplete("Failure: $sceneName skipped by background low memory guard.")
                    return@onSuccess
                }
                val topOnAdUnits = automationItemConfiguration.topOnAdUnits
                val admobAdUnits = automationItemConfiguration.admobAdUnits
                val topLevelAdUnits = (topOnAdUnits + admobAdUnits.firstOrNull()).filterNotNull()
                load(topLevelAdUnits, version) { successCount ->
                    if (successCount > 0) {
                        listener.onComplete("Success: $successCount ads loaded in TopLevelAdUnits(${topLevelAdUnits.size}).")
                    } else {
                        val firstAdmobAdUnit = admobAdUnits.getOrNull(0)
                        val secondAdmobAdUnit = admobAdUnits.getOrNull(1)
                        if (secondAdmobAdUnit == null) {
                            if (firstAdmobAdUnit == null) {
                                listener.onComplete("Failure: Both first and second Admob ad units are null.")
                            } else {
                                load(
                                    listOf(firstAdmobAdUnit),
                                    version
                                ) { successCountOfFirstAdmobAdUnit ->
                                    if (successCountOfFirstAdmobAdUnit == 1) {
                                        listener.onComplete("Success: $successCountOfFirstAdmobAdUnit ads loaded in first Admob ad unit(${firstAdmobAdUnit.id}).")
                                    } else {
                                        listener.onComplete("Failure: $successCountOfFirstAdmobAdUnit ads loaded in first Admob ad unit(${firstAdmobAdUnit.id}).")
                                    }
                                }
                            }
                        } else {
                            load(
                                adUnits = listOf(secondAdmobAdUnit),
                                version
                            ) { successCountSecondAdmobAdUnit ->
                                if (successCountSecondAdmobAdUnit == 1) {
                                    listener.onComplete("Success: $successCountSecondAdmobAdUnit ads loaded in second Admob ad unit(${secondAdmobAdUnit.id}).")
                                } else {
                                    if (firstAdmobAdUnit == null) {
                                        listener.onComplete("Failure: Second Admob ad failed to load and first Admob ad unit is null.")
                                    } else {
                                        load(
                                            listOf(firstAdmobAdUnit),
                                            version
                                        ) { successCountOfFirstAdmobAdUnit ->
                                            if (successCountOfFirstAdmobAdUnit == 1) {
                                                listener.onComplete("Success: $successCountOfFirstAdmobAdUnit ads loaded in first Admob ad unit(${firstAdmobAdUnit.id}).")
                                            } else {
                                                listener.onComplete("Failure: $successCountOfFirstAdmobAdUnit ads loaded in first Admob ad unit(${firstAdmobAdUnit.id}).")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }.onFailure { t ->
                listener.onComplete("Failure: ${t.message}")
            }
    }

    private fun load(
        adUnits: List<PlacementCfg>,
        version: VersionHelper?,
        onComplete: (successCount: Int) -> Unit
    ) {
        if (adUnits.isEmpty()) {
            onComplete(0)
            return
        }

        val totalCount = adUnits.size
        val failureCount = atomic(0)
        val successCount = atomic(0)

        fun callbackIfNeed() {
            if (failureCount.value + successCount.value == totalCount) {
                onComplete(successCount.value)
            }
        }

        for (adUnit in adUnits) {
            val limitCountOfConsecutiveLoadFailure = getLimitCountOfConsecutiveLoadFailure()
            val countOfConsecutiveLoadFailure = getCountOfConsecutiveLoadFailure(adUnit.id)
            logInfo { "load: adUnit=${adUnit}, limitCountOfConsecutiveLoadFailure=$limitCountOfConsecutiveLoadFailure, countOfConsecutiveLoadFailure=$countOfConsecutiveLoadFailure" }
            if (limitCountOfConsecutiveLoadFailure != null && limitCountOfConsecutiveLoadFailure > 0
                && countOfConsecutiveLoadFailure >= limitCountOfConsecutiveLoadFailure
            ) {
                failureCount.incrementAndGet()
                callbackIfNeed()
                continue
            }

            if (cacheCount { it.adUnitId == adUnit.id } > 0) {
                failureCount.incrementAndGet()
                callbackIfNeed()
                continue
            }

            loadAutomationAdUnit(
                loadParameter = adUnit.asPlatformAdLoadParameter("automation_${getName()}"),
                adUnit = adUnit,
                poseidonConfigVersion = version,
                onComplete = { isSuccessful ->
                    logInfo { "load: adUnit=${adUnit}, isSuccessful=$isSuccessful." }
                    if (isSuccessful) {
                        resetCountOfConsecutiveLoadFailure(adUnit.id)
                        successCount.incrementAndGet()
                    } else {
                        increaseCountOfConsecutiveLoadFailure(adUnit.id)
                        failureCount.incrementAndGet()
                    }
                    callbackIfNeed()
                }
            )
        }
    }

    private val cacheAds: MutableList<CacheAd<out AdBase>> = mutableListOf()
    private val cacheAdsLock = reentrantLock()

    fun addAdToCache(ad: CacheAd<out AdBase>) {
        cacheAdsLock.withLock {
            cacheAds.add(ad)
        }
    }

    private fun evictExpiredAds() {
        cacheAdsLock.withLock {
            cacheAds.evictExpiredCacheAds(reason = "${getName()} automation preload")
        }
    }

    internal fun scavengeBelowEcpm(minEcpmToKeep: Double, reason: String): Int {
        return cacheAdsLock.withLock {
            cacheAds.evictCacheAdsBelowEcpm(minEcpmToKeep, reason = "${getName()} automation $reason").size
        }
    }

    internal fun evictExpiredAdsNow(reason: String) {
        cacheAdsLock.withLock {
            cacheAds.evictExpiredCacheAds(reason = "${getName()} automation $reason")
        }
    }

    fun cacheCount(predicate: (CacheAd<out AdBase>) -> Boolean): Int {
        return cacheAdsLock.withLock {
            cacheAds.evictExpiredCacheAds(reason = "${getName()} automation cacheCount")
            cacheAds.filter(predicate).size
        }
    }

    fun consumeCacheAd(@AdTypeDef vararg filteredAdTypes: Int): CacheAd<out AdBase>? {
        val threshold = readHighEcpmThreshold()
        return cacheAdsLock.withLock {
            cacheAds.evictExpiredCacheAds(reason = "${getName()} automation consumeCacheAd")
            val candidates =
                cacheAds.filter { filteredAdTypes.contains(it.ad.getAdType()) && it.isReady() }
            selectTopCacheAd(candidates, threshold)
                ?.also { ad -> cacheAds.remove(ad) }
        }
    }

    private fun loadAutomationAdUnit(
        loadParameter: Any,
        adUnit: PlacementCfg,
        poseidonConfigVersion: VersionHelper?,
        onComplete: (isSuccessful: Boolean) -> Unit
    ) {
        logInfo {
            ">>>>>loadAutomationAdUnit: loadParameter=$loadParameter, adUnit=$adUnit"
        }
        when (adUnit.adType) {
            AD_TYPE_INTERSTITIAL -> {
                val lp = loadParameter as PlatformInterstitialAdLoadParameter
                val network = adUnit.mediation
                val parameter = InterstitialParameter(
                    adUnitId = adUnit.id,
                    startLoadTimeInMillis = TimeHelper.currentTimeInMillis(),
                    network = network,
                    sessionId = StringHelper.newSessionId(),
                    sceneName = lp.sceneName,
                    poseidonConfigVersion = poseidonConfigVersion,
                )
                when (val result =
                    createInterstitialAdLoader(network = network, parameter = parameter)) {
                    is ResultCallback.Failure -> {
                        onComplete(false)
                    }

                    is ResultCallback.Success -> {
                        val callback = object : InnerAdLoadCallback<InterstitialAdBase> {
                            override fun onAdLoadFailed(code: Int, message: String) {
                                onComplete(false)
                            }

                            override fun onAdLoadSuccess(ad: InterstitialAdBase) {
                                val cacheAd = CacheAd(
                                    ad = ad,
                                    adUnitId = parameter.adUnitId,
                                    loadSuccessTimeInMillis = TimeHelper.currentTimeInMillis(),
                                    number = getNumber(),
                                )
                                addAdToCache(cacheAd)
                                onComplete(true)
                            }
                        }
                        val adLoader = result.value
                        adLoader.load(loadParameter = lp, loadCallback = callback)
                    }
                }
            }

            AD_TYPE_REWARDED -> {
                val lp = loadParameter as PlatformRewardedAdLoadParameter
                val network = adUnit.mediation
                val parameter = RewardedParameter(
                    adUnitId = adUnit.id,
                    startLoadTimeInMillis = TimeHelper.currentTimeInMillis(),
                    network = network,
                    sessionId = StringHelper.newSessionId(),
                    sceneName = lp.sceneName,
                    poseidonConfigVersion = poseidonConfigVersion,
                )
                when (val result =
                    createRewardedAdLoader(network = network, parameter = parameter)) {
                    is ResultCallback.Failure -> {
                        onComplete(false)
                    }

                    is ResultCallback.Success -> {
                        val callback = object : InnerAdLoadCallback<RewardedAdBase> {
                            override fun onAdLoadFailed(code: Int, message: String) {
                                onComplete(false)
                            }

                            override fun onAdLoadSuccess(ad: RewardedAdBase) {
                                val cacheAd = CacheAd(
                                    ad = ad,
                                    adUnitId = parameter.adUnitId,
                                    loadSuccessTimeInMillis = TimeHelper.currentTimeInMillis(),
                                    number = getNumber(),
                                )
                                addAdToCache(cacheAd)
                                onComplete(true)
                            }
                        }
                        val adLoader = result.value
                        adLoader.load(loadParameter = lp, loadCallback = callback)
                    }
                }
            }

            AD_TYPE_NATIVE -> {
                val lp = loadParameter as PlatformNativeAdLoadParameter
                val network = adUnit.mediation
                val parameter = NativeParameter(
                    adUnitId = adUnit.id,
                    startLoadTimeInMillis = TimeHelper.currentTimeInMillis(),
                    network = network,
                    sessionId = StringHelper.newSessionId(),
                    sceneName = lp.sceneName,
                    poseidonConfigVersion = poseidonConfigVersion,
                )
                when (val result = createNativeAdLoader(network = network, parameter = parameter)) {
                    is ResultCallback.Failure -> {
                        onComplete(false)
                    }

                    is ResultCallback.Success -> {
                        val callback = object : InnerAdLoadCallback<NativeAdBase> {
                            override fun onAdLoadFailed(code: Int, message: String) {
                                onComplete(false)
                            }

                            override fun onAdLoadSuccess(ad: NativeAdBase) {
                                val cacheAd = CacheAd(
                                    ad = ad,
                                    adUnitId = parameter.adUnitId,
                                    loadSuccessTimeInMillis = TimeHelper.currentTimeInMillis(),
                                    number = getNumber(),
                                )
                                addAdToCache(cacheAd)
                                onComplete(true)
                            }
                        }
                        val adLoader = result.value
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

    private fun automationCounterKey(adUnitId: String): KeyProvider =
        KeyProvider { Key.AutomationAdConsecutiveLoadFailuresCountByAdUnitId.key() + "_" + adUnitId }

    private fun automationDateKey(adUnitId: String): KeyProvider =
        KeyProvider { automationCounterKey(adUnitId).key() + DATE_SUFFIX }

    private fun getCountOfConsecutiveLoadFailure(adUnitId: String): Int {
        val sp = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val counterKey = automationCounterKey(adUnitId)
        val dateKey = automationDateKey(adUnitId)
        val plan = computeDailyCounterPlan(
            savedDate = sp.getString(dateKey, null),
            currentDate = currentDateFormatWithYearMonthDay(),
            savedCounter = sp.getInt(counterKey, 0),
            applyIncrement = false,
        )
        val updates = mutableListOf<SpBatchEntry>()
        if (plan.needsPersistDate) updates.add(
            SpBatchEntry.StringValue(
                key = dateKey,
                value = plan.dateValue
            )
        )
        if (plan.needsPersistCounter) updates.add(
            SpBatchEntry.IntValue(
                key = counterKey,
                value = plan.counterValue
            )
        )
        if (updates.isNotEmpty()) sp.writeBatch(updates)
        return plan.counterValue
    }

    private fun increaseCountOfConsecutiveLoadFailure(adUnitId: String) {
        val sp = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val counterKey = automationCounterKey(adUnitId)
        val dateKey = automationDateKey(adUnitId)
        val plan = computeDailyCounterPlan(
            savedDate = sp.getString(dateKey, null),
            currentDate = currentDateFormatWithYearMonthDay(),
            savedCounter = sp.getInt(counterKey, 0),
            applyIncrement = true,
        )
        val updates = mutableListOf<SpBatchEntry>()
        if (plan.needsPersistDate) updates.add(
            SpBatchEntry.StringValue(
                key = dateKey,
                value = plan.dateValue
            )
        )
        if (plan.needsPersistCounter) updates.add(
            SpBatchEntry.IntValue(
                key = counterKey,
                value = plan.counterValue
            )
        )
        if (updates.isNotEmpty()) sp.writeBatch(updates)
    }

    private fun resetCountOfConsecutiveLoadFailure(adUnitId: String) {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val key = { Key.AutomationAdConsecutiveLoadFailuresCountByAdUnitId.key() + "_" + adUnitId }
        sharedPreference.putInt(key = key, value = 0)
    }

    protected fun getAutomationConfiguration(): Result<Pair<VersionHelper?, AutomationCfg>> {
        val config =
            PoseidonConfig.Companion.get() ?: return Result.failure(Exception("config is null"))
        if (!config.isSwitchOn()) {
            return Result.failure(Exception("global switch is off"))
        }
        val automation = config.automation ?: return Result.failure(Exception("automation is null"))
        if (!automation.isSwitchOn()) {
            return Result.failure(Exception("automation switch is off"))
        }
        return Result.success(config.version to automation)
    }

    protected abstract fun getName(): String
    protected abstract fun getNumber(): Int
    protected abstract fun getLimitCountOfConsecutiveLoadFailure(): Int?
    protected abstract fun getAutomationItemConfiguration(): Result<Pair<VersionHelper?, AutomationItemCfg>>
}

internal fun resolveAutomationPreloadInterval(
    remoteValue: String,
    defaultIntervalMillis: Long,
): Long {
    require(defaultIntervalMillis > 0L) { "defaultIntervalMillis must be positive" }
    return remoteValue.toLongOrNull()?.takeIf { it > 0L } ?: defaultIntervalMillis
}

internal object CeilAutomation : Automation() {

    override fun defaultInterval(): Long {
        return 30_000L
    }

    override fun getName(): String {
        return "ceil"
    }

    override fun getNumber(): Int {
        return 3
    }

    override fun getLimitCountOfConsecutiveLoadFailure(): Int {
        return PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig
            .getString("poseidon_ceil_automation_max_consecutive_load_failures_count")
            .toIntOrNull() ?: 6
    }

    override fun getAutomationItemConfiguration(): Result<Pair<VersionHelper?, AutomationItemCfg>> {
        return getAutomationConfiguration().mapCatching { (version, automation) ->
            val ceil = automation.ceil ?: error("ceil is null")
            if (!ceil.isSwitchOn()) {
                error("ceil switch is off")
            }
            version to ceil
        }
    }
}

internal object FloorAutomation : Automation() {
    override fun defaultInterval(): Long {
        return 20_000L
    }

    override fun getName(): String {
        return "floor"
    }

    override fun getNumber(): Int {
        return 1
    }

    override fun getLimitCountOfConsecutiveLoadFailure(): Int? {
        return PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig
            .getString("poseidon_floor_automation_max_consecutive_load_failures_count")
            .toIntOrNull()
    }

    override fun getAutomationItemConfiguration(): Result<Pair<VersionHelper?, AutomationItemCfg>> {
        return getAutomationConfiguration().mapCatching { (version, automation) ->
            val floor = automation.floor ?: error("floor is null")
            if (!floor.isSwitchOn()) {
                error("floor switch is off")
            }
            version to floor
        }
    }
}

internal fun startAutomation() {
    if (NewUserAdPreloadGate.isOrdinaryPreloadBlocked()) {
        com.poseidon.ad.common.toolkit.logInfo("Automation") {
            "Skip load, ordinary preload is still gated"
        }
        return
    }
    CeilAutomation.start()
    FloorAutomation.start()
}

internal fun PlacementCfg.asPlatformAdLoadParameter(sceneName: String): Any {
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
