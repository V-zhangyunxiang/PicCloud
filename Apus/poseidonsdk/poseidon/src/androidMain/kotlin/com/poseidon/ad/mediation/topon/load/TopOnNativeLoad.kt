package com.poseidon.ad.mediation.topon.load

import com.thinkup.core.api.TUAdInfo
import com.thinkup.core.api.AdError
import com.thinkup.nativead.api.TUNative
import com.thinkup.nativead.api.TUNativeNetworkListener
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNKNOWN
import com.poseidon.ad.main.adinfo.NativeAdBase
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.NativeAdLoader
import com.poseidon.ad.main.load.NativeParameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.processor.PlatformNativeAdLoadParameter
import com.poseidon.ad.mediation.topon.init.TopOnSetup
import com.poseidon.ad.mediation.topon.TopOnStatusDispatcher
import com.poseidon.ad.mediation.topon.resolveAdPaidInfo
import com.poseidon.ad.mediation.topon.toAdSourceInfo
import com.poseidon.ad.mediation.topon.show.TopOnNativeAd

internal class TopOnNativeLoad(parameter: NativeParameter) : NativeAdLoader(parameter = parameter) {

    /** 强类型原生参数，方便复用 */
    private val nativeParameter: NativeParameter = parameter

    override fun mediationAdLoad(
        loadParameter: PlatformNativeAdLoadParameter,
        loadCallback: InnerAdLoadCallback<NativeAdBase>
    ) {
        val topOnNativeAd = TUNative(loadParameter.context, nativeParameter.adUnitId, null)
        topOnNativeAd.setAdListener(
            TopOnNativeLoadListener(
                topOnNative = topOnNativeAd,
                loadCallback = loadCallback
            )
        )
        markAdRequestStarted()
        topOnNativeAd.makeAdRequest()
    }

    override fun mediationAdCheckBeforeLoad(): MediationSetup = TopOnSetup

    /** 负责监听 TopOn 原生加载事件并回调到 SDK */
    private inner class TopOnNativeLoadListener(
        private val topOnNative: TUNative,
        private val loadCallback: InnerAdLoadCallback<NativeAdBase>
    ) : TUNativeNetworkListener {

        override fun onNativeAdLoaded() {
            markAdRequestReturned()
            TopOnStatusDispatcher.dispatch {
                val atTopAdInfo = currentAdInfo()
                finalizeNativeAdLoaded(atTopAdInfo)
            }
        }

        private fun finalizeNativeAdLoaded(atTopAdInfo: TUAdInfo?) {
            val adSourceInfo = atTopAdInfo?.toAdSourceInfo()
            val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
            val topOnNativeAd = TopOnNativeAd(
                adSourceId = adSourceId,
                thirdPartyAd = topOnNative,
                parameter = nativeParameter,
                initialAdInfo = atTopAdInfo,
            )
            val estimatedRevenue = topOnNativeAd.estimatedEcpm(adSourceInfo) ?: -1.0
            loadCallback.onAdLoadSuccess(topOnNativeAd)
            dispatchAdLoadedEvent(
                adSourceId = adSourceId,
                adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                revenue = atTopAdInfo?.let {
                    resolveAdPaidInfo(
                        adInfo = it,
                        loadParameter = nativeParameter
                    ).revenue
                },
                poseidonConfigVersion = nativeParameter.poseidonConfigVersion,
                adSourceName = atTopAdInfo?.networkName,
                estimatedRevenue = estimatedRevenue,
            )
        }

        override fun onNativeAdLoadFail(error: AdError) {
            markAdRequestReturned()
            val code = error.code.toIntOrNull() ?: ERROR_CODE_UNKNOWN
            val message = error.desc
            dispatchAdFailedLoadedEvent(
                code = code,
                message = message,
                poseidonConfigVersion = nativeParameter.poseidonConfigVersion
            )
            loadCallback.onAdLoadFailed(code = code, message = message)
        }

        /** 当前 TopOn SDK 返回的广告信息 */
        private fun currentAdInfo(): TUAdInfo? = runCatching {
            topOnNative.checkAdStatus().getTUTopAdInfo()
        }.getOrNull()
    }
}
