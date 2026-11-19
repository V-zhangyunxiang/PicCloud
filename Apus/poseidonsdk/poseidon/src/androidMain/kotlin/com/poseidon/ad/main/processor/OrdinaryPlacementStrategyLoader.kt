package com.poseidon.ad.main.processor

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import com.poseidon.ad.api.AdLoadListener
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.InterstitialParameter
import com.poseidon.ad.main.load.NativeParameter
import com.poseidon.ad.main.load.OpenParameter
import com.poseidon.ad.main.load.RewardedParameter
import com.poseidon.ad.main.load.createInterstitialAdLoader
import com.poseidon.ad.main.load.createNativeAdLoader
import com.poseidon.ad.main.load.createOpenAdLoader
import com.poseidon.ad.main.load.createRewardedAdLoader
import com.poseidon.ad.main.adinfo.AD_TYPE_INTERSTITIAL
import com.poseidon.ad.main.adinfo.AD_TYPE_NATIVE
import com.poseidon.ad.main.adinfo.AD_TYPE_OPEN
import com.poseidon.ad.main.adinfo.AD_TYPE_REWARDED
import com.poseidon.ad.main.adinfo.CacheAd
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_AD_UNIT
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNKNOWN
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNSUPPORTED_AD_TYPE
import com.poseidon.ad.main.adinfo.addCacheAd
import com.poseidon.ad.main.adinfo.callOnAdLoadStart
import com.poseidon.ad.main.adinfo.hasCacheAd
import com.poseidon.ad.main.adinfo.InterstitialAdBase
import com.poseidon.ad.main.adinfo.NativeAdBase
import com.poseidon.ad.main.adinfo.OpenAdBase
import com.poseidon.ad.main.adinfo.RewardedAdBase
import com.poseidon.ad.main.preload.new.NewUserScopedBidderRegistry
import com.poseidon.ad.main.strategy.common.SceneCfg
import com.poseidon.ad.main.strategy.common.StrategyCfg
import com.poseidon.ad.common.toolkit.ResultCallback
import com.poseidon.ad.common.toolkit.StringHelper
import com.poseidon.ad.common.toolkit.TimeHelper
import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.common.toolkit.logInfo

internal object OrdinaryPlacementStrategyLoader {

    fun <PlatformAdLoadParameter> load(
        sessionId: String,
        loadParameter: PlatformAdLoadParameter,
        scene: SceneCfg,
        strategy: StrategyCfg,
        poseidonConfigVersion: VersionHelper?,
        loadCallback: AdLoadListener?,
    ) {
        callOnAdLoadStart(strategy)
        val placements = strategy.placements
        val coordinator = StrategyLoadCoordinator(
            totalCount = placements.size,
            loadCallback = loadCallback,
        )
        placements.forEachIndexed { index, placement ->
            loadIndexedPlacement(
                sessionId = sessionId,
                loadParameter = loadParameter,
                scene = scene,
                strategy = strategy,
                placement = placement,
                number = index + 1,
                poseidonConfigVersion = poseidonConfigVersion,
                coordinator = coordinator,
            )
        }
    }

    private fun <PlatformAdLoadParameter> loadIndexedPlacement(
        sessionId: String,
        loadParameter: PlatformAdLoadParameter,
        scene: SceneCfg,
        strategy: StrategyCfg,
        placement: com.poseidon.ad.main.strategy.common.PlacementCfg,
        number: Int,
        poseidonConfigVersion: VersionHelper?,
        coordinator: StrategyLoadCoordinator,
    ) {
        val adUnitId = placement.id
        if (adUnitId.isEmpty()) {
            coordinator.onBranchFailure(
                code = ERROR_CODE_NULL_AD_UNIT,
                message = "Ad unit ID is empty: sceneName=${scene.name}, number=$number, placement=$placement"
            )
            return
        }

        if (hasCacheAd(adUnitId = adUnitId, number = number)) {
            NewUserScopedBidderRegistry.registerLoadedStrategy(scene = scene, strategy = strategy)
            coordinator.onBranchSuccess()
            return
        }

        logInfo("OrdinaryPlacementStrategyLoader") {
            "loadIndexedPlacement: scene=$scene, adType=${placement.adType}, number=$number, placement=$placement"
        }

        when (placement.adType) {
            AD_TYPE_OPEN -> {
                when (val result = createOpenAdLoader(
                    network = placement.mediation,
                    parameter = OpenParameter(
                        adUnitId = placement.id,
                        startLoadTimeInMillis = TimeHelper.currentTimeInMillis(),
                        network = placement.mediation,
                        sessionId = sessionId,
                        sceneName = scene.name,
                        poseidonConfigVersion = poseidonConfigVersion
                    )
                )) {
                    is ResultCallback.Failure -> coordinator.onBranchFailure(result.code, result.message)
                    is ResultCallback.Success -> {
                        val callback = object : InnerAdLoadCallback<OpenAdBase> {
                            override fun onAdLoadFailed(code: Int, message: String) {
                                coordinator.onBranchFailure(code, message)
                            }

                            override fun onAdLoadSuccess(ad: OpenAdBase) {
                                addCacheAd(
                                    CacheAd(
                                        ad = ad,
                                        adUnitId = adUnitId,
                                        loadSuccessTimeInMillis = TimeHelper.currentTimeInMillis(),
                                        number = number,
                                    )
                                )
                                NewUserScopedBidderRegistry.registerLoadedStrategy(
                                    scene = scene,
                                    strategy = strategy,
                                )
                                coordinator.onBranchSuccess()
                            }
                        }
                        val lp = whenPlatformAdLoadParameter(
                            loadParameter = loadParameter,
                            interstitialAdLoadParameterConverter = PlatformInterstitialAdLoadParameter::toOpenAdLoadParameter,
                            rewardedAdLoadParameterConverter = PlatformRewardedAdLoadParameter::toOpenAdLoadParameter,
                            nativeAdLoadParameterConverter = PlatformNativeAdLoadParameter::toOpenAdLoadParameter,
                            openAdLoadParameterConverter = { this }
                        )
                        result.value.load(loadParameter = lp, loadCallback = callback)
                    }
                }
            }

            AD_TYPE_INTERSTITIAL -> {
                when (val result = createInterstitialAdLoader(
                    network = placement.mediation,
                    parameter = InterstitialParameter(
                        adUnitId = placement.id,
                        startLoadTimeInMillis = TimeHelper.currentTimeInMillis(),
                        network = placement.mediation,
                        sessionId = sessionId,
                        sceneName = scene.name,
                        poseidonConfigVersion = poseidonConfigVersion
                    )
                )) {
                    is ResultCallback.Failure -> coordinator.onBranchFailure(result.code, result.message)
                    is ResultCallback.Success -> {
                        val callback = object : InnerAdLoadCallback<InterstitialAdBase> {
                            override fun onAdLoadFailed(code: Int, message: String) {
                                coordinator.onBranchFailure(code, message)
                            }

                            override fun onAdLoadSuccess(ad: InterstitialAdBase) {
                                addCacheAd(
                                    CacheAd(
                                        ad = ad,
                                        adUnitId = adUnitId,
                                        loadSuccessTimeInMillis = TimeHelper.currentTimeInMillis(),
                                        number = number,
                                    )
                                )
                                NewUserScopedBidderRegistry.registerLoadedStrategy(
                                    scene = scene,
                                    strategy = strategy,
                                )
                                coordinator.onBranchSuccess()
                            }
                        }
                        val lp = whenPlatformAdLoadParameter(
                            loadParameter = loadParameter,
                            interstitialAdLoadParameterConverter = { this },
                            rewardedAdLoadParameterConverter = PlatformRewardedAdLoadParameter::toInterstitialAdLoadParameter,
                            nativeAdLoadParameterConverter = PlatformNativeAdLoadParameter::toInterstitialAdLoadParameter,
                            openAdLoadParameterConverter = PlatformOpenAdLoadParameter::toInterstitialAdLoadParameter
                        )
                        result.value.load(loadParameter = lp, loadCallback = callback)
                    }
                }
            }

            AD_TYPE_REWARDED -> {
                when (val result = createRewardedAdLoader(
                    network = placement.mediation,
                    parameter = RewardedParameter(
                        adUnitId = placement.id,
                        startLoadTimeInMillis = TimeHelper.currentTimeInMillis(),
                        network = placement.mediation,
                        sessionId = sessionId,
                        sceneName = scene.name,
                        poseidonConfigVersion = poseidonConfigVersion
                    )
                )) {
                    is ResultCallback.Failure -> coordinator.onBranchFailure(result.code, result.message)
                    is ResultCallback.Success -> {
                        val callback = object : InnerAdLoadCallback<RewardedAdBase> {
                            override fun onAdLoadFailed(code: Int, message: String) {
                                coordinator.onBranchFailure(code, message)
                            }

                            override fun onAdLoadSuccess(ad: RewardedAdBase) {
                                addCacheAd(
                                    CacheAd(
                                        ad = ad,
                                        adUnitId = adUnitId,
                                        loadSuccessTimeInMillis = TimeHelper.currentTimeInMillis(),
                                        number = number,
                                    )
                                )
                                NewUserScopedBidderRegistry.registerLoadedStrategy(
                                    scene = scene,
                                    strategy = strategy,
                                )
                                coordinator.onBranchSuccess()
                            }
                        }
                        val lp = whenPlatformAdLoadParameter(
                            loadParameter = loadParameter,
                            interstitialAdLoadParameterConverter = PlatformInterstitialAdLoadParameter::toRewardedAdLoadParameter,
                            rewardedAdLoadParameterConverter = { this },
                            nativeAdLoadParameterConverter = PlatformNativeAdLoadParameter::toRewardedAdLoadParameter,
                            openAdLoadParameterConverter = PlatformOpenAdLoadParameter::toRewardedAdLoadParameter
                        )
                        result.value.load(loadParameter = lp, loadCallback = callback)
                    }
                }
            }

            AD_TYPE_NATIVE -> {
                when (val result = createNativeAdLoader(
                    network = placement.mediation,
                    parameter = NativeParameter(
                        adUnitId = placement.id,
                        startLoadTimeInMillis = TimeHelper.currentTimeInMillis(),
                        network = placement.mediation,
                        sessionId = sessionId,
                        sceneName = scene.name,
                        poseidonConfigVersion = poseidonConfigVersion
                    )
                )) {
                    is ResultCallback.Failure -> coordinator.onBranchFailure(result.code, result.message)
                    is ResultCallback.Success -> {
                        val callback = object : InnerAdLoadCallback<NativeAdBase> {
                            override fun onAdLoadFailed(code: Int, message: String) {
                                coordinator.onBranchFailure(code, message)
                            }

                            override fun onAdLoadSuccess(ad: NativeAdBase) {
                                addCacheAd(
                                    CacheAd(
                                        ad = ad,
                                        adUnitId = adUnitId,
                                        loadSuccessTimeInMillis = TimeHelper.currentTimeInMillis(),
                                        number = number,
                                    )
                                )
                                NewUserScopedBidderRegistry.registerLoadedStrategy(
                                    scene = scene,
                                    strategy = strategy,
                                )
                                coordinator.onBranchSuccess()
                            }
                        }
                        val lp = whenPlatformAdLoadParameter(
                            loadParameter = loadParameter,
                            interstitialAdLoadParameterConverter = PlatformInterstitialAdLoadParameter::toNativeAdLoadParameter,
                            rewardedAdLoadParameterConverter = PlatformRewardedAdLoadParameter::toNativeAdLoadParameter,
                            nativeAdLoadParameterConverter = { this },
                            openAdLoadParameterConverter = PlatformOpenAdLoadParameter::toNativeAdLoadParameter
                        )
                        result.value.load(loadParameter = lp, loadCallback = callback)
                    }
                }
            }

            else -> coordinator.onBranchFailure(
                code = ERROR_CODE_UNSUPPORTED_AD_TYPE,
                message = "Unsupported ad type: ${placement.adType}, sceneName=${scene.name}"
            )
        }
    }
}

private class StrategyLoadCoordinator(
    private val totalCount: Int,
    private val loadCallback: AdLoadListener?,
) {
    private val stateLock = reentrantLock()
    private var completedCount: Int = 0
    private var successSeen: Boolean = false
    private var successReported: Boolean = false
    private var firstFailureCode: Int = ERROR_CODE_UNKNOWN
    private var firstFailureMessage: String = "All ad units failed to load"

    fun onBranchSuccess() {
        var shouldReportSuccess = false
        stateLock.withLock {
            completedCount += 1
            successSeen = true
            if (!successReported) {
                successReported = true
                shouldReportSuccess = true
            }
        }
        if (shouldReportSuccess) {
            loadCallback?.onAdLoadSuccess()
        }
    }

    fun onBranchFailure(code: Int, message: String) {
        var shouldReportFailure = false
        var failureCode = ERROR_CODE_UNKNOWN
        var failureMessage = "All ad units failed to load"
        stateLock.withLock {
            if (completedCount == 0 ||
                (!successSeen &&
                    firstFailureMessage == "All ad units failed to load" &&
                    firstFailureCode == ERROR_CODE_UNKNOWN)
            ) {
                firstFailureCode = code
                firstFailureMessage = message
            }
            completedCount += 1
            if (completedCount == totalCount && !successSeen) {
                shouldReportFailure = true
                failureCode = firstFailureCode
                failureMessage = firstFailureMessage
            }
        }
        if (shouldReportFailure) {
            loadCallback?.onAdLoadFailed(failureCode, failureMessage)
        }
    }
}

