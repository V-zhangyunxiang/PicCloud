package com.poseidon.ad.mediation.topon.load

import com.thinkup.core.api.TUAdInfo
import com.thinkup.core.api.AdError
import com.thinkup.splashad.api.TUSplashAd
import com.thinkup.splashad.api.TUSplashAdEZListener
import com.thinkup.splashad.api.TUSplashAdExtraInfo
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNKNOWN
import com.poseidon.ad.main.adinfo.OpenAdBase
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.OpenAdLoader
import com.poseidon.ad.main.load.OpenParameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Admob
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.processor.PlatformOpenAdLoadParameter
import com.poseidon.ad.mediation.topon.init.TopOnSetup
import com.poseidon.ad.mediation.topon.TopOnAdmobExtractor
import com.poseidon.ad.mediation.topon.TopOnStatusDispatcher
import com.poseidon.ad.mediation.topon.resolveAdPaidInfo
import com.poseidon.ad.mediation.topon.toAdSourceInfo
import com.poseidon.ad.mediation.topon.show.TopOnOpenAd
import com.poseidon.ad.statistics.core.TopOnAdmobBidAnalysisReporter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * TopOn 开屏广告加载器，仅在此处管理 SDK 加载及监听。
 */
internal class TopOnOpenAdLoad(parameter: OpenParameter) : OpenAdLoader(parameter = parameter) {

    private var thirdPartyAd: TUSplashAd? = null

    override fun mediationAdLoad(
        loadParameter: PlatformOpenAdLoadParameter,
        loadCallback: InnerAdLoadCallback<OpenAdBase>
    ) {
        thirdPartyAd = TUSplashAd(
            loadParameter.context,
            parameter.adUnitId,
            object : TUSplashAdEZListener() {

                /** 缓存当前成功创建的 TopOn 开屏广告实例 */
                private var topOnOpenAdAtomic: AtomicReference<TopOnOpenAd> = AtomicReference()

                override fun onAdLoaded() {
                    markAdRequestReturned()
                    val loadedAd = thirdPartyAd
                    TopOnStatusDispatcher.dispatch {
                        val atTopAdInfo: TUAdInfo? = runCatching {
                            loadedAd?.checkAdStatus()?.getTUTopAdInfo()
                        }.getOrNull()
                        val adSourceInfo = atTopAdInfo?.toAdSourceInfo()
                        val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
                        val reportedRevenue = atTopAdInfo?.let {
                            resolveAdPaidInfo(adInfo = it, loadParameter = parameter).revenue
                        } ?: 0.0
                        val extractionResult = if (
                            loadedAd != null &&
                            adSourceId == AD_SOURCE_ID_Admob &&
                            reportedRevenue <= 0.0
                        ) {
                            runCatching {
                                TopOnAdmobExtractor.extractAdmobBidPriceWithDetails(loadedAd)
                            }.getOrNull()
                        } else {
                            null
                        }

                        finalizeLoadedAd(
                            loadedAd = loadedAd,
                            adInfo = atTopAdInfo,
                            extractionResult = extractionResult,
                            reportedRevenue = reportedRevenue,
                        )
                    }
                }

                private fun finalizeLoadedAd(
                    loadedAd: TUSplashAd?,
                    adInfo: TUAdInfo?,
                    extractionResult: TopOnAdmobExtractor.BidExtractionResult?,
                    reportedRevenue: Double
                ) {
                    if (loadedAd == null) {
                        val message = "TopOn reported open-ad load success without an ad instance"
                        dispatchAdFailedLoadedEvent(
                            code = ERROR_CODE_UNKNOWN,
                            message = message,
                            poseidonConfigVersion = parameter.poseidonConfigVersion,
                        )
                        loadCallback.onAdLoadFailed(code = ERROR_CODE_UNKNOWN, message = message)
                        return
                    }
                    val adSourceInfo = adInfo?.toAdSourceInfo()
                    val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
                    val fallbackRevenue = extractionResult?.winnerBidPrice?.takeIf { it > 0.0 }
                    val topOnOpenAd = TopOnOpenAd(
                        adSourceId = adSourceId,
                        thirdPartyAd = loadedAd,
                        parameter = parameter as OpenParameter,
                        bidExtractionResult = extractionResult,
                        initialAdInfo = adInfo,
                    )
                    val estimatedRevenue = topOnOpenAd.estimatedEcpm(adSourceInfo) ?: -1.0
                    topOnOpenAdAtomic.set(topOnOpenAd)
                    if (thirdPartyAd === loadedAd) {
                        thirdPartyAd = null
                    }
                    loadCallback.onAdLoadSuccess(topOnOpenAd)
                    dispatchAdLoadedEvent(
                        adSourceId = adSourceId,
                        adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                        revenue = reportedRevenue.takeIf { it > 0.0 } ?: fallbackRevenue,
                        poseidonConfigVersion = parameter.poseidonConfigVersion,
                        adSourceName = adInfo?.networkName,
                        estimatedRevenue = estimatedRevenue,
                    )
                    if (adInfo != null && extractionResult != null) {
                        TopOnAdmobBidAnalysisReporter(
                            sessionId = parameter.sessionId,
                            sceneName = parameter.sceneName,
                            adUnitId = parameter.adUnitId,
                            adType = parameter.adType,
                            networkId = parameter.network,
                            sdkReportedRevenue = reportedRevenue,
                            sdkReportedPrecision = adInfo.ecpmPrecision,
                            extractionResult = extractionResult,
                            topAdNetworkPlacementId = adInfo.networkPlacementId,
                            topAdNetworkName = adInfo.networkName
                        ).analyze()
                    }
                }

                override fun onNoAdError(error: AdError) {
                    markAdRequestReturned()
                    clearTopOnListeners()
                    val code = error.code.toIntOrNull() ?: ERROR_CODE_UNKNOWN
                    val message = error.desc
                    dispatchAdFailedLoadedEvent(
                        code = code,
                        message = message,
                        poseidonConfigVersion = parameter.poseidonConfigVersion
                    )
                    loadCallback.onAdLoadFailed(code = code, message = message)
                }

                override fun onAdClick(info: TUAdInfo) {
                    topOnOpenAdAtomic.get()?.dispatchAdClickedEvent(info)
                }

                override fun onAdShow(info: TUAdInfo) {
                    topOnOpenAdAtomic.get()?.dispatchAdImpressionEvent(info)
                }

                override fun onAdDismiss(info: TUAdInfo, inf: TUSplashAdExtraInfo?) {
                    topOnOpenAdAtomic.get()?.apply {
                        dispatchAdClosedEvent(info)
                        runCatching {
                            destroy()
                        }
                    }
                    topOnOpenAdAtomic.set(null)
                    thirdPartyAd = null
                }

                private fun clearTopOnListeners() {
                    thirdPartyAd?.setAdListener(null)
                    thirdPartyAd?.setAdRevenueListener(null)
                    thirdPartyAd = null
                }
            },
            TimeUnit.MINUTES.toMillis(5).toInt() /*default: 10000ms*/
        ).apply {
            markAdRequestStarted()
            loadAd()
        }
    }

    override fun mediationAdCheckBeforeLoad(): MediationSetup = TopOnSetup
}
