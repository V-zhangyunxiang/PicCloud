package com.poseidon.ad.mediation.admob.load

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.poseidon.ad.api.AdShowListener
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.main.adinfo.BannerAdBase
import com.poseidon.ad.main.adinfo.ERROR_CODE_NETWORK_NO_INITIALIZATION
import com.poseidon.ad.main.load.BannerAdLoad
import com.poseidon.ad.main.load.BannerParameter
import com.poseidon.ad.main.load.InnerAdLoadAndShowCallback
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdPaidInfo
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.processor.PlatformBannerAdLoadAndShowParameter
import com.poseidon.ad.mediation.admob.contentToString
import com.poseidon.ad.mediation.admob.init.AdmobSetup
import com.poseidon.ad.mediation.admob.resolveAdPaidInfo
import com.poseidon.ad.mediation.admob.resolveAdSourceInfo
import java.util.concurrent.atomic.AtomicReference

internal class AdmobBannerLoad(parameter: BannerParameter) :
    BannerAdLoad(parameter = parameter) {

    private var adViewAtomic: AtomicReference<AdView> = AtomicReference()

    override fun mediationAdLoad(
        loadParameter: PlatformBannerAdLoadAndShowParameter,
        loadCallback: InnerAdLoadAndShowCallback<BannerAdBase>
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
        val context = loadParameter.context
        if(context is AppCompatActivity){
            context.lifecycle.addObserver(object : DefaultLifecycleObserver{
                override fun onDestroy(owner: LifecycleOwner) {
                    destroy()
                }
            })
        }

        // Create a new ad view.
        val adView = AdView(loadParameter.context)
        adView.adUnitId = parameter.adUnitId
        adView.setAdSize(AdSize.BANNER)
        adViewAtomic.set(adView)

        // Replace ad container with new ad view.
        loadParameter.adViewContainer.removeAllViews()
        loadParameter.adViewContainer.addView(adView)

        val admobBannerAd = AdmobBannerAd(
            thirdPartyAd = adView,
            parameter = parameter as BannerParameter,
            listener = loadCallback
        )
        adView.setOnPaidEventListener { adValue ->
            val adPaidInfo = resolveAdPaidInfo(
                responseInfo = adView.responseInfo,
                adValue = adValue,
                loadParameter = parameter,
            )
            logInfo(TAG) {
                "onPaidEvent: adValue=${adValue.contentToString()}, adPaidInfo=$adPaidInfo"
            }
            admobBannerAd.publicDispatchOnPaidEvent(adPaidInfo)
        }
        adView.adListener = object : com.google.android.gms.ads.AdListener() {
            override fun onAdClicked() {
                // Code to be executed when the user clicks on an ad.
                admobBannerAd.dispatchAdClickedEvent()
            }

            override fun onAdClosed() {
                // Code to be executed when the user is about to return
                // to the app after tapping on an ad.
                admobBannerAd.dispatchAdClosedEvent()
                runCatching {
                    destroy()
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                markAdRequestReturned()
                // Code to be executed when an ad request fails.
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

            override fun onAdImpression() {
                // Code to be executed when an impression is recorded
                // for an ad.
                admobBannerAd.dispatchAdImpressionEvent()
            }

            override fun onAdLoaded() {
                markAdRequestReturned()
                // Code to be executed when an ad finishes loading.
                logInfo(TAG) {
                    "onAdLoaded: adView=$adView, responseInfo=${adView.responseInfo}"
                }
                val adSourceInfo = adView.responseInfo.resolveAdSourceInfo()
                val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
                dispatchAdLoadedEvent(
                    adSourceId = adSourceId,
                    adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                    revenue = null,
                    poseidonConfigVersion = parameter.poseidonConfigVersion,
                    adSourceName = adSourceInfo?.adSourceName,
                    estimatedRevenue = -1.0
                )
                admobBannerAd._adSourceId = adSourceId
                loadCallback.onAdLoadSuccess(admobBannerAd)
            }

            override fun onAdOpened() {
                // Code to be executed when an ad opens an overlay that
                // covers the screen.
            }
        }
        val adRequest = AdRequest.Builder().build()
        markAdRequestStarted()
        adView.loadAd(adRequest)
    }

    override fun mediationAdCheckBeforeLoad(): MediationSetup = AdmobSetup

    override fun destroy() {
        logInfo(TAG) { "destroy" }
        adViewAtomic.getAndSet(null)?.destroy()
    }

    @Suppress("SpellCheckingInspection")
    private class AdmobBannerAd(
        private val thirdPartyAd: AdView,
        private val parameter: BannerParameter,
        private val listener: AdShowListener,
    ) : BannerAdBase() {

        @Suppress("PropertyName")
        @AdSourceIdDef
        var _adSourceId: Int = AD_SOURCE_ID_Unknown

        override fun getAdSourceId(): Int = _adSourceId

        fun publicDispatchOnPaidEvent(adPaidInfo: AdPaidInfo) {
            super.dispatchOnPaidEvent(adPaidInfo)
        }

        fun dispatchAdClickedEvent() {
            logInfo(TAG) {
                "Ad was clicked."
            }
            listener.onAdClicked()
            val adSourceInfo = thirdPartyAd.responseInfo.resolveAdSourceInfo()
            super.dispatchAdClickedEvent(
                adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                adSourceName = adSourceInfo?.adSourceName,
            )
        }

        fun dispatchAdImpressionEvent() {
            logInfo(TAG) {
                "Ad recorded an impression."
            }
            listener.onAdShowSuccess()
            val adSourceInfo = thirdPartyAd.responseInfo.resolveAdSourceInfo()
            super.dispatchAdImpressionEvent(
                adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                adSourceName = adSourceInfo?.adSourceName,
            )
        }

        fun dispatchAdClosedEvent() {
            if (!isAdClosedAtomic.compareAndSet(expect = false, update = true)) {
                return
            }
            logInfo(TAG) {
                "Ad closed."
            }
            listener.onAdClosed()
            val adSourceInfo = thirdPartyAd.responseInfo.resolveAdSourceInfo()
            super.dispatchAdClosedEvent(
                adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                adSourceName = adSourceInfo?.adSourceName,
            )
        }

        override fun onAdForceClosed() {
            dispatchAdClosedEvent()
        }

        override fun getParameter(): Parameter = parameter
    }

    companion object {
        private const val TAG = "AdmobBannerAdLoader"
    }
}
