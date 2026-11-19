package com.poseidon.ad.mediation.topon.load

import com.thinkup.core.api.TUAdInfo
import com.thinkup.core.api.AdError
import com.thinkup.interstitial.api.TUInterstitial
import com.thinkup.interstitial.api.TUInterstitialListener
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNKNOWN
import com.poseidon.ad.main.adinfo.InterstitialAdBase
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.InterstitialAdLoader
import com.poseidon.ad.main.load.InterstitialParameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Admob
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.processor.PlatformInterstitialAdLoadParameter
import com.poseidon.ad.mediation.topon.init.TopOnSetup
import com.poseidon.ad.mediation.topon.TopOnAdmobExtractor
import com.poseidon.ad.mediation.topon.TopOnStatusDispatcher
import com.poseidon.ad.mediation.topon.resolveAdPaidInfo
import com.poseidon.ad.mediation.topon.toAdSourceInfo
import com.poseidon.ad.mediation.topon.show.TopOnInterstitialAd
import com.poseidon.ad.statistics.core.TopOnAdmobBidAnalysisReporter
import java.util.concurrent.atomic.AtomicReference

internal class TopOnInterstitialLoad(parameter: InterstitialParameter) :
    InterstitialAdLoader(parameter = parameter) {

    override val canLoadOnBackgroundThread: Boolean = true

    /** 强类型插屏参数，供内部多处使用 */
    private val interstitialParameter: InterstitialParameter = parameter

    override fun mediationAdLoad(
        loadParameter: PlatformInterstitialAdLoadParameter,
        loadCallback: InnerAdLoadCallback<InterstitialAdBase>
    ) {
        val topOnInterstitialAd = TUInterstitial(loadParameter.context, interstitialParameter.adUnitId)
        topOnInterstitialAd.setAdListener(
            TopOnInterstitialLoadListener(
                topOnAd = topOnInterstitialAd,
                loadCallback = loadCallback
            )
        )
        // 此处是TopOn的插屏加载的核心方法：TUInterstitial.load()
        markAdRequestStarted()
        topOnInterstitialAd.load()
    }

    override fun mediationAdCheckBeforeLoad(): MediationSetup = TopOnSetup

    /** 负责处理 TopOn 插屏加载事件并转发给包装广告对象 */
    private inner class TopOnInterstitialLoadListener(
        private val topOnAd: TUInterstitial,
        private val loadCallback: InnerAdLoadCallback<InterstitialAdBase>
    ) : TUInterstitialListener {

        /** 缓存已经加载成功的插屏包装对象 */
        private val loadedAdHolder = AtomicReference<TopOnInterstitialAd>()

        override fun onInterstitialAdLoaded() {
            markAdRequestReturned()
            TopOnStatusDispatcher.dispatch {
                val atTopAdInfo = currentAdInfo()
                val adSourceInfo = atTopAdInfo?.toAdSourceInfo()
                val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
                val reportedRevenue = atTopAdInfo?.let {
                    resolveAdPaidInfo(adInfo = it, loadParameter = interstitialParameter).revenue
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
            val topOnInterstitialAd = TopOnInterstitialAd(
                adSourceId = adSourceId,
                topOnInterstitialAd = topOnAd,
                parameter = interstitialParameter,
                bidExtractionResult = extractionResult,
                initialAdInfo = adInfo,
            )
            val estimatedRevenue = topOnInterstitialAd.estimatedEcpm(adSourceInfo) ?: -1.0
            loadedAdHolder.set(topOnInterstitialAd)
            loadCallback.onAdLoadSuccess(topOnInterstitialAd)
            dispatchAdLoadedEvent(
                adSourceId = adSourceId,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                revenue = reportedRevenue.takeIf { it > 0.0 } ?: fallbackRevenue,
                poseidonConfigVersion = interstitialParameter.poseidonConfigVersion,
                adSourceName = adInfo?.networkName,
                estimatedRevenue = estimatedRevenue,
            )
            if (adInfo != null && extractionResult != null) {
                TopOnAdmobBidAnalysisReporter(
                    sessionId = interstitialParameter.sessionId,
                    sceneName = interstitialParameter.sceneName,
                    adUnitId = interstitialParameter.adUnitId,
                    adType = interstitialParameter.adType,
                    networkId = interstitialParameter.network,
                    sdkReportedRevenue = reportedRevenue,
                    sdkReportedPrecision = adInfo.ecpmPrecision,
                    extractionResult = extractionResult,
                    topAdNetworkPlacementId = adInfo.networkPlacementId,
                    topAdNetworkName = adInfo.networkName
                ).analyze()
            }
        }

        override fun onInterstitialAdLoadFail(error: AdError) {
            markAdRequestReturned()
            clearTopOnListeners()
            val code = error.code.toIntOrNull() ?: ERROR_CODE_UNKNOWN
            val message = error.desc
            dispatchAdFailedLoadedEvent(
                code = code,
                message = message,
                poseidonConfigVersion = interstitialParameter.poseidonConfigVersion
            )
            loadCallback.onAdLoadFailed(code = code, message = message)
        }

        override fun onInterstitialAdClicked(info: TUAdInfo) {
            loadedAdHolder.get()?.dispatchAdClickedEvent(info)
        }

        override fun onInterstitialAdShow(info: TUAdInfo) {
            loadedAdHolder.get()?.dispatchAdImpressionEvent(info)
        }

        override fun onInterstitialAdClose(info: TUAdInfo) {
            loadedAdHolder.getAndClear()?.apply {
                dispatchAdClosedEvent(info)
                runCatching {
                    destroy()
                }
            }
        }

        override fun onInterstitialAdVideoStart(info: TUAdInfo) {
            // no-op
        }

        override fun onInterstitialAdVideoEnd(info: TUAdInfo) {
            // no-op
        }

        override fun onInterstitialAdVideoError(error: AdError) {
            loadedAdHolder.getAndClear()?.apply {
                dispatchAdFailedToShowEvent(
                    error = error,
                    info = null,
                )
                runCatching {
                    destroy()
                }
            }
        }

        /** 当前 TopOn SDK 返回的广告信息 */
        private fun currentAdInfo(): TUAdInfo? = runCatching {
            topOnAd.checkAdStatus().getTUTopAdInfo()
        }.getOrNull()

        private fun clearTopOnListeners() {
            topOnAd.setAdListener(null)
            topOnAd.setAdRevenueListener(null)
        }

        /** 获取并清空当前缓存的广告对象 */
        private fun AtomicReference<TopOnInterstitialAd>.getAndClear(): TopOnInterstitialAd? {
            val value = get()
            set(null)
            return value
        }
    }
}
