package com.poseidon.ad.mediation.topon.show

import com.thinkup.core.api.TUAdInfo
import com.poseidon.ad.api.AdShowListener
import com.poseidon.ad.main.adinfo.BannerAdBase
import com.poseidon.ad.main.load.BannerParameter
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdPaidInfo
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.mediation.topon.toAdSourceInfo

@Suppress("SpellCheckingInspection")
internal class TopOnBannerAd(
    private val parameter: BannerParameter,
    private val listener: AdShowListener
) : BannerAdBase() {

    /** 记录广告来源 ID，用于事件上报 */
    @Suppress("PropertyName")
    @AdSourceIdDef
    var _adSourceId: Int = AD_SOURCE_ID_Unknown

    override fun getAdSourceId(): Int = _adSourceId

    /** 更新当前广告的来源 ID */
    fun updateAdSourceId(adSourceId: Int) {
        _adSourceId = adSourceId
    }

    /** 对外暴露付费事件派发 */
    fun publicDispatchOnPaidEvent(adPaidInfo: AdPaidInfo) {
        super.dispatchOnPaidEvent(adPaidInfo)
    }

    /** 统一广告点击事件派发流程 */
    fun dispatchAdClickedEvent(info: TUAdInfo?) {
        val adSourceInfo = info?.toAdSourceInfo()
        dispatchAdClickedEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = info?.networkName
        )
        listener.onAdClicked()
    }

    /** 统一展示曝光事件派发流程 */
    fun dispatchAdImpressionEvent(info: TUAdInfo?) {
        val adSourceInfo = info?.toAdSourceInfo()
        dispatchAdImpressionEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = info?.networkName
        )
        listener.onAdShowSuccess()
    }

    /** 统一广告关闭事件派发流程 */
    fun dispatchAdClosedEvent(info: TUAdInfo?) {
        if (!isAdClosedAtomic.compareAndSet(expect = false, update = true)) {
            return
        }
        val adSourceInfo = info?.toAdSourceInfo()
        listener.onAdClosed()
        super.dispatchAdClosedEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = info?.networkName
        )
        runCatching {
            destroy()
        }
    }

    override fun onAdForceClosed() {
        dispatchAdClosedEvent(null)
    }

    override fun getParameter(): Parameter = parameter
}
