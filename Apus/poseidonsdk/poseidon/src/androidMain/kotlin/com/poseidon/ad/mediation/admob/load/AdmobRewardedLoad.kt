package com.poseidon.ad.mediation.admob.load

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.main.adinfo.ERROR_CODE_NETWORK_NO_INITIALIZATION
import com.poseidon.ad.main.adinfo.RewardedAdBase
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.RewardedAdLoader
import com.poseidon.ad.main.load.RewardedParameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.processor.PlatformRewardedAdLoadParameter
import com.poseidon.ad.mediation.admob.PartyCrawler
import com.poseidon.ad.mediation.admob.init.AdmobSetup
import com.poseidon.ad.mediation.admob.resolveAdSourceInfo
import com.poseidon.ad.mediation.admob.show.AdmobRewardedAd
import com.poseidon.ad.mediation.admob.extPriceToRoundedRevenue


internal class AdmobRewardedLoad(parameter: RewardedParameter) : RewardedAdLoader(parameter = parameter) {
    override fun mediationAdLoad(loadParameter: PlatformRewardedAdLoadParameter, loadCallback: InnerAdLoadCallback<RewardedAdBase>) {
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
        val adRequest = AdRequest.Builder().build()
        val callback = object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                markAdRequestReturned()
                logInfo(TAG) {
                    "onAdFailedToLoad: $adError"
                }
                val code = adError.code
                val message = adError.message
                dispatchAdFailedLoadedEvent(code = code, message = message, poseidonConfigVersion = parameter.poseidonConfigVersion)
                loadCallback.onAdLoadFailed(code = code, message = message)
            }

            override fun onAdLoaded(ad: RewardedAd) {
                markAdRequestReturned()
                logInfo(TAG) {
                    "onAdLoaded: ad=$ad, responseInfo=${ad.responseInfo}"
                }
                val adSourceInfo = ad.responseInfo.resolveAdSourceInfo()
                val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
                val revenue: Double? = null
                PartyCrawler.resolveExtPriceAsync(
                    format = "rewarded",
                    revenue = revenue,
                    sessionId = parameter.sessionId,
                    loadedAdSourceInstanceId = adSourceInfo?.rawSourceInstanceId,
                    responseInfo = ad.responseInfo,
                    rewardedAd = ad
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
                    val admobRewardedAd = AdmobRewardedAd(
                        adSourceId = adSourceId,
                        thirdPartyAd = ad,
                        parameter = parameter as RewardedParameter,
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
                        estimatedRevenue = admobRewardedAd.estimatedEcpm(adSourceInfo) ?: -1.0
                    )
                    loadCallback.onAdLoadSuccess(admobRewardedAd)
                }
            }
        }
        markAdRequestStarted()
        RewardedAd.load(loadParameter.context, parameter.adUnitId, adRequest, callback)
    }

    override fun mediationAdCheckBeforeLoad(): MediationSetup = AdmobSetup

    companion object {
        private const val TAG = "AdmobRewardedAdLoader"
    }

}
