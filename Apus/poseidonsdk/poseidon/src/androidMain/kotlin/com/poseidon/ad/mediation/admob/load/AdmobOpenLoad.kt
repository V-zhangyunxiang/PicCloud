package com.poseidon.ad.mediation.admob.load

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.main.adinfo.ERROR_CODE_NETWORK_NO_INITIALIZATION
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_THIRD_PARTY_AD
import com.poseidon.ad.main.adinfo.OpenAdBase
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.OpenAdLoader
import com.poseidon.ad.main.load.OpenParameter
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.processor.PlatformOpenAdLoadParameter
import com.poseidon.ad.main.processor.PlatformOpenAdShowParameter
import com.poseidon.ad.main.show.InnerAdShowConfig
import com.poseidon.ad.main.show.InnerFullScreenContentCallback
import com.poseidon.ad.mediation.admob.PartyCrawler
import com.poseidon.ad.mediation.admob.contentToString
import com.poseidon.ad.mediation.admob.init.AdmobSetup
import com.poseidon.ad.mediation.admob.resolveAdPaidInfo
import com.poseidon.ad.mediation.admob.resolveAdSourceInfo
import com.poseidon.ad.main.adinfo.AdSourceInfo
import com.poseidon.ad.mediation.admob.extPriceToRoundedRevenue
import java.util.concurrent.atomic.AtomicReference

internal class AdmobOpenLoad(parameter: OpenParameter) :
    OpenAdLoader(parameter = parameter) {

    override fun mediationAdLoad(
        loadParameter: PlatformOpenAdLoadParameter,
        loadCallback: InnerAdLoadCallback<OpenAdBase>
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
        val callback = object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                markAdRequestReturned()
                logInfo(TAG) {
                    "onAdFailedToLoad: adError=$adError"
                }
                val code = adError.code
                val message = adError.message
                dispatchAdFailedLoadedEvent(code = code, message = message, poseidonConfigVersion = parameter.poseidonConfigVersion)
                loadCallback.onAdLoadFailed(code = code, message = message)
            }

            override fun onAdLoaded(appOpenAd: AppOpenAd) {
                markAdRequestReturned()
                logInfo(TAG) {
                    "onAdLoaded: appOpenAd=$appOpenAd, responseInfo=${appOpenAd.responseInfo}"
                }
                val adSourceInfo = appOpenAd.responseInfo.resolveAdSourceInfo()
                val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
                val revenue: Double? = null
                PartyCrawler.resolveExtPriceAsync(
                    format = "appopen",
                    revenue = revenue,
                    sessionId = parameter.sessionId,
                    loadedAdSourceInstanceId = adSourceInfo?.rawSourceInstanceId,
                    responseInfo = appOpenAd.responseInfo,
                    appOpenAd = appOpenAd
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
                    val admobOpenAd = AdmobOpenAd(
                        adSourceId = adSourceId,
                        thirdPartyAd = appOpenAd,
                        parameter = parameter as OpenParameter,
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
                        estimatedRevenue = admobOpenAd.estimatedEcpm(adSourceInfo) ?: -1.0
                    )
                    loadCallback.onAdLoadSuccess(admobOpenAd)
                }
            }
        }
        val adRequest = AdRequest.Builder().build()
        markAdRequestStarted()
        AppOpenAd.load(loadParameter.context, parameter.adUnitId, adRequest, callback)
    }

    override fun mediationAdCheckBeforeLoad(): MediationSetup = AdmobSetup

    companion object {
        private const val TAG = "AdmobOpenAdLoader"
    }

   internal class AdmobOpenAd(
       @AdSourceIdDef private val adSourceId: Int,
       private var thirdPartyAd: AppOpenAd?,
       private val parameter: OpenParameter,
       initialAdSourceInfo: AdSourceInfo?,
       private val extractionResult: PartyCrawler.ExtractionResult? = null
    ) : OpenAdBase() {

       override fun getAdSourceId(): Int = adSourceId

        private val fullScreenContentCallbackAtomic: AtomicReference<InnerFullScreenContentCallback> =
            AtomicReference()

        init {
            updateEstimatedEcpmAdSourceInfo(initialAdSourceInfo)
        }

        override fun show(
            showParameter: PlatformOpenAdShowParameter,
            showConfig: InnerAdShowConfig
        ): ShowResultCallback {
            val thirdPartyAd = thirdPartyAd
                ?: return ShowResultCallback.Failure(
                    code = ERROR_CODE_NULL_THIRD_PARTY_AD,
                    message = "AppOpenAd is null"
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
                    this@AdmobOpenAd.destroy()
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
            private const val TAG = "AdmobOpenAd"
        }
    }
}
