package com.poseidon.ad.main.load

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.main.adinfo.AdSourceInfo
import com.poseidon.ad.main.adinfo.ERROR_CODE_NETWORK_NO_INITIALIZATION
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_THIRD_PARTY_AD
import com.poseidon.ad.main.adinfo.InterstitialAdBase
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.processor.PlatformInterstitialAdLoadParameter
import com.poseidon.ad.main.processor.PlatformInterstitialAdShowParameter
import com.poseidon.ad.main.show.InnerAdShowConfig
import com.poseidon.ad.main.show.InnerFullScreenContentCallback
import com.poseidon.ad.mediation.admob.PartyCrawler
import com.poseidon.ad.mediation.admob.contentToString
import com.poseidon.ad.mediation.admob.extPriceToRoundedRevenue
import com.poseidon.ad.mediation.admob.init.AdmobSetup
import com.poseidon.ad.mediation.admob.resolveAdPaidInfo
import com.poseidon.ad.mediation.admob.resolveAdSourceInfo
import java.util.concurrent.atomic.AtomicReference

internal class AdmobInterstitialLoad(parameter: InterstitialParameter) :
    InterstitialAdLoader(parameter = parameter) {

    override fun mediationAdLoad(
        loadParameter: PlatformInterstitialAdLoadParameter,
        loadCallback: InnerAdLoadCallback<InterstitialAdBase>
    ) {
        if (!AdmobSetup.isInitComplete()) {
            val code = ERROR_CODE_NETWORK_NO_INITIALIZATION
            val message = ""
            dispatchAdFailedLoadedEvent(
                code = code,
                message = message,
                poseidonConfigVersion = parameter.poseidonConfigVersion
            )
            loadCallback.onAdLoadFailed(code = code, message = message)
            return
        }
        val callback = object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                markAdRequestReturned()
                logInfo(TAG) {
                    "onAdFailedToLoad: adError=$adError"
                }
                val code = adError.code
                val message = adError.message
                dispatchAdFailedLoadedEvent(
                    code = code,
                    message = message,
                    poseidonConfigVersion = parameter.poseidonConfigVersion
                )
                loadCallback.onAdLoadFailed(code = code, message = message)
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                markAdRequestReturned()
                logInfo(TAG) {
                    "onAdLoaded: interstitialAd=$interstitialAd, responseInfo=${interstitialAd.responseInfo}"
                }
                val adSourceInfo = interstitialAd.responseInfo.resolveAdSourceInfo()
                val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
                val revenue: Double? = null
                PartyCrawler.resolveExtPriceAsync(
                    format = "interstitial",
                    revenue = revenue,
                    sessionId = parameter.sessionId,
                    loadedAdSourceInstanceId = adSourceInfo?.rawSourceInstanceId,
                    responseInfo = interstitialAd.responseInfo,
                    interstitialAd = interstitialAd
                ) { extraction ->
                    PartyCrawler.report(
                        result = extraction,
                        reportContext = PartyCrawler.ReportContext(
                            parameter = parameter,
                            adSourceInfo = adSourceInfo,
                            adSourceId = adSourceId,
                            revenue = revenue
                        )
                    )
                    val admobInterstitialAd = AdmobInterstitialAd(
                        adSourceId = adSourceId,
                        thirdPartyAd = interstitialAd,
                        parameter = parameter as InterstitialParameter,
                        initialAdSourceInfo = adSourceInfo,
                        extractionResult = extraction
                    )
                    val mRevenue = extPriceToRoundedRevenue(extraction.extPrice)
                    dispatchAdLoadedEvent(
                        adSourceId = adSourceId,
                        adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                        revenue = mRevenue,
                        poseidonConfigVersion = parameter.poseidonConfigVersion,
                        adSourceName = adSourceInfo?.adSourceName,
                        estimatedRevenue = admobInterstitialAd.estimatedEcpm(adSourceInfo) ?: -1.0,
                    )
                    loadCallback.onAdLoadSuccess(admobInterstitialAd)
                }
            }
        }
        val adRequest = AdRequest.Builder().build()
        markAdRequestStarted()
        InterstitialAd.load(loadParameter.context, parameter.adUnitId, adRequest, callback)
    }

    override fun mediationAdCheckBeforeLoad(): MediationSetup = AdmobSetup

    companion object {
        private const val TAG = "AdmobInterstitialAdLoader"
    }

    internal class AdmobInterstitialAd(
        @AdSourceIdDef private val adSourceId: Int,
        private var thirdPartyAd: InterstitialAd?,
        private val parameter: InterstitialParameter,
        initialAdSourceInfo: AdSourceInfo?,
        private val extractionResult: PartyCrawler.ExtractionResult? = null
    ) : InterstitialAdBase() {

        override fun getAdSourceId(): Int = adSourceId

        private val fullScreenContentCallbackAtomic: AtomicReference<InnerFullScreenContentCallback> =
            AtomicReference()

        init {
            updateEstimatedEcpmAdSourceInfo(initialAdSourceInfo)
        }

        override fun show(
            showParameter: PlatformInterstitialAdShowParameter,
            showConfig: InnerAdShowConfig
        ): ShowResultCallback {
            val thirdPartyAd = thirdPartyAd
                ?: return ShowResultCallback.Failure(
                    code = ERROR_CODE_NULL_THIRD_PARTY_AD,
                    message = "AdmobInterstitialAd is null"
                )

            thirdPartyAd.setOnPaidEventListener { adValue ->
                updateEstimatedEcpmAdSourceInfo(thirdPartyAd.responseInfo.resolveAdSourceInfo())
                val adPaidInfo = resolveAdPaidInfo(
                    responseInfo = thirdPartyAd.responseInfo,
                    adValue = adValue,
                    loadParameter = parameter,
                )
                logInfo(TAG) {
                    "onPaidEvent: adValue=${adValue.contentToString()}, adPaidInfo=$adPaidInfo"
                }
                dispatchOnPaidEvent(adPaidInfo)
            }

            thirdPartyAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdClicked() {
                    // Called when a click is recorded for an ad.
                    logInfo(TAG) {
                        "Ad was clicked."
                    }
                    dispatchAdClickedEvent()
                }

                override fun onAdDismissedFullScreenContent() {
                    // Called when ad is dismissed.
                    logInfo(TAG) {
                        "Ad dismissed fullscreen content."
                    }
                    dispatchAdClosedEvent()
                   runCatching {
                       destroy()
                   }
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    // Called when ad fails to show.
                    logInfo(TAG) {
                        "Ad failed to show fullscreen content."
                    }
                    dispatchAdFailedToShowEvent(adError)
                    this@AdmobInterstitialAd.destroy()
                }

                override fun onAdImpression() {
                    // Called when an impression is recorded for an ad.
                    logInfo(TAG) {
                        "Ad recorded an impression."
                    }
                    dispatchAdImpressionEvent()
                }

                override fun onAdShowedFullScreenContent() {
                    // Called when ad is shown.
                    logInfo(TAG) {
                        "Ad showed fullscreen content."
                    }
                }
            }

            thirdPartyAd.show(showParameter.activity)
            return ShowResultCallback.Success
        }

        override fun setFullScreenContentCallback(fullScreenContentCallback: InnerFullScreenContentCallback) {
            fullScreenContentCallbackAtomic.set(fullScreenContentCallback)
        }

        fun dispatchAdImpressionEvent() {
            val adSourceInfo = thirdPartyAd?.responseInfo.resolveAdSourceInfo()
            updateEstimatedEcpmAdSourceInfo(adSourceInfo)
            super.dispatchAdImpressionEvent(
                adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                adSourceName = adSourceInfo?.adSourceName,
            )
            fullScreenContentCallbackAtomic.get()?.onAdShowedFullScreenContent()
        }

        fun dispatchAdFailedToShowEvent(error: AdError) {
            val adSourceInfo = thirdPartyAd?.responseInfo.resolveAdSourceInfo()
            updateEstimatedEcpmAdSourceInfo(adSourceInfo)
            val code = error.code
            val message = error.message
            super.dispatchAdFailedToShowEvent(
                code = code,
                message = message,
                adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                adSourceName = adSourceInfo?.adSourceName,
            )
            fullScreenContentCallbackAtomic.get()?.onAdFailedToShowFullScreenContent(
                code = code,
                message = message
            )
        }

        fun dispatchAdClickedEvent() {
            val adSourceInfo = thirdPartyAd?.responseInfo.resolveAdSourceInfo()
            updateEstimatedEcpmAdSourceInfo(adSourceInfo)
            super.dispatchAdClickedEvent(
                adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                adSourceName = adSourceInfo?.adSourceName,
            )
            fullScreenContentCallbackAtomic.get()?.onAdClicked()
        }

        fun dispatchAdClosedEvent() {
            if (!isAdClosedAtomic.compareAndSet(expect = false, update = true)) {
                return
            }
            val adSourceInfo = thirdPartyAd?.responseInfo.resolveAdSourceInfo()
            updateEstimatedEcpmAdSourceInfo(adSourceInfo)
            super.dispatchAdClosedEvent(
                adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                adSourceName = adSourceInfo?.adSourceName,
            )
            fullScreenContentCallbackAtomic.get()?.onAdDismissedFullScreenContent()
        }

        override fun onAdForceClosed() {
            dispatchAdClosedEvent()
        }

        override fun isReady(): Boolean {
            return thirdPartyAd != null
        }

        override fun eCPM(): Double? {
            val extEcpm = extractionResult?.extPrice ?: 0.0
            return if (extEcpm > 0.0) extEcpm else super.eCPM()
        }

        override fun getParameter(): Parameter = parameter

        override fun destroy() {
            thirdPartyAd?.fullScreenContentCallback = null
            thirdPartyAd?.onPaidEventListener = null
            thirdPartyAd = null
            fullScreenContentCallbackAtomic.set(null)
        }

        companion object {
            private const val TAG = "AdmobInterstitialAd"
        }
    }
}
