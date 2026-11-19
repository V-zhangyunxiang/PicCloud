package com.poseidon.ad.main.load

import androidx.annotation.MainThread
import com.poseidon.ad.common.toolkit.ResultCallback
import com.poseidon.ad.common.toolkit.StringHelper
import com.poseidon.ad.common.toolkit.TimeHelper
import com.poseidon.ad.common.toolkit.VersionHelper
import com.poseidon.ad.common.toolkit.isMainThread
import com.poseidon.ad.common.toolkit.postOnMainThread
import com.poseidon.ad.common.toolkit.runOnMainThread
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.core.processor.MediationSetupCallback
import com.poseidon.ad.main.adinfo.AdBase
import com.poseidon.ad.main.adinfo.AdTypeDef
import com.poseidon.ad.main.adinfo.CODE_SUCCESS
import com.poseidon.ad.main.adinfo.ERROR_CODE_FAILED_TO_CREATE_AD_NETWORK
import com.poseidon.ad.main.adinfo.ERROR_CODE_NETWORK_INITIALIZATION_FAILED
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNKNOWN
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNSUPPORTED_AD_NETWORK
import com.poseidon.ad.main.preload.share.tryPreloadSharedAdWhenAdResponse
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.mediation.MediationDef
import com.poseidon.ad.mediation.admob.load.AdmobBannerLoad
import com.poseidon.ad.mediation.admob.load.AdmobNativeLoad
import com.poseidon.ad.mediation.admob.load.AdmobOpenLoad
import com.poseidon.ad.mediation.admob.load.AdmobRewardedLoad
import com.poseidon.ad.mediation.matchMediationId
import com.poseidon.ad.mediation.max.load.MaxBannerLoad
import com.poseidon.ad.mediation.max.load.MaxInterstitialLoad
import com.poseidon.ad.mediation.max.load.MaxNativeLoad
import com.poseidon.ad.mediation.max.load.MaxOpenLoad
import com.poseidon.ad.mediation.max.load.MaxRewardedLoad
import com.poseidon.ad.mediation.topon.load.TopOnBannerLoad
import com.poseidon.ad.mediation.topon.load.TopOnInterstitialLoad
import com.poseidon.ad.mediation.topon.load.TopOnNativeLoad
import com.poseidon.ad.mediation.topon.load.TopOnOpenAdLoad
import com.poseidon.ad.mediation.topon.load.TopOnRewardedLoad
import com.poseidon.ad.special.topon.TopOnTrafficGrouping
import com.poseidon.ad.statistics.core.BizAdResponseTracker

abstract class AdLoaderBase<T : AdBase, PlatformAdLoadParameter, C : InnerAdLoadCallback<T>>(val parameter: Parameter) {

    private val loaderSessionId: String by lazy { "AdLoaderSessionId_${parameter.adUnitId}_${StringHelper.newSessionId()}_${parameter.sessionId}" }

    private val durationTracker = AdLoadDurationTracker(parameter.startLoadTimeInMillis)

    open val canLoadOnBackgroundThread: Boolean = false

    fun load(loadParameter: PlatformAdLoadParameter, loadCallback: C) {
        matchMediationId(
            parameter.network,
            topOn = { TopOnTrafficGrouping.callBeforeTopOnAdLoad(adUnitId = parameter.adUnitId) },
            admob = { },
            max = { }
        )
        mediationAdCheckBeforeLoad().init(object : MediationSetupCallback {
            override fun onSuccess() {
                if (canLoadOnBackgroundThread) {
                    safeMediationAdLoad(loadParameter = loadParameter, loadCallback = loadCallback)
                } else if (isMainThread()) {
                    safeMediationAdLoad(loadParameter = loadParameter, loadCallback = loadCallback)
                } else {
                    postOnMainThread {
                        safeMediationAdLoad(loadParameter = loadParameter, loadCallback = loadCallback)
                    }
                }
            }

            override fun onFailure(message: String) = runOnMainThread {
                val code = ERROR_CODE_NETWORK_INITIALIZATION_FAILED
                dispatchAdFailedLoadedEvent(
                    code = code,
                    message = message,
                    poseidonConfigVersion = parameter.poseidonConfigVersion
                )
                loadCallback.onAdLoadFailed(code = code, message = message)
            }
        })
    }

    @MainThread
    private fun safeMediationAdLoad(loadParameter: PlatformAdLoadParameter, loadCallback: C) {
        runCatching {
            AdLoadModeControl.checking(
                sessionId = loaderSessionId,
                adUnitId = parameter.adUnitId,
                onWaiting = { durationTracker.markWaiting() }
            ) {
                durationTracker.markReleased()
                AdLoadModeControl.anyAdLoadStart(
                    sessionId = loaderSessionId,
                    adUnitId = parameter.adUnitId
                )
                mediationAdLoad(loadParameter = loadParameter, loadCallback = loadCallback)
            }
        }.onFailure { t ->
            val code = ERROR_CODE_UNKNOWN
            val message = t.message ?: "Error happened when network load: ${t.stackTraceToString()}"
            dispatchAdFailedLoadedEvent(
                code = code,
                message = message,
                poseidonConfigVersion = parameter.poseidonConfigVersion
            )
            loadCallback.onAdLoadFailed(code = code, message = message)
        }
    }

    abstract fun mediationAdLoad(loadParameter: PlatformAdLoadParameter, loadCallback: C)

    abstract fun mediationAdCheckBeforeLoad(): MediationSetup

    protected fun markAdRequestStarted() = durationTracker.markRequestStarted()

    protected fun markAdRequestReturned() = durationTracker.markRequestReturned()

    @JvmOverloads
    protected fun dispatchAdLoadedEvent(
        @AdSourceIdDef adSourceId: Int,
        adSourcePlacementId: String,
        revenue: Double?,
        poseidonConfigVersion: VersionHelper?,
        adSourceName: String?,
        estimatedRevenue: Double,
        isAutoRefresh: Boolean = false
    ) {
        AdLoadModeControl.anyAdLoadComplete(sessionId = loaderSessionId)
        tryPreloadSharedAdWhenAdResponse(sceneName = parameter.sceneName)
        val durations = if (isAutoRefresh) null else durationTracker.finish()
        BizAdResponseTracker(
            sessionId = parameter.sessionId,
            sceneName = parameter.sceneName,
            adUnitId = parameter.adUnitId,
            adSourcePlacementId = adSourcePlacementId,
            adSourceId = adSourceId,
            adType = parameter.adType,
            networkId = parameter.network,
            take = durations?.duration ?: TimeHelper.take(parameter.startLoadTimeInMillis),
            durations = durations,
            resultCode = CODE_SUCCESS,
            resultMessage = "",
            revenue = revenue,
            poseidonConfigVersion = poseidonConfigVersion,
            adSourceName = adSourceName,
            estimatedRevenue = estimatedRevenue
        ).analyze()
    }

    @JvmOverloads
    protected fun dispatchAdFailedLoadedEvent(
        code: Int,
        message: String,
        poseidonConfigVersion: VersionHelper?,
        isAutoRefresh: Boolean = false
    ) {
        AdLoadModeControl.anyAdLoadComplete(sessionId = loaderSessionId)
        tryPreloadSharedAdWhenAdResponse(sceneName = parameter.sceneName)
        val durations = if (isAutoRefresh) null else durationTracker.finish()
        BizAdResponseTracker(
            sessionId = parameter.sessionId,
            sceneName = parameter.sceneName,
            adUnitId = parameter.adUnitId,
            adSourcePlacementId = null,
            adSourceId = null,
            adType = parameter.adType,
            networkId = parameter.network,
            take = durations?.duration ?: TimeHelper.take(parameter.startLoadTimeInMillis),
            durations = durations,
            resultCode = code,
            resultMessage = message,
            revenue = null,
            poseidonConfigVersion = poseidonConfigVersion,
            adSourceName = null
        ).analyze()
    }
}

open class Parameter(
    val adUnitId: String,
    val startLoadTimeInMillis: Long,
    @MediationDef val network: Int,
    val sessionId: String,
    @AdTypeDef val adType: Int,
    // Cached ads rewrite sceneName right before show, and mediation callbacks may read it from
    // another thread when emitting impression/revenue analytics.
    @kotlin.jvm.Volatile var sceneName: String,
    val poseidonConfigVersion: VersionHelper?
)

internal fun createInterstitialAdLoader(
    @MediationDef network: Int,
    parameter: InterstitialParameter
): ResultCallback<InterstitialAdLoader> = runCatching {
    runCatching {
        matchMediationId<ResultCallback<InterstitialAdLoader>>(
            network,
            topOn = { ResultCallback.Success<InterstitialAdLoader>(value = TopOnInterstitialLoad(parameter = parameter)) },
            admob = { ResultCallback.Success<InterstitialAdLoader>(value = AdmobInterstitialLoad(parameter = parameter)) },
            max = { ResultCallback.Success<InterstitialAdLoader>(value = MaxInterstitialLoad(parameter = parameter)) },
        )
    }.getOrElse {
        ResultCallback.Failure(
            code = ERROR_CODE_UNSUPPORTED_AD_NETWORK,
            message = "Unsupported ad network: $network"
        )
    }
}.getOrElse { t ->
    ResultCallback.Failure(
        code = ERROR_CODE_FAILED_TO_CREATE_AD_NETWORK,
        message = "Failed to create ad network: $network, exception: ${t.message}"
    )
}

internal fun createNativeAdLoader(
    @MediationDef network: Int,
    parameter: NativeParameter
): ResultCallback<NativeAdLoader> = runCatching {
    runCatching {
        matchMediationId<ResultCallback<NativeAdLoader>>(
            network,
            topOn = { ResultCallback.Success<NativeAdLoader>(value = TopOnNativeLoad(parameter = parameter)) },
            admob = { ResultCallback.Success<NativeAdLoader>(value = AdmobNativeLoad(parameter = parameter)) },
            max = { ResultCallback.Success<NativeAdLoader>(value = MaxNativeLoad(parameter = parameter)) }
        )
    }.getOrElse {
        ResultCallback.Failure(
            code = ERROR_CODE_UNSUPPORTED_AD_NETWORK,
            message = "createNativeAdLoader Unsupported ad network: $network"
        )
    }
}.getOrElse { t ->
    ResultCallback.Failure(
        code = ERROR_CODE_FAILED_TO_CREATE_AD_NETWORK,
        message = "Failed to create ad network: $network, exception: ${t.message}"
    )
}

internal fun createRewardedAdLoader(
    @MediationDef network: Int,
    parameter: RewardedParameter
): ResultCallback<RewardedAdLoader> = runCatching {
    runCatching {
        matchMediationId<ResultCallback<RewardedAdLoader>>(
            network,
            topOn = { ResultCallback.Success<RewardedAdLoader>(value = TopOnRewardedLoad(parameter = parameter)) },
            admob = { ResultCallback.Success<RewardedAdLoader>(value = AdmobRewardedLoad(parameter = parameter)) },
            max = { ResultCallback.Success<RewardedAdLoader>(value = MaxRewardedLoad(parameter = parameter)) },
        )
    }.getOrElse {
        ResultCallback.Failure(
            code = ERROR_CODE_UNSUPPORTED_AD_NETWORK,
            message = "createRewardedAdLoader Unsupported ad network: $network"
        )
    }
}.getOrElse { t ->
    ResultCallback.Failure(
        code = ERROR_CODE_FAILED_TO_CREATE_AD_NETWORK,
        message = "Failed to create ad network: $network, exception: ${t.message}"
    )
}

internal fun createOpenAdLoader(
    @MediationDef network: Int,
    parameter: OpenParameter
): ResultCallback<OpenAdLoader> = runCatching {
    runCatching {
        matchMediationId<ResultCallback<OpenAdLoader>>(
            network,
            topOn = { ResultCallback.Success<OpenAdLoader>(value = TopOnOpenAdLoad(parameter = parameter)) },
            admob = { ResultCallback.Success<OpenAdLoader>(value = AdmobOpenLoad(parameter = parameter)) },
            max = { ResultCallback.Success<OpenAdLoader>(value = MaxOpenLoad(parameter = parameter)) },
        )
    }.getOrElse {
        ResultCallback.Failure(
            code = ERROR_CODE_UNSUPPORTED_AD_NETWORK,
            message = "createOpenAdLoader Unsupported ad network: $network"
        )
    }
}.getOrElse { t ->
    ResultCallback.Failure(
        code = ERROR_CODE_FAILED_TO_CREATE_AD_NETWORK,
        message = "Failed to create ad network: $network, exception: ${t.message}"
    )
}

internal fun createBannerAdLoader(
    @MediationDef network: Int,
    parameter: BannerParameter
): ResultCallback<BannerAdLoad> = runCatching {
    runCatching {
        matchMediationId<ResultCallback<BannerAdLoad>>(
            network,
            topOn = { ResultCallback.Success<BannerAdLoad>(value = TopOnBannerLoad(parameter = parameter)) },
            admob = { ResultCallback.Success<BannerAdLoad>(value = AdmobBannerLoad(parameter = parameter)) },
            max = { ResultCallback.Success<BannerAdLoad>(value = MaxBannerLoad(parameter = parameter)) },
        )
    }.getOrElse {
        ResultCallback.Failure(
            code = ERROR_CODE_UNSUPPORTED_AD_NETWORK,
            message = "createBannerAdLoader Unsupported ad network: $network"
        )
    }
}.getOrElse { t ->
    ResultCallback.Failure(
        code = ERROR_CODE_FAILED_TO_CREATE_AD_NETWORK,
        message = "Failed to create ad network: $network, exception: ${t.message}"
    )
}
@Suppress("FunctionName")
fun TopOnInterstitialAdLoader(parameter: InterstitialParameter): InterstitialAdLoader {
    return TopOnInterstitialLoad(parameter = parameter)
}

@Suppress("FunctionName")
fun AdmobInterstitialAdLoader(parameter: InterstitialParameter): InterstitialAdLoader {
    return AdmobInterstitialLoad(parameter = parameter)
}

@Suppress("FunctionName")
fun MaxInterstitialAdLoader(parameter: InterstitialParameter): InterstitialAdLoader {
    return MaxInterstitialLoad(parameter = parameter)
}

@Suppress("FunctionName")
fun TopOnNativeAdLoader(parameter: NativeParameter): NativeAdLoader {
    return TopOnNativeLoad(parameter = parameter)
}

@Suppress("FunctionName")
fun AdmobNativeAdLoader(parameter: NativeParameter): NativeAdLoader {
    return AdmobNativeLoad(parameter = parameter)
}

@Suppress("FunctionName")
fun MaxNativeAdLoader(parameter: NativeParameter): NativeAdLoader {
    return MaxNativeLoad(parameter = parameter)
}

@Suppress("FunctionName")
fun TopOnRewardedAdLoader(parameter: RewardedParameter): RewardedAdLoader {
    return TopOnRewardedLoad(parameter = parameter)
}

@Suppress("FunctionName")
fun AdmobRewardedAdLoader(parameter: RewardedParameter): RewardedAdLoader {
    return AdmobRewardedLoad(parameter = parameter)
}

@Suppress("FunctionName")
fun MaxRewardedAdLoader(parameter: RewardedParameter): RewardedAdLoader {
    return MaxRewardedLoad(parameter = parameter)
}

@Suppress("FunctionName")
fun TopOnOpenAdLoader(parameter: OpenParameter): OpenAdLoader {
    return TopOnOpenAdLoad(parameter = parameter)
}

@Suppress("FunctionName")
fun AdmobOpenAdLoader(parameter: OpenParameter): OpenAdLoader {
    return AdmobOpenLoad(parameter = parameter)
}

@Suppress("FunctionName")
fun MaxOpenAdLoader(parameter: OpenParameter): OpenAdLoader {
    return MaxOpenLoad(parameter = parameter)
}

@Suppress("FunctionName")
fun TopOnBannerAdLoader(parameter: BannerParameter): BannerAdLoad {
    return TopOnBannerLoad(parameter = parameter)
}

@Suppress("FunctionName")
fun AdmobBannerAdLoader(parameter: BannerParameter): BannerAdLoad {
    return AdmobBannerLoad(parameter = parameter)
}

@Suppress("FunctionName")
fun MaxBannerAdLoader(parameter: BannerParameter): BannerAdLoad {
    return MaxBannerLoad(parameter = parameter)
}
