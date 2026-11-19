package com.poseidon.ad.mediation.max.load

import android.content.Context
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.sdk.AppLovinSdk
import com.poseidon.ad.api.OnUserEarnedRewardListener
import com.poseidon.ad.api.RewardItem
import com.poseidon.ad.common.toolkit.ShowResultCallback
import com.poseidon.ad.common.toolkit.logInfo
import com.poseidon.ad.core.processor.MediationSetup
import com.poseidon.ad.main.adinfo.ERROR_CODE_ACTIVITY_DESTROYED
import com.poseidon.ad.main.adinfo.ERROR_CODE_NULL_THIRD_PARTY_AD
import com.poseidon.ad.main.adinfo.ERROR_CODE_UNKNOWN
import com.poseidon.ad.main.adinfo.RewardedAdBase
import com.poseidon.ad.main.load.InnerAdLoadCallback
import com.poseidon.ad.main.load.Parameter
import com.poseidon.ad.main.load.RewardedAdLoader
import com.poseidon.ad.main.load.RewardedParameter
import com.poseidon.ad.main.price.AD_SOURCE_ID_Unknown
import com.poseidon.ad.main.price.AdSourceIdDef
import com.poseidon.ad.main.processor.PlatformRewardedAdLoadParameter
import com.poseidon.ad.main.processor.PlatformRewardedAdShowParameter
import com.poseidon.ad.main.show.InnerAdShowConfig
import com.poseidon.ad.main.show.InnerFullScreenContentCallback
import com.poseidon.ad.mediation.max.contentToString
import com.poseidon.ad.mediation.max.init.MaxSetup
import com.poseidon.ad.mediation.max.resolveAdPaidInfo
import com.poseidon.ad.mediation.max.resolveAdSourceInfo
import java.util.concurrent.atomic.AtomicReference
import com.applovin.mediation.ads.MaxRewardedAd as ThirdPartyMaxRewardedAd

internal class MaxRewardedLoad(parameter: RewardedParameter) : RewardedAdLoader(parameter = parameter) {

    override fun mediationAdLoad(loadParameter: PlatformRewardedAdLoadParameter, loadCallback: InnerAdLoadCallback<RewardedAdBase>) {
        val context = loadParameter.context
        val thirdPartyAd = ThirdPartyMaxRewardedAd::class.java.getDeclaredConstructor(String::class.java, AppLovinSdk::class.java, Context::class.java)
            .apply { isAccessible = true }
            .newInstance(parameter.adUnitId, AppLovinSdk.getInstance(context), context)
        thirdPartyAd.setListener(object : MaxRewardedAdListener {

            private val maxRewardedAdAtomic = AtomicReference<MaxRewardedLoad.MaxRewardedAd>()

            override fun onAdLoaded(maxAd: MaxAd) {
                markAdRequestReturned()
                val adSourceInfo = maxAd.resolveAdSourceInfo()
                logInfo(TAG) {
                    "onAdLoaded: ad=${maxAd.contentToString()}, adSourceInfo=${adSourceInfo}"
                }
                val adSourceId = adSourceInfo?.adSourceId ?: AD_SOURCE_ID_Unknown
                val maxRewardedAd = MaxRewardedAd(
                    adSourceId = adSourceId,
                    thirdPartyAd = thirdPartyAd,
                    maxAd = maxAd,
                    parameter = parameter as RewardedParameter
                )
                dispatchAdLoadedEvent(
                    adSourceId = adSourceId,
                    adSourcePlacementId = adSourceInfo?.adSourcePlacementId.orEmpty(),
                    revenue = resolveAdPaidInfo(maxAd = maxAd, loadParameter = parameter).revenue,
                    poseidonConfigVersion = parameter.poseidonConfigVersion,
                    adSourceName = adSourceInfo?.adSourceName,
                    estimatedRevenue = maxRewardedAd.estimatedEcpm(adSourceInfo) ?: -1.0
                )
                maxRewardedAdAtomic.set(maxRewardedAd)
                loadCallback.onAdLoadSuccess(maxRewardedAd)
            }

            override fun onAdDisplayed(maxAd: MaxAd) {
                maxRewardedAdAtomic.get()?.dispatchAdImpressionEvent(maxAd = maxAd)
            }

            override fun onAdHidden(maxAd: MaxAd) {
                maxRewardedAdAtomic.get()?.dispatchAdClosedEvent(maxAd = maxAd)
            }

            override fun onAdClicked(maxAd: MaxAd) {
                maxRewardedAdAtomic.get()?.dispatchAdClickedEvent(maxAd = maxAd)
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                markAdRequestReturned()
                thirdPartyAd.setListener(null)
                thirdPartyAd.setRevenueListener(null)
                thirdPartyAd.destroy()
                logInfo(TAG) {
                    "onAdLoadFailed: adUnitId=$adUnitId, error=${error.contentToString()}"
                }
                val code = error.code
                val message = error.message
                dispatchAdFailedLoadedEvent(code = code, message = message, poseidonConfigVersion = parameter.poseidonConfigVersion)
                loadCallback.onAdLoadFailed(code = code, message = message)
            }

            override fun onAdDisplayFailed(maxAd: MaxAd, error: MaxError) {
                maxRewardedAdAtomic.get()?.dispatchAdFailedToShowEvent(maxAd = maxAd, error = error)
            }

            override fun onUserRewarded(maxAd: MaxAd, maxReward: MaxReward) {
                maxRewardedAdAtomic.get()?.dispatchUserRewarded(maxAd = maxAd, maxReward = maxReward)
            }

        })
        markAdRequestStarted()
        runCatching {
            thirdPartyAd.loadAd()
        }.onFailure { t ->
            logInfo(TAG) {
                "loadAd failed at MAX boundary: message=${t.message}"
            }
            loadCallback.onAdLoadFailed(
                code = ERROR_CODE_UNKNOWN,
                message = t.message ?: "Failed to load MaxRewardedAd"
            )
        }
    }

    override fun mediationAdCheckBeforeLoad(): MediationSetup = MaxSetup

    companion object {
        private const val TAG = "MaxRewardedAdLoader"
    }

    private class MaxRewardedAd(
        @AdSourceIdDef private val adSourceId: Int,
        private var thirdPartyAd: ThirdPartyMaxRewardedAd?,
        private var maxAd: MaxAd?,
        private val parameter: RewardedParameter
    ) : RewardedAdBase() {

        override fun getAdSourceId(): Int = adSourceId

        private val fullScreenContentCallbackAtomic: AtomicReference<InnerFullScreenContentCallback> = AtomicReference()
        private val onUserEarnedRewardListenerAtomic: AtomicReference<OnUserEarnedRewardListener> = AtomicReference()

        init {
            updateEstimatedEcpmAdSourceInfo(maxAd.resolveAdSourceInfo())
        }

        override fun show(showParameter: PlatformRewardedAdShowParameter, showConfig: InnerAdShowConfig, onUserEarnedRewardListener: OnUserEarnedRewardListener?): ShowResultCallback {
            val thirdPartyAd = thirdPartyAd
                ?: return ShowResultCallback.Failure(
                    code = ERROR_CODE_NULL_THIRD_PARTY_AD,
                    message = "MaxRewardedAd is null"
                )

            thirdPartyAd.setRevenueListener { maxAd ->
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
                thirdPartyAd.showAd(showParameter.activity)
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
            runCatching {
                destroy()
            }
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

        override fun isReady(): Boolean {
            return thirdPartyAd != null
        }

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
}

private fun Throwable.isMaxRewardedInvalidActivityShowException(): Boolean {
    return this is IllegalArgumentException &&
        message == MAX_INVALID_ACTIVITY_SHOW_MESSAGE
}

private const val MAX_INVALID_ACTIVITY_SHOW_MESSAGE =
    "Attempting to show ad without a valid activity."
