package com.poseidon.ad.mediation.admob.show

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.appopen.AppOpenAd
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_THIRD_PARTY_AD
import com.poseidon.ad.main.adinfo.OpenAdBase
import com.poseidon.ad.main.load.OpenParameter
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.processor.PlatformOpenAdShowParameter
import com.poseidon.ad.main.show.InnerAdShowConfig
import com.poseidon.ad.main.show.InnerFullScreenContentCallback
import com.poseidon.ad.mediation.admob.contentToString
import com.poseidon.ad.mediation.admob.resolveAdPaidInfo
import com.poseidon.ad.mediation.admob.resolveAdSourceInfo
import java.util.concurrent.atomic.AtomicReference

/**
 * AdMob 开屏包装类，负责展示及事件派发。
 */
internal class AdmobOpenAd(
    @AdSourceIdDef private val adSourceId: Int,
    private var thirdPartyAd: AppOpenAd?,
    private val parameter: OpenParameter
) : OpenAdBase() {

    /** 全屏内容回调引用 */
    private val fullScreenContentCallbackAtomic = AtomicReference<InnerFullScreenContentCallback>()

    init {
        updateEstimatedEcpmAdSourceInfo(thirdPartyAd?.responseInfo.resolveAdSourceInfo())
    }

    override fun getAdSourceId(): Int = adSourceId

    override fun show(
        showParameter: PlatformOpenAdShowParameter,
        showConfig: InnerAdShowConfig
    ): ShowResultCallback {
        val currentAd = thirdPartyAd ?: return ShowResultCallback.Failure(
            code = ERROR_CODE_NULL_THIRD_PARTY_AD,
            message = "AppOpenAd is null"
        )
        currentAd.setOnPaidEventListener { adValue ->
            updateEstimatedEcpmAdSourceInfo(currentAd.responseInfo.resolveAdSourceInfo())
            val adPaidInfo = resolveAdPaidInfo(
                responseInfo = currentAd.responseInfo,
                adValue = adValue,
                loadParameter = parameter
            )
            logInfo(TAG) {
                "onPaidEvent: adValue=${adValue.contentToString()}, adPaidInfo=$adPaidInfo"
            }
            dispatchOnPaidEvent(adPaidInfo)
        }
        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdClicked() {
                logInfo(TAG) { "Ad was clicked." }
                dispatchAdClickedEvent()
            }

            override fun onAdDismissedFullScreenContent() {
                logInfo(TAG) { "Ad dismissed fullscreen content." }
                dispatchAdClosedEvent()
                runCatching {
                    destroy()
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                logInfo(TAG) { "Ad failed to show fullscreen content." }
                dispatchAdFailedToShowEvent(adError)
                this@AdmobOpenAd.destroy()
            }

            override fun onAdImpression() {
                logInfo(TAG) { "Ad recorded an impression." }
                dispatchAdImpressionEvent()
            }

            override fun onAdShowedFullScreenContent() {
                logInfo(TAG) { "Ad showed fullscreen content." }
            }
        }
        currentAd.show(showParameter.activity)
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
        runCatching {
            destroy()
        }
    }

    override fun isReady(): Boolean = thirdPartyAd != null

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
