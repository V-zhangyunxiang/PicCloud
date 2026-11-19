package com.poseidon.ad.mediation.max.show

import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
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
import com.poseidon.ad.mediation.max.contentToString
import com.poseidon.ad.mediation.max.resolveAdPaidInfo
import com.poseidon.ad.mediation.max.resolveAdSourceInfo
import java.util.concurrent.atomic.AtomicReference

/**
 * Max 开屏包装类，负责展示、事件分发及生命周期管理。
 */
internal class MaxOpenAd(
    @AdSourceIdDef private val adSourceId: Int,
    private var thirdPartyAd: MaxAppOpenAd?,
    private var maxAd: MaxAd?,
    private val parameter: OpenParameter
) : OpenAdBase() {

    /** 全屏内容回调引用，保证生命周期一致 */
    private val fullScreenContentCallbackAtomic = AtomicReference<InnerFullScreenContentCallback>()

    init {
        updateEstimatedEcpmAdSourceInfo(maxAd?.resolveAdSourceInfo())
    }

    override fun getAdSourceId(): Int = adSourceId

    override fun show(
        showParameter: PlatformOpenAdShowParameter,
        showConfig: InnerAdShowConfig
    ): ShowResultCallback {
        val currentAd = thirdPartyAd ?: return ShowResultCallback.Failure(
            code = ERROR_CODE_NULL_THIRD_PARTY_AD,
            message = "MaxOpenAd is null"
        )
        currentAd.setRevenueListener { ad ->
            val adSourceInfo = ad.resolveAdSourceInfo()
            updateEstimatedEcpmAdSourceInfo(adSourceInfo)
            val adPaidInfo = resolveAdPaidInfo(
                maxAd = ad,
                loadParameter = parameter,
                adSourceInfo = adSourceInfo
            )
            logInfo(TAG) {
                "setRevenueListener: maxAd=${ad.contentToString()}, adPaidInfo=$adPaidInfo"
            }
            dispatchOnPaidEvent(adPaidInfo)
        }
        currentAd.showAd()
        return ShowResultCallback.Success
    }

    override fun setFullScreenContentCallback(fullScreenContentCallback: InnerFullScreenContentCallback) {
        fullScreenContentCallbackAtomic.set(fullScreenContentCallback)
    }

    /** 派发曝光事件并触发展示回调 */
    fun dispatchAdImpressionEvent(maxAd: MaxAd) {
        val adSourceInfo = maxAd.resolveAdSourceInfo()
        updateEstimatedEcpmAdSourceInfo(adSourceInfo)
        logInfo(TAG) {
            "Ad recorded an impression. maxAd=${maxAd.contentToString()}, adSourceInfo=${adSourceInfo}"
        }
        super.dispatchAdImpressionEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = adSourceInfo?.adSourceName,
        )
        fullScreenContentCallbackAtomic.get()?.onAdShowedFullScreenContent()
    }

    /** 派发展示失败事件并同步回调 */
    fun dispatchAdFailedToShowEvent(maxAd: MaxAd, error: MaxError) {
        val adSourceInfo = maxAd.resolveAdSourceInfo()
        updateEstimatedEcpmAdSourceInfo(adSourceInfo)
        logInfo(TAG) {
            "Ad failed to show fullscreen content. maxAd=${maxAd.contentToString()}, error=${error.contentToString()}, adSourceInfo=${adSourceInfo}"
        }
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

    /** 派发点击事件并同步回调 */
    fun dispatchAdClickedEvent(maxAd: MaxAd) {
        val adSourceInfo = maxAd.resolveAdSourceInfo()
        updateEstimatedEcpmAdSourceInfo(adSourceInfo)
        logInfo(TAG) {
            "Ad was clicked. maxAd=${maxAd.contentToString()}, adSourceInfo=$adSourceInfo"
        }
        super.dispatchAdClickedEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = adSourceInfo?.adSourceName,
        )
        fullScreenContentCallbackAtomic.get()?.onAdClicked()
    }

    /** 派发关闭事件并同步回调 */
    fun dispatchAdClosedEvent(maxAd: MaxAd?) {
        if (!isAdClosedAtomic.compareAndSet(expect = false, update = true)) {
            return
        }
        val adSourceInfo = maxAd.resolveAdSourceInfo()
        updateEstimatedEcpmAdSourceInfo(adSourceInfo)
        logInfo(TAG) {
            "Ad dismissed fullscreen content. maxAd=${maxAd?.contentToString()}, adSourceInfo=$adSourceInfo"
        }
        super.dispatchAdClosedEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = adSourceInfo?.adSourceName,
        )
        fullScreenContentCallbackAtomic.get()?.onAdDismissedFullScreenContent()
        runCatching {
            destroy()
        }
    }

    override fun onAdForceClosed() {
        dispatchAdClosedEvent(maxAd)
    }

    override fun isReady(): Boolean = thirdPartyAd != null

    override fun getParameter(): Parameter = parameter

    override fun eCPM(): Double? = maxAd?.let { ad ->
        val adSourceInfo = ad.resolveAdSourceInfo()
        updateEstimatedEcpmAdSourceInfo(adSourceInfo)
        resolveAdPaidInfo(
            maxAd = ad,
            loadParameter = parameter,
            adSourceInfo = adSourceInfo
        ).eCPM.takeIf { it > 0.0 } ?: estimatedEcpm(adSourceInfo)
    } ?: super.eCPM()

    override fun destroy() {
        thirdPartyAd?.setListener(null)
        thirdPartyAd?.setRevenueListener(null)
        thirdPartyAd?.destroy()
        thirdPartyAd = null
        maxAd = null
        fullScreenContentCallbackAtomic.set(null)
    }

    companion object {
        private const val TAG = "MaxOpenAd"
    }
}
