package com.poseidon.ad.mediation.max.show

import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.ads.MaxRewardedAd
import com.poseidon.ad.api.OnUserEarnedRewardListener
import com.poseidon.ad.api.RewardItem
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.main.adinfo.ERROR_CODE_ACTIVITY_DESTROYED
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_THIRD_PARTY_AD
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNKNOWN
import com.poseidon.ad.main.adinfo.RewardedAdBase
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.load.RewardedParameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.processor.PlatformRewardedAdShowParameter
import com.poseidon.ad.main.show.InnerAdShowConfig
import com.poseidon.ad.main.show.InnerFullScreenContentCallback
import com.poseidon.ad.mediation.max.contentToString
import com.poseidon.ad.mediation.max.resolveAdPaidInfo
import com.poseidon.ad.mediation.max.resolveAdSourceInfo
import java.util.concurrent.atomic.AtomicReference

/**
 * Max 激励广告包装类，负责展示、奖励派发及生命周期管理。
 */
internal class MaxRewardedAd(
    @AdSourceIdDef private val adSourceId: Int,
    private var thirdPartyAd: MaxRewardedAd?,
    private var maxAd: MaxAd?,
    private val parameter: RewardedParameter
) : RewardedAdBase() {

    /** 全屏内容回调引用 */
    private val fullScreenContentCallbackAtomic = AtomicReference<InnerFullScreenContentCallback>()
    /** 激励奖励监听引用 */
    private val onUserEarnedRewardListenerAtomic = AtomicReference<OnUserEarnedRewardListener>()

    init {
        updateEstimatedEcpmAdSourceInfo(maxAd?.resolveAdSourceInfo())
    }

    override fun getAdSourceId(): Int = adSourceId

    override fun show(
        showParameter: PlatformRewardedAdShowParameter,
        showConfig: InnerAdShowConfig,
        onUserEarnedRewardListener: OnUserEarnedRewardListener?
    ): ShowResultCallback {
        val currentAd = thirdPartyAd ?: return ShowResultCallback.Failure(
            code = ERROR_CODE_NULL_THIRD_PARTY_AD,
            message = "MaxRewardedAd is null"
        )
        currentAd.setRevenueListener { maxAd ->
            val adSourceInfo = maxAd.resolveAdSourceInfo()
            updateEstimatedEcpmAdSourceInfo(adSourceInfo)
            val adPaidInfo = resolveAdPaidInfo(
                maxAd = maxAd,
                loadParameter = parameter,
                adSourceInfo = adSourceInfo
            )
            logInfo(TAG) {
                "setRevenueListener: maxAd=${maxAd.contentToString()}, adPaidInfo=$adPaidInfo"
            }
            dispatchOnPaidEvent(adPaidInfo)
        }
        onUserEarnedRewardListenerAtomic.set(onUserEarnedRewardListener)
        return runCatching {
            currentAd.showAd(showParameter.activity)
        }.fold(
            onSuccess = { ShowResultCallback.Success },
            onFailure = { t ->
                if (t.isMaxRewardedInvalidActivityShowException()) {
                    logInfo(TAG) {
                        "Caught MAX invalid Activity show exception; keeping ad for later show: message=${t.message}"
                    }
                    ShowResultCallback.Failure(
                        code = ERROR_CODE_ACTIVITY_DESTROYED,
                        message = t.message ?: MAX_INVALID_ACTIVITY_SHOW_MESSAGE
                    )
                } else {
                    ShowResultCallback.Failure(
                        code = ERROR_CODE_UNKNOWN,
                        message = t.message ?: "Failed to show MaxRewardedAd"
                    )
                }
            }
        )
    }

    override fun setFullScreenContentCallback(fullScreenContentCallback: InnerFullScreenContentCallback) {
        fullScreenContentCallbackAtomic.set(fullScreenContentCallback)
    }

    fun dispatchUserRewarded(maxAd: MaxAd, maxReward: MaxReward) {
        logInfo(TAG) {
            "dispatchUserRewarded: maxAd=${maxAd.contentToString()}, maxReward=${maxReward.contentToString()}"
        }
        val item = RewardItem(amount = maxReward.amount, type = maxReward.label)
        onUserEarnedRewardListenerAtomic.get()?.onUserEarnedReward(item)
    }

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

    fun dispatchAdClickedEvent(maxAd: MaxAd) {
        val adSourceInfo = maxAd.resolveAdSourceInfo()
        updateEstimatedEcpmAdSourceInfo(adSourceInfo)
        logInfo(TAG) {
            "Ad was clicked. maxAd=${maxAd.contentToString()}, adSourceInfo=${adSourceInfo}"
        }
        super.dispatchAdClickedEvent(
            adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown,
            adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
            adSourceName = adSourceInfo?.adSourceName,
        )
        fullScreenContentCallbackAtomic.get()?.onAdClicked()
    }

    fun dispatchAdClosedEvent(maxAd: MaxAd?) {
        if (!isAdClosedAtomic.compareAndSet(expect = false, update = true)) {
            return
        }
        val adSourceInfo = maxAd.resolveAdSourceInfo()
        updateEstimatedEcpmAdSourceInfo(adSourceInfo)
        logInfo(TAG) {
            "Ad dismissed fullscreen content. maxAd=${maxAd?.contentToString()}, adSourceInfo=${adSourceInfo}"
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
        onUserEarnedRewardListenerAtomic.set(null)
    }

    companion object {
        private const val TAG = "MaxRewardedAd"
    }
}

private fun Throwable.isMaxRewardedInvalidActivityShowException(): Boolean {
    return this is IllegalArgumentException &&
        message == MAX_INVALID_ACTIVITY_SHOW_MESSAGE
}

private const val MAX_INVALID_ACTIVITY_SHOW_MESSAGE =
    "Attempting to show ad without a valid activity."
