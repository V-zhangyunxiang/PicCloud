package com.poseidon.ad.main.adinfo

import androidx.annotation.CallSuper
import com.poseidon.ad.common.storage.db.AD_REVENUE_REPOSITORY_IMPL
import com.poseidon.ad.common.storage.db.AdRevenueRecord
import com.poseidon.ad.common.storage.db.RoomBackedPolicyState
import com.poseidon.ad.common.toolkit.dispatchToBackground
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.common.toolkit.postDelayed
import com.poseidon.ad.core.processor.PoseidonSetupBaseProcessor.Companion.poseidonSetupProcessor
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.preload.share.tryPreloadSharedAdWhenAdDismissed
import com.poseidon.ad.main.preload.share.tryPreloadSharedAdWhenAdFailedToShow
import com.poseidon.ad.main.preload.share.tryPreloadSharedAdWhenAdImpression
import com.poseidon.ad.main.price.AdPaidInfo
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.price.traceFirebaseAdImpressionCumulativeRevenueGreatThan0dot01
import com.poseidon.ad.main.price.trackAdjustAdRevenue
import com.poseidon.ad.main.price.trackAdjustEventWhenAdImpressed
import com.poseidon.ad.main.price.trackAdjustEventWhenFullscreenAdImpressed
import com.poseidon.ad.main.price.trackHighRevenueNewUserEventsWhenTimelineReady
import com.poseidon.ad.main.price.trackFirebaseAdRevenue
import com.poseidon.ad.main.show.FullscreenAdImpressionIntervalLimit
import com.poseidon.ad.main.show.InnerFullScreenContentCallback
import com.poseidon.ad.main.strategy.aeo.trackAeo
import com.poseidon.ad.main.strategy.estimated.ecpm.EstimatedEcpmCfg
import com.poseidon.ad.main.strategy.rrc.trackRrcRoas
import com.poseidon.ad.restrict.ctr.CtrLimit
import com.poseidon.ad.restrict.ctr.callOnAdClosed
import com.poseidon.ad.restrict.ctr.callOnAdImpression
import com.poseidon.ad.restrict.show.AdShowLimit
import com.poseidon.ad.statistics.base.ErrorDetectionReporter
import com.poseidon.ad.statistics.core.BizAdClickTracker
import com.poseidon.ad.statistics.core.BizAdCloseTracker
import com.poseidon.ad.statistics.core.BizAdImpressionTracker
import com.poseidon.ad.statistics.core.BizAdRevenueTracker
import kotlinx.atomicfu.atomic
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

abstract class AdBase {
    @AdTypeDef
    abstract fun getAdType(): Int

    abstract fun isReady(): Boolean

    @AdSourceIdDef
    abstract fun getAdSourceId(): Int

    abstract fun getParameter(): Parameter

    override fun toString(): String {
        return "${super.toString()} - [adType:${getAdType()},ecpm:${eCPM()},AdSourceId:${getAdSourceId()},Parameter:${getParameter().toString()}]"
    }

    @Volatile
    private var estimatedEcpmAdSourceInfo: AdSourceInfo? = null

    @CallSuper
    open fun eCPM(): Double? {
        return estimatedEcpm(estimatedEcpmAdSourceInfo)
    }

    @Suppress("SpellCheckingInspection")
    protected fun updateEstimatedEcpmAdSourceInfo(adSourceInfo: AdSourceInfo?) {
        if (adSourceInfo != null) {
            estimatedEcpmAdSourceInfo = adSourceInfo
        }
    }


    @Suppress("SpellCheckingInspection")
    internal fun estimatedEcpm(adSourceInfo: AdSourceInfo?): Double? {
        val parameter = getParameter()
        val config = EstimatedEcpmCfg.Companion.get()
            ?.firstOrNull { it.adUnitId == parameter.adUnitId && it.adType == parameter.adType && it.network == parameter.network }
            ?: return null
        return config.resolveEstimatedEcpm(adSourceInfo)
    }

    fun updateSceneName(sceneName: String) {
        logInfo(TAG) { "current scene name=${getParameter().sceneName}" }
        getParameter().sceneName = sceneName
        logInfo(TAG) { "updated scene name with: $sceneName == ${getParameter().sceneName}" }
    }

    protected fun dispatchOnPaidEvent(adPaidInfo: AdPaidInfo) {
        val parameter = getParameter()
        BizAdRevenueTracker(
            sessionId = parameter.sessionId,
            sceneName = parameter.sceneName,
            adUnitId = parameter.adUnitId,
            adSourcePlacementId = adPaidInfo.adSourcePlacementId,
            adSourceId = adPaidInfo.adSourceId,
            adType = getAdType(),
            networkId = parameter.network,
            revenue = adPaidInfo.revenue,
            currency = adPaidInfo.currencyCode,
            precision = adPaidInfo.precision.value,
            adSourceName = adPaidInfo.adSourceName,
            poseidonConfigVersion = parameter.poseidonConfigVersion
        ).analyze()
        adPaidInfo.trackAdjustAdRevenue()
        adPaidInfo.trackFirebaseAdRevenue()
        adPaidInfo.trackAdjustEventWhenAdImpressed()
        val isFullscreen = isFullscreenAd(getAdType())
        if (isFullscreen) {
            adPaidInfo.trackAdjustEventWhenFullscreenAdImpressed()
            dispatchToBackground {
                adPaidInfo.trackHighRevenueNewUserEventsWhenTimelineReady()
            }
        }
        adPaidInfo.traceFirebaseAdImpressionCumulativeRevenueGreatThan0dot01()

        dispatchToBackground {
            if (!RoomBackedPolicyState.isRevenuePersistenceEnabled()) {
                logInfo(TAG) { "Skip ad revenue Room persistence because AEO/RRC policies are disabled." }
                return@dispatchToBackground
            }
            val record = AdRevenueRecord(
                revenue = adPaidInfo.revenue,
                eCPM = adPaidInfo.eCPM,
                currencyCode = adPaidInfo.currencyCode,
                precision = adPaidInfo.precision.value,
                networkId = parameter.network,
                adSourceId = adPaidInfo.adSourceId,
                adSourcePlacementId = adPaidInfo.adSourcePlacementId,
                adUnitId = parameter.adUnitId,
                adType = getAdType(),
                createTime = Clock.System.now().toEpochMilliseconds()
            )
            val cutoffTime = Clock.System.now()
                .minus(7, DateTimeUnit.DAY, TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
            val result = runCatching {
                AD_REVENUE_REPOSITORY_IMPL.deleteOldReports(cutoffTime)
                AD_REVENUE_REPOSITORY_IMPL.insert(record)
            }.onFailure { tr ->
                if (poseidonSetupProcessor().debug) {
                    error("(Only Crash on DEBUG) Failed to persist ad revenue: ${tr.message}")
                } else {
                    ErrorDetectionReporter(
                        name = "AdRevenueDatabaseError",
                        extra = tr.message.orEmpty()
                    ).analyze()
                }
            }
            if (result.isSuccess) {
                trackAeo()
                trackRrcRoas(record)
            }
        }
    }

    protected fun dispatchAdImpressionEvent(
        @AdSourceIdDef adSourceId: Int,
        adSourcePlacementId: String,
        adSourceName: String? = null
    ) {
        val parameter = getParameter()
        CtrLimit.callOnAdImpression(sessionId = parameter.sessionId)
        tryPreloadSharedAdWhenAdImpression(sceneName = parameter.sceneName)
        val adType = getAdType()
        if (isFullscreenAd(adType)) {
            // Prevent situations where there is no callback for ads to be closed, such as when the app exits the background and the process dies
            FullscreenAdImpressionIntervalLimit.callOnAdClosed(fullscreenAdType = adType)
        }
        BizAdImpressionTracker(
            sessionId = parameter.sessionId,
            sceneName = parameter.sceneName,
            adUnitId = parameter.adUnitId,
            adSourcePlacementId = adSourcePlacementId,
            adSourceId = adSourceId,
            adType = adType,
            networkId = parameter.network,
            resultCode = CODE_SUCCESS,
            resultMessage = "",
            adSourceName = adSourceName,
            poseidonConfigVersion = parameter.poseidonConfigVersion
        ).analyze()
        AdShowLimit.callOnAdImpressed(adSourceId)
    }

    protected fun dispatchAdFailedToShowEvent(
        @ShowCodeDef code: Int,
        message: String,
        @AdSourceIdDef adSourceId: Int,
        adSourcePlacementId: String,
        adSourceName: String? = null
    ) {
        val parameter = getParameter()
        tryPreloadSharedAdWhenAdFailedToShow(sceneName = parameter.sceneName)
        BizAdImpressionTracker(
            sessionId = parameter.sessionId,
            sceneName = parameter.sceneName,
            adUnitId = parameter.adUnitId,
            adSourcePlacementId = adSourcePlacementId,
            adSourceId = adSourceId,
            adType = getAdType(),
            networkId = parameter.network,
            resultCode = code,
            resultMessage = message,
            adSourceName = adSourceName,
            poseidonConfigVersion = parameter.poseidonConfigVersion
        ).analyze()
    }

    protected fun dispatchAdClickedEvent(
        @AdSourceIdDef adSourceId: Int,
        adSourcePlacementId: String,
        adSourceName: String? = null
    ) {
        val parameter = getParameter()

        CtrLimit.callOnAdClicked(sessionId = parameter.sessionId) {
            postDelayed(500) {
                onAdForceClosed()
            }
        }

        BizAdClickTracker(
            sessionId = parameter.sessionId,
            sceneName = parameter.sceneName,
            adUnitId = parameter.adUnitId,
            adSourcePlacementId = adSourcePlacementId,
            adSourceId = adSourceId,
            adType = getAdType(),
            networkId = parameter.network,
            adSourceName = adSourceName,
            poseidonConfigVersion = parameter.poseidonConfigVersion
        ).analyze()
    }

    protected val isAdClosedAtomic = atomic(false)

    protected abstract fun onAdForceClosed()

    protected fun dispatchAdClosedEvent(
        @AdSourceIdDef adSourceId: Int,
        adSourcePlacementId: String,
        adSourceName: String? = null
    ) {
        val adType = getAdType()
        if (isFullscreenAd(adType)) {
            FullscreenAdImpressionIntervalLimit.callOnAdClosed(fullscreenAdType = adType)
        }
        val parameter = getParameter()
        CtrLimit.callOnAdClosed(sessionId = parameter.sessionId)
        tryPreloadSharedAdWhenAdDismissed(sceneName = parameter.sceneName)
        BizAdCloseTracker(
            sessionId = parameter.sessionId,
            sceneName = parameter.sceneName,
            adUnitId = parameter.adUnitId,
            adSourcePlacementId = adSourcePlacementId,
            adSourceId = adSourceId,
            adType = adType,
            networkId = parameter.network,
            adSourceName = adSourceName,
            poseidonConfigVersion = parameter.poseidonConfigVersion
        ).analyze()
    }

    abstract fun destroy()

    companion object {
        private const val TAG = "AdBase"
    }
}

abstract class FullScreenAdBase : AdBase() {
    abstract fun setFullScreenContentCallback(fullScreenContentCallback: InnerFullScreenContentCallback)
}
