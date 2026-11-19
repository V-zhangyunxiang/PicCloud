package com.poseidon.ad.mediation.topon.load

import com.thinkup.core.api.TUAdInfo
import com.thinkup.core.api.AdError
import com.thinkup.rewardvideo.api.TURewardVideoAd
import com.thinkup.rewardvideo.api.TURewardVideoListener
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNKNOWN
import com.poseidon.ad.main.adinfo.RewardedAdBase
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.RewardedAdLoader
import com.poseidon.ad.main.load.RewardedParameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Admob
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.processor.PlatformRewardedAdLoadParameter
import com.poseidon.ad.mediation.topon.init.TopOnSetup
import com.poseidon.ad.mediation.topon.TopOnAdmobExtractor
import com.poseidon.ad.mediation.topon.TopOnStatusDispatcher
import com.poseidon.ad.mediation.topon.resolveAdPaidInfo
import com.poseidon.ad.mediation.topon.toAdSourceInfo
import com.poseidon.ad.mediation.topon.show.TopOnRewardedAd
import com.poseidon.ad.statistics.core.TopOnAdmobBidAnalysisReporter
import java.util.concurrent.atomic.AtomicReference

/**
 * TopOn 激励视频加载器，仅负责 SDK 加载与监听。
 */
internal class TopOnRewardedLoad(parameter: RewardedParameter) :
    RewardedAdLoader(parameter = parameter) {

    /** 强类型激励参数，供监听器复用 */
    private val rewardedParameter: RewardedParameter = parameter

    override fun mediationAdLoad(
        loadParameter: PlatformRewardedAdLoadParameter,
        loadCallback: InnerAdLoadCallback<RewardedAdBase>
    ) {
        val topOnRewardedAd = TURewardVideoAd(loadParameter.context, rewardedParameter.adUnitId)
        topOnRewardedAd.setAdListener(
            TopOnRewardedLoadListener(
                topOnAd = topOnRewardedAd,
                loadCallback = loadCallback
            )
        )
        markAdRequestStarted()
        topOnRewardedAd.load()
    }

    override fun mediationAdCheckBeforeLoad(): MediationSetup = TopOnSetup

    /** 负责桥接 TopOn 激励广告加载事件并转为包装对象 */
    private inner class TopOnRewardedLoadListener(
        private val topOnAd: TURewardVideoAd,
        private val loadCallback: InnerAdLoadCallback<RewardedAdBase>
    ) : TURewardVideoListener {

        /** 缓存加载成功的 TopOn 激励包装对象 */
        private val loadedAdHolder = AtomicReference<TopOnRewardedAd>()

        override fun onRewardedVideoAdLoaded() {
            markAdRequestReturned()
            TopOnStatusDispatcher.dispatch {
                val atTopAdInfo: TUAdInfo? = runCatching {
                    topOnAd.checkAdStatus().getTUTopAdInfo()
                }.getOrNull()
                val adSourceInfo = atTopAdInfo?.toAdSourceInfo()
                val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
                val reportedRevenue = atTopAdInfo?.let {
                    resolveAdPaidInfo(adInfo = it, loadParameter = rewardedParameter).revenue
                } ?: 0.0
                val extractionResult = if (
                    adSourceId == AD_SOURCE_ID_Admob && reportedRevenue <= 0.0
                ) {
                    runCatching {
                        TopOnAdmobExtractor.extractAdmobBidPriceWithDetails(topOnAd)
                    }.getOrNull()
                } else {
                    null
                }

                finalizeLoadedAd(
                    adInfo = atTopAdInfo,
                    extractionResult = extractionResult,
                    reportedRevenue = reportedRevenue,
                )
            }
        }

        private fun finalizeLoadedAd(
            adInfo: TUAdInfo?,
            extractionResult: TopOnAdmobExtractor.BidExtractionResult?,
            reportedRevenue: Double
        ) {
            val adSourceInfo = adInfo?.toAdSourceInfo()
            val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
            val fallbackRevenue = extractionResult?.winnerBidPrice?.takeIf { it > 0.0 }
            val topOnRewardedAd = TopOnRewardedAd(
                adSourceId = adSourceId,
                thirdPartyAd = topOnAd,
                parameter = rewardedParameter,
                bidExtractionResult = extractionResult,
                initialAdInfo = adInfo,
            )
            val estimatedRevenue = topOnRewardedAd.estimatedEcpm(adSourceInfo) ?: -1.0
            loadedAdHolder.set(topOnRewardedAd)
            loadCallback.onAdLoadSuccess(topOnRewardedAd)
            dispatchAdLoadedEvent(
                adSourceId = adSourceId,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                revenue = reportedRevenue.takeIf { it > 0.0 } ?: fallbackRevenue,
                poseidonConfigVersion = rewardedParameter.poseidonConfigVersion,
                adSourceName = adInfo?.networkName,
                estimatedRevenue = estimatedRevenue,
            )
            if (adInfo != null && extractionResult != null) {
                TopOnAdmobBidAnalysisReporter(
                    sessionId = rewardedParameter.sessionId,
                    sceneName = rewardedParameter.sceneName,
                    adUnitId = rewardedParameter.adUnitId,
                    adType = rewardedParameter.adType,
                    networkId = rewardedParameter.network,
                    sdkReportedRevenue = reportedRevenue,
                    sdkReportedPrecision = adInfo.ecpmPrecision,
                    extractionResult = extractionResult,
                    topAdNetworkPlacementId = adInfo.networkPlacementId,
                    topAdNetworkName = adInfo.networkName
                ).analyze()
            }
        }

        override fun onRewardedVideoAdFailed(error: AdError) {
            markAdRequestReturned()
            clearTopOnListeners()
            val code = error.code.toIntOrNull() ?: ERROR_CODE_UNKNOWN
            val message = error.desc
            dispatchAdFailedLoadedEvent(
                code = code,
                message = message,
                poseidonConfigVersion = rewardedParameter.poseidonConfigVersion
            )
            loadCallback.onAdLoadFailed(code = code, message = message)
        }

        override fun onRewardedVideoAdPlayStart(info: TUAdInfo) {
            loadedAdHolder.get()?.dispatchAdImpressionEvent(info)
        }

        override fun onRewardedVideoAdPlayEnd(info: TUAdInfo) {
            // no-op
        }

        override fun onRewardedVideoAdPlayFailed(error: AdError, info: TUAdInfo) {
            loadedAdHolder.getAndClear()?.apply {
                dispatchAdFailedToShowEvent(error = error, info = info)
                runCatching {
                    destroy()
                }
            }
        }

        override fun onRewardedVideoAdClosed(info: TUAdInfo) {
            loadedAdHolder.getAndClear()?.apply {
                dispatchAdClosedEvent(info)
                runCatching {
                    destroy()
                }
            }
        }

        override fun onRewardedVideoAdPlayClicked(info: TUAdInfo) {
            loadedAdHolder.get()?.dispatchAdClickedEvent(info)
        }

        override fun onReward(info: TUAdInfo) {
            loadedAdHolder.get()?.dispatchUserEarnedRewardEvent(info)
        }

        private fun clearTopOnListeners() {
            topOnAd.setAdListener(null)
            topOnAd.setAdRevenueListener(null)
        }

        /** 获取并清空加载成功的广告引用 */
        private fun AtomicReference<TopOnRewardedAd>.getAndClear(): TopOnRewardedAd? {
            val value = get()
            set(null)
            return value
        }
    }
}
