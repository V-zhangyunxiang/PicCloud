package com.poseidon.ad.main.preload.notification

import androidx.annotation.AnyThread
import com.poseidon.ad.api.PoseidonSetupProcessor
import com.poseidon.ad.common.storage.sp.Key
import com.poseidon.ad.common.storage.sp.SpBatchEntry
import com.poseidon.ad.common.storage.sp.computeDailyCounterPlan
import com.poseidon.ad.common.toolkit.ExtraDO
import com.poseidon.ad.common.toolkit.ResultCallback
import com.poseidon.ad.common.toolkit.StringHelper
import com.poseidon.ad.common.toolkit.TimeHelper
import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.common.toolkit.isNetworkAvailable
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.main.adinfo.CacheAd
import com.poseidon.ad.main.adinfo.InterstitialAdBase
import com.poseidon.ad.main.adinfo.destroySafely
import com.poseidon.ad.main.adinfo.isBelowEcpmBar
import com.poseidon.ad.main.adinfo.isExpired
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.InterstitialParameter
import com.poseidon.ad.main.load.createInterstitialAdLoader
import com.poseidon.ad.main.preload.share.tryPreloadSharedAdWhenNotificationShownOrClick
import com.poseidon.ad.main.processor.PlatformInterstitialAdLoadParameter
import com.poseidon.ad.main.preload.new.shouldBlockOrdinaryPreload
import com.poseidon.ad.main.processor.PreloadMemoryGuard
import com.poseidon.ad.main.strategy.common.NotificationSplashAdCfg
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.restrict.show.AdShowLimit
import com.poseidon.ad.restrict.show.currentDateFormatWithYearMonthDay
import com.poseidon.ad.statistics.biz.NotificationSplashAdPreloadReporter
import kotlinx.atomicfu.atomic

internal object NotificationSplashAdManager {

    private const val TAG = "NotificationSplashAdManager"

    private fun callOnNotificationClicked() {
        logInfo(TAG) { ">>>>>>>>>>>>>>>>Notification Clicked<<<<<<<<<<<<<<<<" }
        val sp = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        sp.writeBatch(listOf(
            SpBatchEntry.IntValue(key = Key.NotificationSplashAdConsecutiveLoadFailuresCount, value = 0),
            SpBatchEntry.IntValue(key = Key.NotificationSplashAdFillCount, value = 0),
        ))
    }

    fun preloadAd(loadParameter: PlatformInterstitialAdLoadParameter) {
        if (shouldBlockOrdinaryPreload(sceneName = loadParameter.sceneName)) {
            logInfo(TAG) {
                "Skip notification preload because ordinary preload is still gated: " +
                    "sceneName=${loadParameter.sceneName}"
            }
            return
        }
        tryPreloadSharedAdWhenNotificationShownOrClick()
        val sessionId = StringHelper.newSessionId()
        val sceneName = loadParameter.sceneName
        NotificationSplashAdPreloadReporter(
            sceneName = sceneName,
            sessionId = sessionId,
            type = NotificationSplashAdPreloadReporter.TYPE_TRIGGER,
        ).analyze()

        val configuration = PoseidonConfig.get()
        val notificationSplashAd = configuration?.notificationSplashAd
        val (configurationPassed, notificationSplashAdConfigCheckMessage) = notificationSplashAd.check()
        logInfo(TAG) { "[Configuration Check] configurationPassed=$configurationPassed, notificationSplashAdConfigCheckMessage=$notificationSplashAdConfigCheckMessage" }
        NotificationSplashAdPreloadReporter(
            sceneName = sceneName,
            sessionId = sessionId,
            type = NotificationSplashAdPreloadReporter.TYPE_CONFIGURATION_CHECKING,
            extra = notificationSplashAdConfigCheckMessage,
            passed = configurationPassed,
        ).analyze()
        if (!configurationPassed) return

        if (notificationSplashAd!!.preloadAdWhenNotificationClickedSceneName == sceneName) {
            callOnNotificationClicked()
        }
        if (notificationSplashAd.preloadAdWhenNotificationDisplayedSceneName == sceneName) {
            logInfo(TAG) { ">>>>>>>>>>>>>>>>Notification Displayed<<<<<<<<<<<<<<<<" }
        }

        val canLoadAdByAdImpressionLimit = AdShowLimit.canLoadAd()
        logInfo(TAG) { "[AdImpressionLimit Check] canLoadAdByAdImpressionLimit=$canLoadAdByAdImpressionLimit" }
        NotificationSplashAdPreloadReporter(
            sceneName = sceneName,
            sessionId = sessionId,
            type = NotificationSplashAdPreloadReporter.TYPE_AD_IMPRESSION_LIMIT_CHECKING,
            passed = canLoadAdByAdImpressionLimit,
        ).analyze()
        if (!canLoadAdByAdImpressionLimit) return

        val (hasCacheAd, hasCacheAdMessage) = hasCacheAd()
        logInfo(TAG) { "[Cache Check] hasCacheAd=$hasCacheAd, message=$hasCacheAdMessage" }
        NotificationSplashAdPreloadReporter(
            sceneName = sceneName,
            sessionId = sessionId,
            type = NotificationSplashAdPreloadReporter.TYPE_CACHE_CHECKING,
            extra = hasCacheAdMessage,
            passed = !hasCacheAd,
        ).analyze()
        if (hasCacheAd) return

        if (notificationSplashAd.preloadAdWhenNotificationDisplayedSceneName == sceneName) {
            val sp = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
            val currentDate = currentDateFormatWithYearMonthDay()
            val failurePlan = computeDailyCounterPlan(
                savedDate = sp.getString(Key.NotificationSplashAdConsecutiveLoadFailuresCountLastDate, null),
                currentDate = currentDate,
                savedCounter = sp.getInt(Key.NotificationSplashAdConsecutiveLoadFailuresCount, 0),
                applyIncrement = false,
            )
            val failureUpdates = mutableListOf<SpBatchEntry>()
            if (failurePlan.needsPersistDate) failureUpdates.add(SpBatchEntry.StringValue(key = Key.NotificationSplashAdConsecutiveLoadFailuresCountLastDate, value = failurePlan.dateValue))
            if (failurePlan.needsPersistCounter) failureUpdates.add(SpBatchEntry.IntValue(key = Key.NotificationSplashAdConsecutiveLoadFailuresCount, value = failurePlan.counterValue))
            if (failureUpdates.isNotEmpty()) sp.writeBatch(failureUpdates)
            val adConsecutiveLoadFailuresCount = failurePlan.counterValue
            val maxAdConsecutiveLoadFailuresCountFromRemote = getMaxAdConsecutiveLoadFailuresCountFromRemote()
            logInfo(TAG) { "[MaxAdConsecutiveLoadFailuresCount Check] adConsecutiveLoadFailuresCount=$adConsecutiveLoadFailuresCount, maxAdConsecutiveLoadFailuresCountFromRemote=$maxAdConsecutiveLoadFailuresCountFromRemote" }
            NotificationSplashAdPreloadReporter(
                sceneName = sceneName,
                sessionId = sessionId,
                type = NotificationSplashAdPreloadReporter.TYPE_CONSECUTIVE_LOAD_FAILURES_CHECKING,
                extra = "adConsecutiveLoadFailuresCount=$adConsecutiveLoadFailuresCount, maxAdConsecutiveLoadFailuresCountFromRemote=$maxAdConsecutiveLoadFailuresCountFromRemote",
                passed = adConsecutiveLoadFailuresCount < maxAdConsecutiveLoadFailuresCountFromRemote,
            ).analyze()
            if (adConsecutiveLoadFailuresCount >= maxAdConsecutiveLoadFailuresCountFromRemote) return

            val fillPlan = computeDailyCounterPlan(
                savedDate = sp.getString(Key.NotificationSplashAdFillCountLastDate, null),
                currentDate = currentDate,
                savedCounter = sp.getInt(Key.NotificationSplashAdFillCount, 0),
                applyIncrement = false,
            )
            val fillUpdates = mutableListOf<SpBatchEntry>()
            if (fillPlan.needsPersistDate) fillUpdates.add(SpBatchEntry.StringValue(key = Key.NotificationSplashAdFillCountLastDate, value = fillPlan.dateValue))
            if (fillPlan.needsPersistCounter) fillUpdates.add(SpBatchEntry.IntValue(key = Key.NotificationSplashAdFillCount, value = fillPlan.counterValue))
            if (fillUpdates.isNotEmpty()) sp.writeBatch(fillUpdates)
            val adFillCount = fillPlan.counterValue
            val maxAdFilledUnusedCount = getMaxAdFilledUnusedCountFromRemote()
            logInfo(TAG) { "[MaxAdFilledUnusedCount Checking] maxAdFilledUnusedCount=$maxAdFilledUnusedCount, adFillCount=$adFillCount" }
            NotificationSplashAdPreloadReporter(
                sceneName = sceneName,
                sessionId = sessionId,
                type = NotificationSplashAdPreloadReporter.TYPE_AD_FILL_UNUSED_CHECKING,
                extra = "maxAdFilledUnusedCount=$maxAdFilledUnusedCount, adFillCount=$adFillCount",
                passed = adFillCount < maxAdFilledUnusedCount,
            ).analyze()
            if (adFillCount >= maxAdFilledUnusedCount) return

            val (isRequestIntervalTooShort, isRequestIntervalTooShortMessage) = isRequestIntervalTooShort()
            logInfo(TAG) { "[RequestInterval Checking] isRequestIntervalTooShort=$isRequestIntervalTooShort, message=$isRequestIntervalTooShortMessage" }
            NotificationSplashAdPreloadReporter(
                sceneName = sceneName,
                sessionId = sessionId,
                type = NotificationSplashAdPreloadReporter.TYPE_REQUEST_INTERVAL_CHECKING,
                extra = isRequestIntervalTooShortMessage,
                passed = !isRequestIntervalTooShort,
            ).analyze()
            if (isRequestIntervalTooShort) return
        }

        if (PreloadMemoryGuard.shouldSkipPreload(PoseidonSetupProcessor.getInstance().application, sceneName = sceneName, loadCallback = loadParameter.adLoadCallback)) {
            return
        }

        val networkAvailable = isNetworkAvailable()
        logInfo(TAG) { "[Network Check] networkAvailable=$networkAvailable" }
        NotificationSplashAdPreloadReporter(
            sceneName = sceneName,
            sessionId = sessionId,
            type = NotificationSplashAdPreloadReporter.TYPE_NETWORK_CHECKING,
            extra = null,
            passed = networkAvailable,
        ).analyze()
        if (!networkAvailable) return

        logInfo(TAG) { "Start to load ad..." }
        NotificationSplashAdPreloadReporter(
            sceneName = sceneName,
            sessionId = sessionId,
            type = NotificationSplashAdPreloadReporter.TYPE_LOAD,
            extra = notificationSplashAd.toString(),
        ).analyze()
        loadAd(loadParameter = loadParameter, notificationSplashAd = notificationSplashAd, poseidonConfigVersion = configuration.version)
    }

    private val cacheAdAtomic = atomic<CacheAd<InterstitialAdBase>?>(null)

    private val adLoadingAtomic = atomic(false)

    /**
     * Load ad, this method is thread-safe, ensuring that only one ad is being loaded at the same time
     */
    @AnyThread
    private fun loadAd(loadParameter: PlatformInterstitialAdLoadParameter, notificationSplashAd: NotificationSplashAdCfg, poseidonConfigVersion: VersionHelper?) {
        if (!adLoadingAtomic.value) {
            val r = adLoadingAtomic.compareAndSet(expect = false, update = true)
            if (!r) {
                return
            }
            // Load ad, this ensures that only one thread can enter
            val network = notificationSplashAd.mediation
            val parameter = InterstitialParameter(
                adUnitId = notificationSplashAd.placementId,
                startLoadTimeInMillis = TimeHelper.currentTimeInMillis(),
                network = network,
                sessionId = StringHelper.newSessionId(),
                sceneName = notificationSplashAd.showAdSceneName,
                poseidonConfigVersion = poseidonConfigVersion
            )
            when (val result = createInterstitialAdLoader(network = network, parameter = parameter)) {
                is ResultCallback.Failure -> {
                    increaseAdConsecutiveLoadFailuresCount()
                    adLoadingAtomic.compareAndSet(expect = true, update = false)
                }
                is ResultCallback.Success -> {
                    val callback = object : InnerAdLoadCallback<InterstitialAdBase> {
                        override fun onAdLoadFailed(code: Int, message: String) {
                            increaseAdConsecutiveLoadFailuresCount()
                            adLoadingAtomic.compareAndSet(expect = true, update = false)
                        }

                        override fun onAdLoadSuccess(ad: InterstitialAdBase) {
                            setAdConsecutiveLoadFailuresCount(count = 0)
                            increaseAdFillCount()
                            val cacheAd = CacheAd(
                                ad = ad,
                                adUnitId = parameter.adUnitId,
                                loadSuccessTimeInMillis = TimeHelper.currentTimeInMillis(),
                                number = 1,
                            )
                            setCacheAd(cacheAd)

                            adLoadingAtomic.compareAndSet(expect = true, update = false)
                        }
                    }
                    if (notificationSplashAd.preloadAdWhenNotificationDisplayedSceneName == loadParameter.sceneName) {
                        PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference.putLong(
                            Key.NotificationSplashAdLastRequestTimeInMillis,
                            TimeHelper.currentTimeInMillis()
                        )
                    }
                    val adLoader = result.value
                    adLoader.load(loadParameter = loadParameter, loadCallback = callback)
                }
            }
        } else {
            // Currently loading, do not load repeatedly
            // Nothing to do
        }
    }

    private fun getAdConsecutiveLoadFailuresCount() : Int {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        return sharedPreference.getInt(Key.NotificationSplashAdConsecutiveLoadFailuresCount, 0)
    }

    private fun getAdConsecutiveLoadFailuresCountLastDate(): String? {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        return sharedPreference.getString(Key.NotificationSplashAdConsecutiveLoadFailuresCountLastDate, null)
    }

    private fun updateAdConsecutiveLoadFailuresCountLastDate(currentDate: String) {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        sharedPreference.putString(Key.NotificationSplashAdConsecutiveLoadFailuresCountLastDate, currentDate)
    }

    private fun getMaxAdConsecutiveLoadFailuresCountFromRemote(): Int {
        return PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig
            .getString("notification_splash_ad_max_consecutive_load_failures_count")
            .toIntOrNull() ?: 6
    }

    private fun increaseAdConsecutiveLoadFailuresCount() {
        val sp = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val plan = computeDailyCounterPlan(
            savedDate = sp.getString(Key.NotificationSplashAdConsecutiveLoadFailuresCountLastDate, null),
            currentDate = currentDateFormatWithYearMonthDay(),
            savedCounter = sp.getInt(Key.NotificationSplashAdConsecutiveLoadFailuresCount, 0),
            applyIncrement = true,
        )
        val updates = mutableListOf<SpBatchEntry>()
        if (plan.needsPersistDate) updates.add(SpBatchEntry.StringValue(key = Key.NotificationSplashAdConsecutiveLoadFailuresCountLastDate, value = plan.dateValue))
        if (plan.needsPersistCounter) updates.add(SpBatchEntry.IntValue(key = Key.NotificationSplashAdConsecutiveLoadFailuresCount, value = plan.counterValue))
        if (updates.isNotEmpty()) sp.writeBatch(updates)
    }

    private fun setAdConsecutiveLoadFailuresCount(count: Int) {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        sharedPreference.putInt(Key.NotificationSplashAdConsecutiveLoadFailuresCount, count)
    }

    private fun increaseAdFillCount() {
        val sp = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val plan = computeDailyCounterPlan(
            savedDate = sp.getString(Key.NotificationSplashAdFillCountLastDate, null),
            currentDate = currentDateFormatWithYearMonthDay(),
            savedCounter = sp.getInt(Key.NotificationSplashAdFillCount, 0),
            applyIncrement = true,
        )
        val updates = mutableListOf<SpBatchEntry>()
        if (plan.needsPersistDate) updates.add(SpBatchEntry.StringValue(key = Key.NotificationSplashAdFillCountLastDate, value = plan.dateValue))
        if (plan.needsPersistCounter) updates.add(SpBatchEntry.IntValue(key = Key.NotificationSplashAdFillCount, value = plan.counterValue))
        if (updates.isNotEmpty()) sp.writeBatch(updates)
    }

    private fun getAdFillCount() : Int {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        return sharedPreference.getInt(Key.NotificationSplashAdFillCount, 0)
    }

    private fun getAdFillCountLastDate(): String? {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        return sharedPreference.getString(Key.NotificationSplashAdFillCountLastDate, null)
    }

    private fun updateAdFillCountLastDate(currentDate: String) {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        sharedPreference.putString(Key.NotificationSplashAdFillCountLastDate, currentDate)
    }

    private fun getMaxAdFilledUnusedCountFromRemote(): Int {
        return PoseidonSetupBaseProcessor.poseidonSetupProcessor().remoteConfig
            .getString("notification_splash_ad_max_ad_filled_unused_count")
            .toIntOrNull() ?: 30
    }

    private fun setAdFillCount(count: Int) {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        sharedPreference.putInt(Key.NotificationSplashAdFillCount, count)
    }

    private fun isRequestIntervalTooShort(): ExtraDO<Boolean, String> {
        val sharedPreference = PoseidonSetupBaseProcessor.poseidonSetupProcessor().sharedPreference
        val lastRequestTimeInMillis =
            sharedPreference.getLong(Key.NotificationSplashAdLastRequestTimeInMillis, -1L)
        val currentTimeInMillis = TimeHelper.currentTimeInMillis()

        return if (lastRequestTimeInMillis == -1L) {
            ExtraDO.stringExtra(data = false, extra = "First trigger, request interval condition passed")
        } else {
            if (currentTimeInMillis >= lastRequestTimeInMillis) {
                val interval = currentTimeInMillis - lastRequestTimeInMillis // >=0
                check(interval >= 0)
                val minInterval = 1/*min*/ * 60 * 1000
                if (interval <= minInterval) {
                    ExtraDO.stringExtra(data = true, extra = "The time interval between two requests(${interval}ms) is less than or equal to ${minInterval}ms, the request interval limit is not passed")
                } else {
                    ExtraDO.stringExtra(data = false, extra = "The time interval between two requests(${interval}ms) is greater than ${minInterval}ms, the request interval limit is passed")
                }
            } else {
                ExtraDO.stringExtra(data = true, extra = "The user modified the system time or changed the system configuration (lastRequestTimeInMillis=${lastRequestTimeInMillis}, currentTimeInMillis=${currentTimeInMillis}), which is an abnormal behavior. The request interval limit is not passed.")
            }
        }
    }

    fun consumeCacheAd() : CacheAd<InterstitialAdBase>? {
        while (true) {
            val cacheAd = cacheAdAtomic.value ?: return null
            if (cacheAd.isExpired()) {
                if (cacheAdAtomic.compareAndSet(expect = cacheAd, update = null)) {
                    cacheAd.destroySafely(reason = "$TAG consumeCacheAd")
                }
                continue
            }
            if (cacheAdAtomic.compareAndSet(expect = cacheAd, update = null)) {
                return cacheAd
            }
        }
    }

    fun setCacheAd(cacheAd: CacheAd<InterstitialAdBase>) {
        cacheAdAtomic.value = cacheAd
    }

    internal fun scavengeBelowEcpm(minEcpmToKeep: Double, reason: String): Int {
        val cacheAd = cacheAdAtomic.value ?: return 0
        if (!isBelowEcpmBar(cacheAd.cachedEcpm, minEcpmToKeep)) return 0
        return if (cacheAdAtomic.compareAndSet(expect = cacheAd, update = null)) {
            cacheAd.destroySafely(reason = "$TAG $reason")
            1
        } else {
            0
        }
    }

    internal fun evictExpiredAdNow(reason: String) {
        val cacheAd = cacheAdAtomic.value ?: return
        if (cacheAd.isExpired() && cacheAdAtomic.compareAndSet(expect = cacheAd, update = null)) {
            cacheAd.destroySafely(reason = "$TAG $reason")
        }
    }

    fun hasCacheAd(): ExtraDO<Boolean, String> {
        val cacheAd = cacheAdAtomic.value
        return if (cacheAd == null) {
            ExtraDO.stringExtra(data = false, extra = "There is no available cache ad")
        } else {
            if (cacheAd.isExpired()) {
                if (cacheAdAtomic.compareAndSet(expect = cacheAd, update = null)) {
                    cacheAd.destroySafely(reason = "$TAG hasCacheAd")
                }
                ExtraDO.stringExtra(data = false, extra = "There is a cached ad but it has expired")
            } else {
                ExtraDO.stringExtra(data = true, extra = "There is a cached ad that is not expired: $cacheAd")
            }
        }
    }
}
