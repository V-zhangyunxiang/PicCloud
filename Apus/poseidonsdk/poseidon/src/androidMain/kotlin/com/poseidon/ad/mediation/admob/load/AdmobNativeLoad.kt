package com.poseidon.ad.mediation.admob.load

import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.main.adinfo.ERROR_CODE_NETWORK_NO_INITIALIZATION
import com.poseidon.ad.main.adinfo.NativeAdBase
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.NativeAdLoader
import com.poseidon.ad.main.load.NativeParameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.processor.PlatformNativeAdLoadParameter
import com.poseidon.ad.mediation.admob.init.AdmobSetup
import com.poseidon.ad.mediation.admob.resolveAdSourceInfo
import com.poseidon.ad.mediation.admob.show.AdmobNativeAd
import java.util.concurrent.atomic.AtomicReference

internal class AdmobNativeLoad(parameter: NativeParameter) : NativeAdLoader(parameter = parameter) {

    /** 缓存当前构建的 AdMob 原生广告包装对象 */
    private val thirdPartyAdAtomic: AtomicReference<AdmobNativeAd> = AtomicReference()

    override fun mediationAdLoad(
        loadParameter: PlatformNativeAdLoadParameter,
        loadCallback: InnerAdLoadCallback<NativeAdBase>
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
        val adLoader = AdLoader.Builder(loadParameter.context, parameter.adUnitId)
            .forNativeAd { ad: NativeAd ->
                markAdRequestReturned()
                logInfo(TAG) {
                    "onNativeAdLoaded: ad=$ad, responseInfo=${ad.responseInfo}"
                }
                val adSourceInfo = ad.responseInfo.resolveAdSourceInfo()
                val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
                val admobNativeAd = AdmobNativeAd(
                    adSourceId = adSourceId,
                    thirdPartyAd = ad,
                    parameter = parameter as NativeParameter
                )
                dispatchAdLoadedEvent(
                    adSourceId = adSourceId,
                    adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                    revenue = null,
                    poseidonConfigVersion = parameter.poseidonConfigVersion,
                    adSourceName = adSourceInfo?.adSourceName,
                    estimatedRevenue = admobNativeAd.estimatedEcpm(adSourceInfo) ?: -1.0
                )
                thirdPartyAdAtomic.set(admobNativeAd)
                loadCallback.onAdLoadSuccess(admobNativeAd)
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
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

                override fun onAdClicked() {
                    thirdPartyAdAtomic.get()?.dispatchAdClickedEvent()
                }

                override fun onAdImpression() {
                    thirdPartyAdAtomic.get()?.dispatchAdImpressionEvent()
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .build()
            )
            .build()
        val adRequest = AdRequest.Builder().build()
        markAdRequestStarted()
        adLoader.loadAd(adRequest)
    }

    override fun mediationAdCheckBeforeLoad(): MediationSetup = AdmobSetup

    companion object {
        private const val TAG = "AdmobNativeAdLoader"
    }
}
