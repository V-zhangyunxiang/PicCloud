package com.poseidon.ad.mediation.topon.show

import com.thinkup.core.api.TUAdConst
import com.thinkup.core.api.TUAdInfo
import com.thinkup.core.api.TUShowConfig
import com.thinkup.core.api.AdError
import com.thinkup.rewardvideo.api.TURewardVideoAd
import com.poseidon.ad.api.OnUserEarnedRewardListener
import com.poseidon.ad.api.RewardItem
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_THIRD_PARTY_AD
import com.poseidon.ad.main.adinfo.ERROR_CODE_THIRD_PARTY_AD_NOT_READY
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNKNOWN
import com.poseidon.ad.main.adinfo.RewardedAdBase
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.load.RewardedParameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.processor.PlatformRewardedAdShowParameter
import com.poseidon.ad.main.show.InnerAdShowConfig
import com.poseidon.ad.main.show.InnerFullScreenContentCallback
import com.poseidon.ad.mediation.topon.resolveAdPaidInfo
import com.poseidon.ad.mediation.topon.TopOnAdmobExtractor
import com.poseidon.ad.mediation.topon.toAdSourceInfo
import java.util.concurrent.atomic.AtomicReference

/**
 * TopOn 激励广告包装类，负责展示、事件分发与回调。
 */
internal class TopOnRewardedAd(
    @AdSourceIdDef private val adSourceId: Int,
    private var thirdPartyAd: TURewardVideoAd?,
    private val parameter: RewardedParameter,
    private val bidExtractionResult: TopOnAdmobExtractor.BidExtractionResult? = null,
    initialAdInfo: TUAdInfo? = null,
) : RewardedAdBase() {

    /** 全屏内容回调引用，保障回调生命周期 */
    private val fullScreenContentCallbackAtomic =
        AtomicReference<InnerFullScreenContentCallback>()
    /** 激励收益回调引用 */
    private val onUserEarnedRewardListenerAtomic =
        AtomicReference<OnUserEarnedRewardListener>()
    private val latestAdInfoAtomic = AtomicReference(initialAdInfo)

    init {
        rememberAdInfo(initialAdInfo)
    }

    override fun getAdSourceId(): Int = adSourceId

    override fun show(
        showParameter: PlatformRewardedAdShowParameter,
        showConfig: InnerAdShowConfig,
        onUserEarnedRewardListener: OnUserEarnedRewardListener?
    ): ShowResultCallback {
        val currentAd = thirdPartyAd ?: return ShowResultCallback.Failure(
            code = ERROR_CODE_NULL_THIRD_PARTY_AD,
            message = "TopOnRewardedAd is null"
        )
        if (!isReady()) {
            return ShowResultCallback.Failure(
                code = ERROR_CODE_THIRD_PARTY_AD_NOT_READY,
                message = "TopOnRewardedAd is not ready"
            )
        }
        currentAd.setAdRevenueListener { adInfo ->
            rememberAdInfo(adInfo)
            val adPaidInfo = resolveAdPaidInfo(adInfo = adInfo, loadParameter = parameter)
            logInfo(TAG) {
                "onAdRevenue: adInfo = $adInfo, adPaidInfo=$adPaidInfo"
            }
            dispatchOnPaidEvent(adPaidInfo)
        }
        onUserEarnedRewardListenerAtomic.set(onUserEarnedRewardListener)
        currentAd.show(
            showParameter.activity,
            TUShowConfig.Builder()
                .scenarioId(showConfig.sceneName)
                .build()
        )
        return ShowResultCallback.Success
    }

    override fun setFullScreenContentCallback(fullScreenContentCallback: InnerFullScreenContentCallback) {
        fullScreenContentCallbackAtomic.set(fullScreenContentCallback)
    }

    fun dispatchAdImpressionEvent(info: TUAdInfo) {
        rememberAdInfo(info)
        val adSourceInfo = info.toAdSourceInfo()
        super.dispatchAdImpressionEvent(
            adSourceId = adSourceInfo.adSourceId,
            adSourcePlacementId = adSourceInfo.adSourcePlacementId,
            adSourceName = info.networkName
        )
        fullScreenContentCallbackAtomic.get()?.onAdShowedFullScreenContent()
    }

    fun dispatchAdFailedToShowEvent(error: AdError, info: TUAdInfo) {
        rememberAdInfo(info)
        val adSourceInfo = info.toAdSourceInfo()
        val code = error.code.toIntOrNull() ?: ERROR_CODE_UNKNOWN
        val message = error.desc
        super.dispatchAdFailedToShowEvent(
            code = code,
            message = message,
            adSourceId = adSourceInfo.adSourceId,
            adSourcePlacementId = adSourceInfo.adSourcePlacementId,
            adSourceName = info.networkName
        )
        fullScreenContentCallbackAtomic.get()?.onAdFailedToShowFullScreenContent(
            code = code,
            message = message
        )
    }

    fun dispatchAdClickedEvent(info: TUAdInfo) {
        rememberAdInfo(info)
        val adSourceInfo = info.toAdSourceInfo()
        super.dispatchAdClickedEvent(
            adSourceId = adSourceInfo.adSourceId,
            adSourcePlacementId = adSourceInfo.adSourcePlacementId,
            adSourceName = info.networkName
        )
        fullScreenContentCallbackAtomic.get()?.onAdClicked()
    }

    fun dispatchAdClosedEvent(info: TUAdInfo?) {
        if (!isAdClosedAtomic.compareAndSet(expect = false, update = true)) {
            return
        }
        val effectiveInfo = info ?: latestAdInfoAtomic.get()
        rememberAdInfo(effectiveInfo)
        val adSourceInfo = effectiveInfo?.toAdSourceInfo()
        super.dispatchAdClosedEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = effectiveInfo?.networkName
        )
        fullScreenContentCallbackAtomic.get()?.onAdDismissedFullScreenContent()
        runCatching {
            destroy()
        }
    }

    override fun onAdForceClosed() {
        dispatchAdClosedEvent(null)
    }

    fun dispatchUserEarnedRewardEvent(info: TUAdInfo) {
        rememberAdInfo(info)
        val item = info.extInfoMap?.run {
            val name = get(TUAdConst.KEY.REWARD_NAME).toString()
            val amount = get(TUAdConst.KEY.REWARD_AMOUNT).toString().toIntOrNull()
            RewardItem(amount = amount ?: 1, type = name)
        } ?: RewardItem.UNKNOWN
        onUserEarnedRewardListenerAtomic.get()?.onUserEarnedReward(item)
    }

    override fun isReady(): Boolean = thirdPartyAd != null

    override fun getParameter(): Parameter = parameter

    override fun eCPM(): Double? {
        val adInfo = latestAdInfoAtomic.get()
        val adSourceInfo = adInfo?.toAdSourceInfo()
        return adInfo?.let { resolveAdPaidInfo(adInfo = it, loadParameter = parameter).eCPM }
            ?.takeIf { it > 0.0 }
            ?: bidExtractionResult?.winnerEcpm?.takeIf { it > 0.0 }
            ?: estimatedEcpm(adSourceInfo)
    }

    override fun destroy() {
        thirdPartyAd?.setAdListener(null)
        thirdPartyAd?.setAdRevenueListener(null)
        thirdPartyAd = null
        fullScreenContentCallbackAtomic.set(null)
        onUserEarnedRewardListenerAtomic.set(null)
        latestAdInfoAtomic.set(null)
    }

    private fun rememberAdInfo(info: TUAdInfo?) {
        if (info != null) {
            latestAdInfoAtomic.set(info)
            updateEstimatedEcpmAdSourceInfo(info.toAdSourceInfo())
        }
    }

    companion object {
        private const val TAG = "TopOnRewardedAd"
    }
}
