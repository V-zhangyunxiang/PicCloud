package com.poseidon.ad.core.strategy

import com.poseidon.ad.common.toolkit.ResultCallback
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.main.adinfo.AD_TYPE_BANNER
import com.poseidon.ad.main.adinfo.AD_TYPE_INTERSTITIAL
import com.poseidon.ad.main.adinfo.AD_TYPE_NATIVE
import com.poseidon.ad.main.adinfo.AD_TYPE_OPEN
import com.poseidon.ad.main.adinfo.AD_TYPE_REWARDED
import com.poseidon.ad.main.adinfo.hasCacheAd
import com.poseidon.ad.main.preload.notification.NotificationSplashAdManager
import com.poseidon.ad.main.preload.notification.isNotificationSplashAdPreloadScene
import com.poseidon.ad.main.preload.notification.isNotificationSplashAdShowScene
import com.poseidon.ad.main.preload.share.CeilAutomation
import com.poseidon.ad.main.preload.share.FloorAutomation
import com.poseidon.ad.main.preload.share.SharedCeilAdPool
import com.poseidon.ad.main.preload.share.SharedFloorAdPool
import com.poseidon.ad.main.preload.special.SpecificAdSceneCacheBackup
import com.poseidon.ad.main.strategy.common.PoseidonConfig
import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import com.poseidon.ad.main.processor.PoseidonAPIProcessor
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor
import com.poseidon.ad.main.processor.PlatformHasCacheParameter

/**
 * StrategyHub centralizes scene/strategy evaluation logic.
 * StrategyHub 负责集中处理策略、场景与缓存判断，减少 API 外壳的复杂度。
 */
object StrategyHub {

    fun hasCacheAd(parameter: PlatformHasCacheParameter): Boolean {
        val sceneName = parameter.sceneName
        val checkingSharedFloorAdPool = parameter.checkingSharedFloorAdPool

        fun logDecision(
            sceneAdType: Int?,
            strategyId: Int?,
            strategyHasCache: Boolean?,
            notificationSplashHasCache: Boolean?,
            ceilAutomationCount: Int?,
            sharedCeilCount: Int?,
            backupHasCache: Boolean?,
            floorAutomationCount: Int?,
            sharedFloorCount: Int?,
            finalResult: Boolean,
            resolveMessage: String? = null,
        ) {
            logInfo(tag = "StrategyHub") {
                "hasCacheAd(sceneName=$sceneName, checkingSharedFloorAdPool=$checkingSharedFloorAdPool, sceneAdType=$sceneAdType, strategyId=$strategyId, strategyHasCache=$strategyHasCache, notificationSplashHasCache=$notificationSplashHasCache, ceilAutomationCount=$ceilAutomationCount, sharedCeilCount=$sharedCeilCount, backupHasCache=$backupHasCache, floorAutomationCount=$floorAutomationCount, sharedFloorCount=$sharedFloorCount, finalResult=$finalResult, resolveMessage=$resolveMessage)"
            }
        }

        if (isNotificationSplashAdPreloadScene(sceneName = sceneName) || isNotificationSplashAdShowScene(
                sceneName = sceneName
            )
        ) {
            val notificationSplashHasCache = NotificationSplashAdManager.hasCacheAd().data
            val ceilAutomationCount = CeilAutomation.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL }
            val sharedCeilCount = SharedCeilAdPool.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL }
            val fallbackStrategyHasCache = PoseidonConfig.Companion.get()?.notificationSplashAd?.fallbackStrategyId?.let {
                hasCacheAd(
                    strategyId = it,
                    number = null
                )
            } ?: false
            val backupHasCache = SpecificAdSceneCacheBackup.hasCacheAd(sceneName = parameter.sceneName)
            val floorAutomationCount = if (checkingSharedFloorAdPool) {
                FloorAutomation.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL }
            } else {
                0
            }
            val sharedFloorCount = if (checkingSharedFloorAdPool) {
                SharedFloorAdPool.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL }
            } else {
                0
            }
            val finalResult = notificationSplashHasCache
                    || ceilAutomationCount > 0
                    || sharedCeilCount > 0
                    || fallbackStrategyHasCache
                    || backupHasCache
                    || (checkingSharedFloorAdPool && floorAutomationCount > 0)
                    || (checkingSharedFloorAdPool && sharedFloorCount > 0)
            logDecision(
                sceneAdType = AD_TYPE_INTERSTITIAL,
                strategyId = null,
                strategyHasCache = fallbackStrategyHasCache,
                notificationSplashHasCache = notificationSplashHasCache,
                ceilAutomationCount = ceilAutomationCount,
                sharedCeilCount = sharedCeilCount,
                backupHasCache = backupHasCache,
                floorAutomationCount = floorAutomationCount,
                sharedFloorCount = sharedFloorCount,
                finalResult = finalResult,
            )
            return finalResult
        }

        return when (val result = PoseidonAPIProcessor.resolveScene(sceneName)) {
            is ResultCallback.Failure -> {
                logDecision(
                    sceneAdType = null,
                    strategyId = null,
                    strategyHasCache = null,
                    notificationSplashHasCache = null,
                    ceilAutomationCount = null,
                    sharedCeilCount = null,
                    backupHasCache = null,
                    floorAutomationCount = null,
                    sharedFloorCount = null,
                    finalResult = false,
                    resolveMessage = "resolveScene failure: code=${result.code}, message=${result.message}"
                )
                false
            }
            is ResultCallback.Success -> {
                val (_, sceneToStrategyPair) = result.value
                val scene = sceneToStrategyPair.first

                if (scene.adType == AD_TYPE_BANNER) {
                    val message =
                        "hasCacheAd API is not supported for banner scene: sceneName=${scene.name}"
                    if (PoseidonSetupBaseProcessor.poseidonSetupProcessor().debug) {
                        error(message)
                    } else {
                        ErrorDetectionReporter(
                            name = "HasCacheAdError",
                            extra = message
                        )
                        return false
                    }
                }

                val strategy = sceneToStrategyPair.second
                if (strategy == null) {
                    logDecision(
                        sceneAdType = scene.adType,
                        strategyId = null,
                        strategyHasCache = null,
                        notificationSplashHasCache = null,
                        ceilAutomationCount = null,
                        sharedCeilCount = null,
                        backupHasCache = null,
                        floorAutomationCount = null,
                        sharedFloorCount = null,
                        finalResult = false,
                        resolveMessage = "resolveScene success but strategy is null"
                    )
                    return false
                }

                val strategyHasCache = hasCacheAd(strategyId = strategy.id, number = null)
                when (scene.adType) {
                    AD_TYPE_INTERSTITIAL -> {
                        val ceilAutomationCount = CeilAutomation.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL }
                        val sharedCeilCount = SharedCeilAdPool.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL }
                        val backupHasCache = SpecificAdSceneCacheBackup.hasCacheAd(sceneName = parameter.sceneName)
                        val floorAutomationCount = if (checkingSharedFloorAdPool) {
                            FloorAutomation.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL }
                        } else {
                            0
                        }
                        val sharedFloorCount = if (checkingSharedFloorAdPool) {
                            SharedFloorAdPool.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL }
                        } else {
                            0
                        }
                        val finalResult = strategyHasCache
                                || ceilAutomationCount > 0
                                || sharedCeilCount > 0
                                || backupHasCache
                                || (checkingSharedFloorAdPool && floorAutomationCount > 0)
                                || (checkingSharedFloorAdPool && sharedFloorCount > 0)
                        logDecision(
                            sceneAdType = scene.adType,
                            strategyId = strategy.id,
                            strategyHasCache = strategyHasCache,
                            notificationSplashHasCache = null,
                            ceilAutomationCount = ceilAutomationCount,
                            sharedCeilCount = sharedCeilCount,
                            backupHasCache = backupHasCache,
                            floorAutomationCount = floorAutomationCount,
                            sharedFloorCount = sharedFloorCount,
                            finalResult = finalResult,
                        )
                        finalResult
                    }

                    AD_TYPE_OPEN -> {
                        val ceilAutomationCount = CeilAutomation.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL }
                        val sharedCeilCount = SharedCeilAdPool.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL }
                        val backupHasCache = SpecificAdSceneCacheBackup.hasCacheAd(sceneName = parameter.sceneName)
                        val floorAutomationCount = if (checkingSharedFloorAdPool) {
                            FloorAutomation.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL }
                        } else {
                            0
                        }
                        val sharedFloorCount = if (checkingSharedFloorAdPool) {
                            SharedFloorAdPool.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL }
                        } else {
                            0
                        }
                        val finalResult = strategyHasCache
                                || sharedCeilCount > 0
                                || ceilAutomationCount > 0
                                || backupHasCache
                                || (checkingSharedFloorAdPool && floorAutomationCount > 0)
                                || (checkingSharedFloorAdPool && sharedFloorCount > 0)
                        logDecision(
                            sceneAdType = scene.adType,
                            strategyId = strategy.id,
                            strategyHasCache = strategyHasCache,
                            notificationSplashHasCache = null,
                            ceilAutomationCount = ceilAutomationCount,
                            sharedCeilCount = sharedCeilCount,
                            backupHasCache = backupHasCache,
                            floorAutomationCount = floorAutomationCount,
                            sharedFloorCount = sharedFloorCount,
                            finalResult = finalResult,
                        )
                        finalResult
                    }

                    AD_TYPE_NATIVE -> {
                        val ceilAutomationCount = CeilAutomation.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE }
                        val sharedCeilCount = SharedCeilAdPool.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE }
                        val floorAutomationCount = if (checkingSharedFloorAdPool) {
                            FloorAutomation.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE }
                        } else {
                            0
                        }
                        val sharedFloorCount = if (checkingSharedFloorAdPool) {
                            SharedFloorAdPool.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE }
                        } else {
                            0
                        }
                        val finalResult = strategyHasCache
                                || sharedCeilCount > 0
                                || ceilAutomationCount > 0
                                || (checkingSharedFloorAdPool && floorAutomationCount > 0)
                                || (checkingSharedFloorAdPool && sharedFloorCount > 0)
                        logDecision(
                            sceneAdType = scene.adType,
                            strategyId = strategy.id,
                            strategyHasCache = strategyHasCache,
                            notificationSplashHasCache = null,
                            ceilAutomationCount = ceilAutomationCount,
                            sharedCeilCount = sharedCeilCount,
                            backupHasCache = null,
                            floorAutomationCount = floorAutomationCount,
                            sharedFloorCount = sharedFloorCount,
                            finalResult = finalResult,
                        )
                        finalResult
                    }

                    AD_TYPE_REWARDED -> {
                        val ceilAutomationCount = CeilAutomation.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL || it.ad.getAdType() == AD_TYPE_REWARDED }
                        val sharedCeilCount = SharedCeilAdPool.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL || it.ad.getAdType() == AD_TYPE_REWARDED }
                        val backupHasCache = SpecificAdSceneCacheBackup.hasCacheAd(sceneName = parameter.sceneName)
                        val floorAutomationCount = if (checkingSharedFloorAdPool) {
                            FloorAutomation.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL || it.ad.getAdType() == AD_TYPE_REWARDED }
                        } else {
                            0
                        }
                        val sharedFloorCount = if (checkingSharedFloorAdPool) {
                            SharedFloorAdPool.cacheCount { it.ad.getAdType() == AD_TYPE_NATIVE || it.ad.getAdType() == AD_TYPE_INTERSTITIAL || it.ad.getAdType() == AD_TYPE_REWARDED }
                        } else {
                            0
                        }
                        val finalResult = strategyHasCache
                                || ceilAutomationCount > 0
                                || sharedCeilCount > 0
                                || backupHasCache
                                || (checkingSharedFloorAdPool && floorAutomationCount > 0)
                                || (checkingSharedFloorAdPool && sharedFloorCount > 0)
                        logDecision(
                            sceneAdType = scene.adType,
                            strategyId = strategy.id,
                            strategyHasCache = strategyHasCache,
                            notificationSplashHasCache = null,
                            ceilAutomationCount = ceilAutomationCount,
                            sharedCeilCount = sharedCeilCount,
                            backupHasCache = backupHasCache,
                            floorAutomationCount = floorAutomationCount,
                            sharedFloorCount = sharedFloorCount,
                            finalResult = finalResult,
                        )
                        finalResult
                    }

                    else -> {
                        logDecision(
                            sceneAdType = scene.adType,
                            strategyId = strategy.id,
                            strategyHasCache = strategyHasCache,
                            notificationSplashHasCache = null,
                            ceilAutomationCount = null,
                            sharedCeilCount = null,
                            backupHasCache = null,
                            floorAutomationCount = null,
                            sharedFloorCount = null,
                            finalResult = strategyHasCache,
                            resolveMessage = "unsupported scene ad type"
                        )
                        strategyHasCache
                    }
                }
            }
        }
    }
}
