package com.poseidon.ad.mediation.max.show

import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.poseidon.ad.api.AdShowListener
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.main.adinfo.BannerAdBase
import com.poseidon.ad.main.load.BannerParameter
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdPaidInfo
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.mediation.max.contentToString
import com.poseidon.ad.mediation.max.resolveAdSourceInfo

/**
 * Max Banner 包装类，负责事件分发与监听回调。
 */
@Suppress("SpellCheckingInspection")
internal class MaxBannerAd(
    private val parameter: BannerParameter,
    private val listener: AdShowListener
) : BannerAdBase() {

    /** 当前广告源 ID，用于统计 */
    @Suppress("PropertyName")
    @AdSourceIdDef
    var _adSourceId: Int = AD_SOURCE_ID_Unknown

    override fun getAdSourceId(): Int = _adSourceId

    /** 派发付费事件 */
    fun publicDispatchOnPaidEvent(adPaidInfo: AdPaidInfo) {
        super.dispatchOnPaidEvent(adPaidInfo)
    }

    /** 派发点击事件 */
    fun dispatchAdClickedEvent(maxAd: MaxAd) {
        val adSourceInfo = maxAd.resolveAdSourceInfo()
        logInfo(TAG) {
            "Ad was clicked. maxAd=${maxAd.contentToString()}, adSourceInfo=${adSourceInfo}"
        }
        listener.onAdClicked()
        super.dispatchAdClickedEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = adSourceInfo?.adSourceName,
        )
    }

    /** 派发展示失败事件 */
    fun dispatchAdFailedToShowEvent(maxAd: MaxAd, error: MaxError) {
        val adSourceInfo = maxAd.resolveAdSourceInfo()
        logInfo(TAG) {
            "Ad failed to show. maxAd=${maxAd.contentToString()}, error=${error.contentToString()}, adSourceInfo=${adSourceInfo}"
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
        listener.onAdShowFailed(code = code, message = message)
    }

    /** 派发曝光事件 */
    fun dispatchAdImpressionEvent(maxAd: MaxAd) {
        val adSourceInfo = maxAd.resolveAdSourceInfo()
        logInfo(TAG) {
            "Ad recorded an impression. maxAd=${maxAd.contentToString()}, adSourceInfo=${adSourceInfo}"
        }
        listener.onAdShowSuccess()
        super.dispatchAdImpressionEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = adSourceInfo?.adSourceName,
        )
    }

    /** 派发关闭事件 */
    fun dispatchAdClosedEvent(maxAd: MaxAd?) {
        if (!isAdClosedAtomic.compareAndSet(expect = false, update = true)) {
            return
        }
        val adSourceInfo = maxAd.resolveAdSourceInfo()
        logInfo(TAG) {
            "Ad closed. maxAd=${maxAd?.contentToString()}, adSourceInfo=${adSourceInfo}"
        }
        listener.onAdClosed()
        super.dispatchAdClosedEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = adSourceInfo?.adSourceName,
        )
        runCatching {
            destroy()
        }
    }

    override fun onAdForceClosed() {
        dispatchAdClosedEvent(null)
    }

    override fun getParameter(): Parameter = parameter

    companion object {
        private const val TAG = "MaxBannerAd"
    }
}
