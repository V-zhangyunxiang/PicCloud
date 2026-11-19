package com.poseidon.ad.mediation.admob.show

import com.google.android.gms.ads.AdView
import com.poseidon.ad.api.AdShowListener
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.main.adinfo.BannerAdBase
import com.poseidon.ad.main.load.BannerParameter
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdPaidInfo
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.mediation.admob.resolveAdSourceInfo

/**
 * AdMob Banner 包装类，负责事件派发及监听器通知。
 */
@Suppress("SpellCheckingInspection")
internal class AdmobBannerAd(
    private val thirdPartyAd: AdView,
    private val parameter: BannerParameter,
    private val listener: AdShowListener
) : BannerAdBase() {

    /** 记录当前广告源 ID，供外部统计 */
    @Suppress("PropertyName")
    @AdSourceIdDef
    var _adSourceId: Int = AD_SOURCE_ID_Unknown

    override fun getAdSourceId(): Int = _adSourceId

    /** 派发付费事件 */
    fun publicDispatchOnPaidEvent(adPaidInfo: AdPaidInfo) {
        super.dispatchOnPaidEvent(adPaidInfo)
    }

    /** 派发点击事件并通知监听器 */
    fun dispatchAdClickedEvent() {
        logInfo(TAG) { "Ad was clicked." }
        listener.onAdClicked()
        val adSourceInfo = thirdPartyAd.responseInfo.resolveAdSourceInfo()
        super.dispatchAdClickedEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = adSourceInfo?.adSourceName,
        )
    }

    /** 派发曝光事件并通知监听器 */
    fun dispatchAdImpressionEvent() {
        logInfo(TAG) { "Ad recorded an impression." }
        listener.onAdShowSuccess()
        val adSourceInfo = thirdPartyAd.responseInfo.resolveAdSourceInfo()
        super.dispatchAdImpressionEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = adSourceInfo?.adSourceName,
        )
    }

    /** 派发关闭事件并通知监听器 */
    fun dispatchAdClosedEvent() {
        if (!isAdClosedAtomic.compareAndSet(expect = false, update = true)) {
            return
        }
        logInfo(TAG) { "Ad closed." }
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
        runCatching {
            destroy()
        }
    }

    override fun getParameter(): Parameter = parameter

    companion object {
        private const val TAG = "AdmobBannerAd"
    }
}
